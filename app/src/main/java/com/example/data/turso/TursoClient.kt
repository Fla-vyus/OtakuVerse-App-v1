package com.example.data.turso

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.AuthorItem
import com.example.data.model.ExtLinkItem
import com.example.data.model.TagItem
import com.example.data.model.WorkCard
import com.example.data.model.WorkDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

open class TursoException(message: String, cause: Throwable? = null) : IOException(message, cause)
class TursoMissingTokenException(message: String = "Chave de acesso não configurada") : TursoException(message)
class TursoAuthException(message: String = "Chave inválida ou expirada") : TursoException(message)
class TursoSqlException(val sqlError: String) : TursoException("Erro na consulta do catálogo: $sqlError")
class TursoNetworkException(message: String, cause: Throwable? = null) : TursoException(message, cause)

sealed class TursoArg {
    data class Text(val value: String) : TursoArg()
    data class Integer(val value: Long) : TursoArg()
    data class Real(val value: Double) : TursoArg()
    object Null : TursoArg()

    fun toJson(): JSONObject {
        val obj = JSONObject()
        when (this) {
            is Text -> {
                obj.put("type", "text")
                obj.put("value", value)
            }
            is Integer -> {
                obj.put("type", "integer")
                obj.put("value", value.toString())
            }
            is Real -> {
                obj.put("type", "float")
                obj.put("value", value)
            }
            is Null -> {
                obj.put("type", "null")
            }
        }
        return obj
    }
}

class TursoRow(private val colMap: Map<String, Int>, private val cells: JSONArray) {
    fun getString(col: String): String? {
        val index = colMap[col] ?: return null
        if (index < 0 || index >= cells.length()) return null
        val cell = cells.optJSONObject(index) ?: return null
        if (cell.optString("type") == "null") return null
        val raw = cell.opt("value") ?: return null
        return if (raw == JSONObject.NULL) null else raw.toString()
    }

    fun getInt(col: String): Int? {
        return getLong(col)?.toInt()
    }

    fun getLong(col: String): Long? {
        val index = colMap[col] ?: return null
        if (index < 0 || index >= cells.length()) return null
        val cell = cells.optJSONObject(index) ?: return null
        if (cell.optString("type") == "null") return null
        val value = cell.opt("value") ?: return null
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    fun getBoolean(col: String): Boolean {
        val num = getLong(col) ?: 0L
        return num != 0L
    }

    fun getStringList(col: String): List<String> {
        val raw = getString(col)?.trim() ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        return try {
            if (raw.startsWith("[")) {
                val arr = JSONArray(raw)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i)
                    if (item.isNotBlank()) list.add(item)
                }
                list
            } else {
                raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getTagList(col: String): List<TagItem> {
        val raw = getString(col)?.trim() ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        return try {
            val list = mutableListOf<TagItem>()
            if (raw.startsWith("[")) {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i)
                    if (obj != null) {
                        val name = obj.optString("name", "")
                        val rank = obj.optInt("rank", 0)
                        if (name.isNotBlank()) {
                            list.add(TagItem(name = name, rank = rank))
                        }
                    } else {
                        val str = arr.optString(i)
                        if (str.isNotBlank()) list.add(TagItem(name = str, rank = 0))
                    }
                }
            }
            list.sortedByDescending { it.rank }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getAuthorList(col: String): List<AuthorItem> {
        val raw = getString(col)?.trim() ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        return try {
            val list = mutableListOf<AuthorItem>()
            if (raw.startsWith("[")) {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i)
                    if (obj != null) {
                        val name = obj.optString("name", "").trim()
                        val role = obj.optString("role", "").trim().takeIf { it.isNotBlank() }
                        if (name.isNotBlank()) {
                            list.add(AuthorItem(name = name, role = role))
                        }
                    } else {
                        val str = arr.optString(i).trim()
                        if (str.isNotBlank()) {
                            list.add(AuthorItem(name = str, role = null))
                        }
                    }
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getExtLinkList(col: String): List<ExtLinkItem> {
        val raw = getString(col)?.trim() ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        return try {
            val list = mutableListOf<ExtLinkItem>()
            if (raw.startsWith("[")) {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i)
                    if (obj != null) {
                        val site = obj.optString("site", "").trim()
                        val url = obj.optString("url", "").trim()
                        val type = obj.optString("type", "").trim().takeIf { it.isNotBlank() }
                        val color = obj.optString("color", "").trim().takeIf { it.isNotBlank() }
                        val icon = obj.optString("icon", "").trim().takeIf { it.isNotBlank() }
                        if (url.isNotBlank()) {
                            list.add(
                                ExtLinkItem(
                                    site = if (site.isNotBlank()) site else "Site Oficial",
                                    url = url,
                                    type = type,
                                    color = color,
                                    icon = icon
                                )
                            )
                        }
                    }
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class TursoClient(
    private var customTokenProvider: (() -> String?)? = null,
    private var suspendTokenProvider: (suspend () -> String?)? = null
) {
    companion object {
        const val PIPELINE_URL = "https://otakuverse-fla-vyus.aws-sa-east-1.turso.io/v2/pipeline"
        private const val TAG = "TursoClient"
        const val CARD_COLUMNS = """id, "type", "format", "title", "titleEn", "cover", "coverColor", "banner", "avgScore", "popularity", "status", "genres", "startDate", "nextEpisodeAt", "nextEpisodeNum", "favourites", "isAdult""""
        const val ADULT_FILTER = "isAdult = 0"

        fun sanitizeText(raw: Any?): String? {
            if (raw == null || raw == JSONObject.NULL) return null
            val s = raw.toString().trim()
            if (s.isEmpty() || s.equals("null", ignoreCase = true)) return null
            return s
        }
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun getBuildConfigToken(): String {
        val tokenVal = BuildConfig.TURSO_TOKEN
        if (tokenVal.isNotBlank() && tokenVal != "TURSO_READ_ONLY_TOKEN_PLACEHOLDER") {
            return tokenVal.trim()
        }
        val localVal = BuildConfig.TURSO_TOKEN_LOCAL
        if (localVal.isNotBlank() && localVal != "TURSO_READ_ONLY_TOKEN_PLACEHOLDER") {
            return localVal.trim()
        }
        return ""
    }

    suspend fun getActiveToken(): String {
        // Priority 1: Token in BuildConfig (from .env / secrets) (A6)
        val buildConfigToken = getBuildConfigToken()
        if (buildConfigToken.isNotBlank()) return buildConfigToken

        // Priority 2: Suspend token provider (awaits preferencesRepository.customToken.first())
        val suspendCustom = suspendTokenProvider?.invoke()
        if (!suspendCustom.isNullOrBlank()) return suspendCustom.trim()

        // Priority 3: Fallback sync token provider
        val custom = customTokenProvider?.invoke()
        if (!custom.isNullOrBlank()) return custom.trim()

        return ""
    }

    suspend fun query(sql: String, args: List<TursoArg> = emptyList()): List<TursoRow> {
        val results = batchQuery(listOf(Pair(sql, args)))
        return results.firstOrNull() ?: emptyList()
    }

    suspend fun batchQuery(statements: List<Pair<String, List<TursoArg>>>): List<List<TursoRow>> =
        withContext(Dispatchers.IO) {
            val token = getActiveToken()
            if (token.isBlank()) {
                throw TursoMissingTokenException("Chave de acesso não configurada")
            }

            val requestPayload = JSONObject().apply {
                val requests = JSONArray()
                statements.forEach { (sql, args) ->
                    val executeReq = JSONObject().apply {
                        put("type", "execute")
                        val stmt = JSONObject().apply {
                            put("sql", sql)
                            val argsArr = JSONArray()
                            args.forEach { argsArr.put(it.toJson()) }
                            put("args", argsArr)
                        }
                        put("stmt", stmt)
                    }
                    requests.put(executeReq)
                }
                requests.put(JSONObject().apply { put("type", "close") })
                put("requests", requests)
            }

            var attempt = 0
            var lastException: Exception? = null

            while (attempt < 2) {
                try {
                    return@withContext executeHttpBatch(requestPayload, token)
                } catch (e: TursoAuthException) {
                    // 401/403: Do not retry!
                    throw e
                } catch (e: TursoSqlException) {
                    // SQL syntax error: Do not retry!
                    throw e
                } catch (e: Exception) {
                    lastException = e
                    attempt++
                    if (attempt < 2) {
                        delay(500L * attempt)
                    }
                }
            }
            throw lastException ?: TursoNetworkException("Falha na conexão com o catálogo.")
        }

    private fun executeHttpBatch(payload: JSONObject, token: String): List<List<TursoRow>> {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = payload.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(PIPELINE_URL)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: SocketTimeoutException) {
            throw TursoNetworkException("Tempo limite esgotado ao conectar ao banco.", e)
        } catch (e: IOException) {
            throw TursoNetworkException("Erro de conexão de rede.", e)
        }

        response.use { resp ->
            if (resp.code == 401 || resp.code == 403) {
                Log.w(TAG, "Auth failure ${resp.code}")
                throw TursoAuthException("Chave inválida ou expirada")
            }

            if (!resp.isSuccessful) {
                val errBody = resp.body?.string().orEmpty()
                Log.w(TAG, "Turso HTTP ${resp.code}: $errBody")
                if (resp.code in 500..599 || resp.code == 429) {
                    throw TursoNetworkException("Serviço temporariamente indisponível (${resp.code}).")
                } else {
                    throw TursoException("Erro ao consultar catálogo (${resp.code}).")
                }
            }

            val bodyString = resp.body?.string().orEmpty()
            val root = JSONObject(bodyString)
            val resultsArr = root.optJSONArray("results")
                ?: throw TursoException("Resposta inválida do servidor.")

            val batchList = mutableListOf<List<TursoRow>>()

            for (i in 0 until resultsArr.length()) {
                val resItem = resultsArr.optJSONObject(i) ?: continue
                if (resItem.optString("type") == "close") continue

                if (resItem.optString("type") == "error") {
                    val errObj = resItem.optJSONObject("error")
                    val msg = errObj?.optString("message") ?: "Erro na consulta SQL"
                    Log.w(TAG, "Turso SQL Error at statement $i: $msg")
                    throw TursoSqlException(msg)
                }

                val responseObj = resItem.optJSONObject("response")
                val resultObj = responseObj?.optJSONObject("result")

                if (resultObj == null) {
                    batchList.add(emptyList())
                    continue
                }

                val cols = resultObj.optJSONArray("cols") ?: JSONArray()
                val colMap = mutableMapOf<String, Int>()
                for (c in 0 until cols.length()) {
                    val colObj = cols.optJSONObject(c)
                    val name = colObj?.optString("name")
                    if (!name.isNullOrEmpty()) {
                        colMap[name] = c
                    }
                }

                val rowsArr = resultObj.optJSONArray("rows") ?: JSONArray()
                val rowsList = mutableListOf<TursoRow>()
                for (r in 0 until rowsArr.length()) {
                    val rowCells = rowsArr.optJSONArray(r)
                    if (rowCells != null) {
                        rowsList.add(TursoRow(colMap, rowCells))
                    }
                }
                batchList.add(rowsList)
            }

            return batchList
        }
    }

    fun mapRowToCard(row: TursoRow): WorkCard {
        return WorkCard(
            id = row.getString("id") ?: "",
            type = row.getString("type") ?: "anime",
            format = row.getString("format") ?: "",
            title = row.getString("title") ?: "",
            titleEn = row.getString("titleEn"),
            cover = row.getString("cover") ?: "",
            coverColor = row.getString("coverColor"),
            banner = row.getString("banner"),
            avgScore = row.getInt("avgScore"),
            popularity = row.getInt("popularity"),
            status = row.getString("status") ?: "",
            genres = row.getStringList("genres"),
            startDate = row.getString("startDate"),
            nextEpisodeAt = row.getString("nextEpisodeAt"),
            nextEpisodeNum = row.getInt("nextEpisodeNum"),
            favourites = row.getInt("favourites"),
            isAdult = row.getBoolean("isAdult")
        )
    }

    fun mapRowToDetail(row: TursoRow): WorkDetail {
        val synPT = row.getString("synopsisPT")?.takeIf { it.isNotBlank() }
        val synShort = row.getString("synopsisShort")
        val synFull = row.getString("synopsis")

        return WorkDetail(
            id = row.getString("id") ?: "",
            malId = row.getInt("malId"),
            type = row.getString("type") ?: "anime",
            format = row.getString("format") ?: "",
            title = row.getString("title") ?: "",
            titleEn = row.getString("titleEn"),
            titleNative = row.getString("titleNative"),
            synonyms = row.getStringList("synonyms"),
            synopsis = synFull ?: synShort,
            synopsisPT = synPT,
            cover = row.getString("cover") ?: "",
            coverAlt = row.getString("coverAlt"),
            coverColor = row.getString("coverColor"),
            banner = row.getString("banner"),
            status = row.getString("status") ?: "",
            chapters = row.getInt("chapters"),
            volumes = row.getInt("volumes"),
            episodes = row.getInt("episodes"),
            duration = row.getInt("duration"),
            startDate = row.getString("startDate"),
            endDate = row.getString("endDate"),
            country = row.getString("country"),
            isAdult = row.getBoolean("isAdult"),
            classification = row.getString("classification"),
            genres = row.getStringList("genres"),
            tags = row.getTagList("tags"),
            avgScore = row.getInt("avgScore"),
            popularity = row.getInt("popularity"),
            favourites = row.getInt("favourites"),
            authors = row.getAuthorList("authors"),
            extLinks = row.getExtLinkList("extLinks"),
            magazine = row.getString("magazine"),
            network = row.getString("network"),
            source = row.getString("source"),
            siteUrl = row.getString("siteUrl"),
            lastUpdate = row.getLong("lastUpdate"),
            nextEpisodeAt = row.getString("nextEpisodeAt"),
            nextEpisodeNum = row.getInt("nextEpisodeNum"),
            coverAlts = row.getStringList("coverAlts"),
            trending = row.getInt("trending"),
            follows = row.getInt("follows")
        )
    }

    fun mapJsonToCard(obj: JSONObject): WorkCard? {
        val id = sanitizeText(obj.opt("id"))
            ?: sanitizeText(obj.opt("workId"))
            ?: return null

        val title = sanitizeText(obj.opt("title"))
            ?: sanitizeText(obj.opt("titleEn"))
            ?: sanitizeText(obj.opt("titleNative"))
            ?: return null

        val cover = sanitizeText(obj.opt("cover"))
            ?: sanitizeText(obj.opt("coverUrl"))
            ?: sanitizeText(obj.opt("image"))
            ?: return null

        val type = sanitizeText(obj.opt("type")) ?: "anime"
        val format = sanitizeText(obj.opt("format")) ?: ""
        val titleEn = sanitizeText(obj.opt("titleEn"))
        val coverColor = sanitizeText(obj.opt("coverColor"))
        val banner = sanitizeText(obj.opt("banner"))

        val avgScore = if (obj.has("avgScore") && !obj.isNull("avgScore")) {
            val num = obj.optDouble("avgScore")
            if (num in 0.0..10.0 && num > 0.0) (num * 10).toInt() else num.toInt()
        } else null

        val popularity = if (obj.has("popularity") && !obj.isNull("popularity")) obj.optInt("popularity") else null
        val status = sanitizeText(obj.opt("status")) ?: ""

        val genresList = mutableListOf<String>()
        val genresRaw = obj.opt("genres")
        if (genresRaw is JSONArray) {
            for (i in 0 until genresRaw.length()) {
                val item = genresRaw.opt(i)
                if (item is JSONObject) {
                    val name = sanitizeText(item.opt("name"))
                    if (name != null) genresList.add(name)
                } else if (item != null) {
                    val s = sanitizeText(item)
                    if (s != null) genresList.add(s)
                }
            }
        } else if (genresRaw is String && genresRaw.isNotBlank()) {
            val trimmed = genresRaw.trim()
            if (trimmed.startsWith("[")) {
                try {
                    val arr = JSONArray(trimmed)
                    for (i in 0 until arr.length()) {
                        val s = sanitizeText(arr.opt(i))
                        if (s != null) genresList.add(s)
                    }
                } catch (_: Exception) {}
            } else {
                genresList.addAll(
                    trimmed.split(",")
                        .mapNotNull { sanitizeText(it) }
                )
            }
        }

        val startDate = sanitizeText(obj.opt("startDate"))
            ?: sanitizeText(obj.opt("year"))
        val nextEpisodeAt = sanitizeText(obj.opt("nextEpisodeAt"))
        val nextEpisodeNum = if (obj.has("nextEpisodeNum") && !obj.isNull("nextEpisodeNum")) obj.optInt("nextEpisodeNum") else null
        val favourites = if (obj.has("favourites") && !obj.isNull("favourites")) {
            obj.optInt("favourites")
        } else if (obj.has("favorites") && !obj.isNull("favorites")) {
            obj.optInt("favorites")
        } else null

        val isAdult = obj.optBoolean("isAdult", false)
            || (obj.optInt("isAdult", 0) != 0)
            || obj.optString("isAdult", "false").equals("true", ignoreCase = true)

        return WorkCard(
            id = id,
            type = type,
            format = format,
            title = title,
            titleEn = titleEn,
            cover = cover,
            coverColor = coverColor,
            banner = banner,
            avgScore = avgScore,
            popularity = popularity,
            status = status,
            genres = genresList,
            startDate = startDate,
            nextEpisodeAt = nextEpisodeAt,
            nextEpisodeNum = nextEpisodeNum,
            favourites = favourites,
            isAdult = isAdult
        )
    }

    fun parseWorkCardsFromItemsJson(itemsJson: String?): List<WorkCard> {
        if (itemsJson.isNullOrBlank()) return emptyList()
        val list = mutableListOf<WorkCard>()
        try {
            val trimmed = itemsJson.trim()
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    mapJsonToCard(obj)?.let { list.add(it) }
                }
            } else if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                val arr = root.optJSONArray("items") ?: root.optJSONArray("data")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        mapJsonToCard(obj)?.let { list.add(it) }
                    }
                }
            }
        } catch (_: Exception) {}
        return list.distinctBy { it.id }
    }
}
