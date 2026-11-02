package com.example.data.repository

import android.util.Log
import com.example.data.local.CachedWorkEntity
import com.example.data.local.WorkDao
import com.example.data.model.AdultMode
import com.example.data.model.ExtraHomeSection
import com.example.data.model.ScheduledEpisode
import com.example.data.model.WorkCard
import com.example.data.model.WorkDetail
import com.example.data.turso.TursoAuthException
import com.example.data.turso.TursoMissingTokenException
import com.example.data.turso.TursoClient
import com.example.data.turso.TursoQueryBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class HomeDataResult(
    val heroItems: List<WorkCard> = emptyList(),
    // 10 sections in exact order (Etapa 1.3):
    val recentReleases: List<WorkCard> = emptyList(),
    val newHits: List<WorkCard> = emptyList(),
    val monthSchedule: List<WorkCard> = emptyList(),
    val topPopularity: List<WorkCard> = emptyList(),
    val trendingManhwas: List<WorkCard> = emptyList(),
    val popularMangas: List<WorkCard> = emptyList(),
    val topRatedMangaManhwa: List<WorkCard> = emptyList(),
    val bingeItems: List<WorkCard> = emptyList(),
    val isekaiReincarnation: List<WorkCard> = emptyList(),
    val featuredManhuas: List<WorkCard> = emptyList(),
    // Status & Diagnostics
    val isOffline: Boolean = false,
    val isTokenError: Boolean = false,
    val errorMessage: String? = null,
    val batch1DurationMs: Long = 0L,
    val batch2DurationMs: Long = 0L,
    val bombandoCriterion: String = ""
)

class WorkRepository(
    private val tursoClient: TursoClient,
    private val workDao: WorkDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    companion object {
        private const val TAG = "WorkRepository"
        private const val COUNT_CACHE_TTL_MS = 10 * 60 * 1000L // 10 minutes cache for Explore COUNT
        const val OLD_EXTRA_SECTIONS_CACHE_FILE = "home_extra_sections_cache.json"
        const val EXTRA_SECTIONS_CACHE_FILE = "home_extra_sections_cache_v2.json"
    }

    // Auxiliary table capabilities (sqlite_master check)
    @Volatile
    private var hasAuxGenreTable: Boolean = false
    @Volatile
    private var hasAuxTagTable: Boolean = false
    @Volatile
    private var capabilitiesChecked: Boolean = false

    // "Bombando" chapter probe cache
    @Volatile
    private var probedBombando: Boolean = false
    @Volatile
    private var useChaptersCountForBombando: Boolean = false
    @Volatile
    private var bombandoDiagnosticString: String = "Não avaliado"

    // 10-minute in-memory cache for explore counts
    private val exploreCountCache = mutableMapOf<String, Pair<Int, Long>>()

    private suspend fun ensureCapabilitiesChecked() {
        if (capabilitiesChecked) return
        try {
            val sql = TursoQueryBuilder.buildAuxiliaryTablesCheckQuery()
            val rows = tursoClient.query(sql)
            val names = rows.mapNotNull { it.getString("name") }
            hasAuxGenreTable = names.contains("WorkGenre")
            hasAuxTagTable = names.contains("WorkTag")
            capabilitiesChecked = true
            Log.d(TAG, "Auxiliary tables: WorkGenre=$hasAuxGenreTable, WorkTag=$hasAuxTagTable")
        } catch (e: Exception) {
            Log.w(TAG, "Failed checking auxiliary tables, using json_each fallback: ${e.message}")
            hasAuxGenreTable = false
            hasAuxTagTable = false
        }
    }

    private suspend fun ensureBombandoProbed() {
        if (probedBombando) return
        try {
            val sql = TursoQueryBuilder.buildProbeChaptersQuery()
            val rows = tursoClient.query(sql)
            if (rows.isNotEmpty()) {
                val total = rows[0].getLong("total") ?: 0L
                val chaptersCount = rows[0].getLong("chaptersCount") ?: 0L
                // If chapters is almost always null in releasing works:
                useChaptersCountForBombando = (total > 0 && chaptersCount > (total * 0.2))
                bombandoDiagnosticString = if (useChaptersCountForBombando) {
                    "chapters <= 80 (Preenchidos: $chaptersCount de $total)"
                } else {
                    "startDate >= 18 meses (Chapters nulo em ${total - chaptersCount} de $total)"
                }
                probedBombando = true
                Log.d(TAG, "Bombando criterion: $bombandoDiagnosticString")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed probing chapters for bombando: ${e.message}")
            useChaptersCountForBombando = false
            bombandoDiagnosticString = "startDate >= 18 meses (Fallback de erro)"
        }
    }

    /**
     * Stale-while-revalidate flow for Home (Etapa 1.4):
     * 1. Emits cached state immediately from Room.
     * 2. Checks capabilities & probes.
     * 3. Batch 1: Hero + Sections 1 to 4 in ONE pipeline request -> saved to Room & emitted.
     * 4. Batch 2: Sections 5 to 10 in a second pipeline request -> saved to Room & emitted.
     */
    fun getHomeDataStream(): Flow<HomeDataResult> = flow {
        val activeToken = tursoClient.getActiveToken()
        if (activeToken.isBlank()) {
            emit(
                HomeDataResult(
                    heroItems = emptyList(),
                    recentReleases = emptyList(),
                    newHits = emptyList(),
                    monthSchedule = emptyList(),
                    topPopularity = emptyList(),
                    trendingManhwas = emptyList(),
                    popularMangas = emptyList(),
                    topRatedMangaManhwa = emptyList(),
                    bingeItems = emptyList(),
                    isekaiReincarnation = emptyList(),
                    featuredManhuas = emptyList(),
                    isOffline = false,
                    isTokenError = true,
                    errorMessage = "Chave de acesso não configurada",
                    bombandoCriterion = bombandoDiagnosticString
                )
            )
            return@flow
        }

        // Step 1: Read local cache first (Cache-first)
        val cached = getHomeFromCache()
        if (cached != null) {
            emit(cached)
        }

        ensureCapabilitiesChecked()
        ensureBombandoProbed()

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val now = Calendar.getInstance()
        val todayIso = sdf.format(now.time)

        val cal180 = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -180) }
        val todayMinus180DaysIso = sdf.format(cal180.time)

        val cal90 = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -90) }
        val todayMinus90DaysIso = sdf.format(cal90.time)

        val cal18M = Calendar.getInstance().apply { add(Calendar.MONTH, -18) }
        val todayMinus18MonthsIso = sdf.format(cal18M.time)

        val calToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val windowStartMillis = calToday.timeInMillis
        val windowEndMillis = windowStartMillis + 30L * 24 * 60 * 60 * 1000L
        val windowStartIso = sdf.format(calToday.time)
        val calNext30 = (calToday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 30) }
        val windowEndIso = sdf.format(calNext30.time)

        var batch1Duration = 0L
        var batch2Duration = 0L

        var heroList: List<WorkCard> = cached?.heroItems ?: emptyList()
        var s1: List<WorkCard> = cached?.recentReleases ?: emptyList()
        var s2: List<WorkCard> = cached?.newHits ?: emptyList()
        var s3: List<WorkCard> = cached?.monthSchedule?.mapNotNull { card ->
            val ts = com.example.data.model.getAgendaTimestamp(card) ?: return@mapNotNull null
            if (ts in windowStartMillis..windowEndMillis) Pair(card, ts) else null
        }?.sortedBy { it.second }?.map { it.first } ?: emptyList()
        var s4: List<WorkCard> = cached?.topPopularity ?: emptyList()
        var s5: List<WorkCard> = cached?.trendingManhwas ?: emptyList()
        var s6: List<WorkCard> = cached?.popularMangas ?: emptyList()
        var s7: List<WorkCard> = cached?.topRatedMangaManhwa ?: emptyList()
        var s8: List<WorkCard> = cached?.bingeItems ?: emptyList()
        var s9: List<WorkCard> = cached?.isekaiReincarnation ?: emptyList()
        var s10: List<WorkCard> = cached?.featuredManhuas ?: emptyList()

        // -------------------------------------------------------------
        // Batch 1: Hero + Sections 1 to 4 in ONE pipeline request
        // -------------------------------------------------------------
        val b1Start = System.currentTimeMillis()
        try {
            val qHero = TursoQueryBuilder.buildHeroQuery(todayMinus180DaysIso, limit = 8, useAuxGenreTable = hasAuxGenreTable)
            val qS1 = TursoQueryBuilder.buildRecentReleasesQuery(todayMinus90DaysIso, todayIso, limit = 20, useAuxGenreTable = hasAuxGenreTable)
            val qS2 = TursoQueryBuilder.buildNewHitsQuery(useChaptersCountForBombando, todayMinus18MonthsIso, limit = 20, useAuxGenreTable = hasAuxGenreTable)
            val qS3 = TursoQueryBuilder.buildMonthScheduleQuery(windowStartIso, windowEndIso, limit = 30, useAuxGenreTable = hasAuxGenreTable)
            val qS4 = TursoQueryBuilder.buildTopPopularityQuery(limit = 20, useAuxGenreTable = hasAuxGenreTable)

            val b1Results = tursoClient.batchQuery(listOf(qHero, qS1, qS2, qS3, qS4))
            batch1Duration = System.currentTimeMillis() - b1Start

            if (b1Results.size >= 5) {
                var heroCards = b1Results[0].map { tursoClient.mapRowToCard(it) }
                // Fallback if hero has fewer than 8 items
                if (heroCards.size < 8) {
                    val (fbSql, fbArgs) = TursoQueryBuilder.buildHeroFallbackQuery(limit = 8, useAuxGenreTable = hasAuxGenreTable)
                    val fbRows = tursoClient.query(fbSql, fbArgs)
                    val additional = fbRows.map { tursoClient.mapRowToCard(it) }.filter { add -> heroCards.none { it.id == add.id } }
                    heroCards = (heroCards + additional).take(8)
                }

                heroList = heroCards
                s1 = b1Results[1].map { tursoClient.mapRowToCard(it) }
                s2 = b1Results[2].map { tursoClient.mapRowToCard(it) }
                val rawS3 = b1Results[3].map { tursoClient.mapRowToCard(it) }
                s3 = rawS3.mapNotNull { card ->
                    val ts = com.example.data.model.getAgendaTimestamp(card) ?: return@mapNotNull null
                    if (ts in windowStartMillis..windowEndMillis) {
                        Pair(card, ts)
                    } else null
                }.sortedBy { it.second }.map { it.first }
                s4 = b1Results[4].map { tursoClient.mapRowToCard(it) }

                saveSectionToCache("hero", heroList)
                saveSectionToCache("sec_recent", s1)
                saveSectionToCache("sec_bombando", s2)
                saveSectionToCache("sec_agenda", s3)
                saveSectionToCache("sec_popular_anime", s4)

                emit(
                    HomeDataResult(
                        heroItems = heroList,
                        recentReleases = s1,
                        newHits = s2,
                        monthSchedule = s3,
                        topPopularity = s4,
                        trendingManhwas = s5,
                        popularMangas = s6,
                        topRatedMangaManhwa = s7,
                        bingeItems = s8,
                        isekaiReincarnation = s9,
                        featuredManhuas = s10,
                        isOffline = false,
                        isTokenError = false,
                        errorMessage = null,
                        batch1DurationMs = batch1Duration,
                        batch2DurationMs = 0L,
                        bombandoCriterion = bombandoDiagnosticString
                    )
                )
            }
        } catch (e: TursoMissingTokenException) {
            Log.w(TAG, "Turso Missing Token: ${e.message}")
            userPreferencesRepository.recordDiagnostic(e.message, 0L)
            emit(
                HomeDataResult(
                    heroItems = emptyList(),
                    recentReleases = emptyList(), newHits = emptyList(), monthSchedule = emptyList(), topPopularity = emptyList(),
                    trendingManhwas = emptyList(), popularMangas = emptyList(), topRatedMangaManhwa = emptyList(),
                    bingeItems = emptyList(), isekaiReincarnation = emptyList(), featuredManhuas = emptyList(),
                    isOffline = false,
                    isTokenError = true,
                    errorMessage = "Chave de acesso não configurada",
                    bombandoCriterion = bombandoDiagnosticString
                )
            )
            return@flow
        } catch (e: TursoAuthException) {
            Log.w(TAG, "Turso Auth: ${e.message}")
            userPreferencesRepository.recordDiagnostic(e.message, 0L)
            emit(
                HomeDataResult(
                    heroItems = emptyList(),
                    recentReleases = emptyList(), newHits = emptyList(), monthSchedule = emptyList(), topPopularity = emptyList(),
                    trendingManhwas = emptyList(), popularMangas = emptyList(), topRatedMangaManhwa = emptyList(),
                    bingeItems = emptyList(), isekaiReincarnation = emptyList(), featuredManhuas = emptyList(),
                    isOffline = false,
                    isTokenError = true,
                    errorMessage = "Chave inválida ou expirada",
                    bombandoCriterion = bombandoDiagnosticString
                )
            )
            return@flow
        } catch (e: Exception) {
            Log.w(TAG, "Batch 1 network info: ${e.message}")
            userPreferencesRepository.recordDiagnostic(e.message, 0L)
            emit(
                HomeDataResult(
                    heroItems = heroList,
                    recentReleases = s1, newHits = s2, monthSchedule = s3, topPopularity = s4,
                    trendingManhwas = s5, popularMangas = s6, topRatedMangaManhwa = s7,
                    bingeItems = s8, isekaiReincarnation = s9, featuredManhuas = s10,
                    isOffline = heroList.isNotEmpty(),
                    isTokenError = false,
                    errorMessage = if (heroList.isNotEmpty()) "Exibindo dados offline do catálogo." else (e.message ?: "Falha ao conectar aos servidores."),
                    bombandoCriterion = bombandoDiagnosticString
                )
            )
            return@flow
        }

        // -------------------------------------------------------------
        // Batch 2: Sections 5 to 10 in a second pipeline request
        // -------------------------------------------------------------
        val b2Start = System.currentTimeMillis()
        try {
            val qS5 = TursoQueryBuilder.buildTrendingManhwaQuery(limit = 20, useAuxGenreTable = hasAuxGenreTable)
            val qS6 = TursoQueryBuilder.buildPopularMangaQuery(limit = 20, useAuxGenreTable = hasAuxGenreTable)
            val qS7 = TursoQueryBuilder.buildTopRatedMangaManhwaQuery(limit = 20, useAuxGenreTable = hasAuxGenreTable)
            val qS8 = TursoQueryBuilder.buildBingeQuery(limit = 20, useAuxGenreTable = hasAuxGenreTable)
            val qS9 = TursoQueryBuilder.buildIsekaiReincarnationQuery(limit = 20, useAuxGenreTable = hasAuxGenreTable, useAuxTagTable = hasAuxTagTable)
            val qS10 = TursoQueryBuilder.buildFeaturedManhuaQuery(limit = 20, useAuxGenreTable = hasAuxGenreTable)

            val b2Results = tursoClient.batchQuery(listOf(qS5, qS6, qS7, qS8, qS9, qS10))
            batch2Duration = System.currentTimeMillis() - b2Start

            if (b2Results.size >= 6) {
                s5 = b2Results[0].map { tursoClient.mapRowToCard(it) }
                s6 = b2Results[1].map { tursoClient.mapRowToCard(it) }
                s7 = b2Results[2].map { tursoClient.mapRowToCard(it) }
                s8 = b2Results[3].map { tursoClient.mapRowToCard(it) }
                s9 = b2Results[4].map { tursoClient.mapRowToCard(it) }
                s10 = b2Results[5].map { tursoClient.mapRowToCard(it) }

                saveSectionToCache("sec_trending_manhwa", s5)
                saveSectionToCache("sec_popular_manga", s6)
                saveSectionToCache("sec_top_rated", s7)
                saveSectionToCache("sec_binge", s8)
                saveSectionToCache("sec_isekai", s9)
                saveSectionToCache("sec_manhua", s10)

                userPreferencesRepository.recordDiagnostic(
                    null,
                    batch1Duration + batch2Duration,
                    sampleEpisode = "Batch1: ${batch1Duration}ms, Batch2: ${batch2Duration}ms"
                )

                emit(
                    HomeDataResult(
                        heroItems = heroList,
                        recentReleases = s1,
                        newHits = s2,
                        monthSchedule = s3,
                        topPopularity = s4,
                        trendingManhwas = s5,
                        popularMangas = s6,
                        topRatedMangaManhwa = s7,
                        bingeItems = s8,
                        isekaiReincarnation = s9,
                        featuredManhuas = s10,
                        isOffline = false,
                        isTokenError = false,
                        errorMessage = null,
                        batch1DurationMs = batch1Duration,
                        batch2DurationMs = batch2Duration,
                        bombandoCriterion = bombandoDiagnosticString
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Batch 2 non-fatal error: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun getHomeFromCache(): HomeDataResult? = withContext(Dispatchers.IO) {
        val hero = workDao.getAnyCachedSection("hero").map { it.toWorkCard() }
        val s1 = workDao.getAnyCachedSection("sec_recent").map { it.toWorkCard() }
        val s2 = workDao.getAnyCachedSection("sec_bombando").map { it.toWorkCard() }
        val rawS3 = workDao.getAnyCachedSection("sec_agenda").map { it.toWorkCard() }
        val calNow = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startWin = calNow.timeInMillis
        val endWin = startWin + 30L * 24 * 60 * 60 * 1000L
        val s3 = rawS3.mapNotNull { card ->
            val ts = com.example.data.model.getAgendaTimestamp(card) ?: return@mapNotNull null
            if (ts in startWin..endWin) Pair(card, ts) else null
        }.sortedBy { it.second }.map { it.first }
        val s4 = workDao.getAnyCachedSection("sec_popular_anime").map { it.toWorkCard() }
        val s5 = workDao.getAnyCachedSection("sec_trending_manhwa").map { it.toWorkCard() }
        val s6 = workDao.getAnyCachedSection("sec_popular_manga").map { it.toWorkCard() }
        val s7 = workDao.getAnyCachedSection("sec_top_rated").map { it.toWorkCard() }
        val s8 = workDao.getAnyCachedSection("sec_binge").map { it.toWorkCard() }
        val s9 = workDao.getAnyCachedSection("sec_isekai").map { it.toWorkCard() }
        val s10 = workDao.getAnyCachedSection("sec_manhua").map { it.toWorkCard() }

        if (hero.isEmpty() && s1.isEmpty() && s4.isEmpty()) return@withContext null

        HomeDataResult(
            heroItems = hero,
            recentReleases = s1,
            newHits = s2,
            monthSchedule = s3,
            topPopularity = s4,
            trendingManhwas = s5,
            popularMangas = s6,
            topRatedMangaManhwa = s7,
            bingeItems = s8,
            isekaiReincarnation = s9,
            featuredManhuas = s10,
            isOffline = true,
            isTokenError = false,
            errorMessage = null,
            bombandoCriterion = bombandoDiagnosticString
        )
    }

    private suspend fun saveSectionToCache(sectionKey: String, cards: List<WorkCard>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entities = cards.mapIndexed { idx, c ->
            CachedWorkEntity.fromWorkCard(c, sectionKey, idx, now)
        }
        workDao.replaceSection(sectionKey, entities)
    }

    // ==========================================
    // ETAPA 2: EXPLORE & ADVANCED FILTERS
    // ==========================================

    suspend fun exploreWorks(
        criteria: TursoQueryBuilder.ExploreFilterCriteria,
        limit: Int = 30,
        offset: Int = 0
    ): Result<List<WorkCard>> = withContext(Dispatchers.IO) {
        ensureCapabilitiesChecked()
        try {
            val (sql, args) = TursoQueryBuilder.buildExploreQuery(
                criteria = criteria,
                limit = limit,
                offset = offset,
                useAuxGenreTable = hasAuxGenreTable,
                useAuxTagTable = hasAuxTagTable
            )
            val rows = tursoClient.query(sql, args)
            Result.success(rows.map { tursoClient.mapRowToCard(it) })
        } catch (e: TursoAuthException) {
            Log.w(TAG, "Explore: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.w(TAG, "Explore error: ${e.message}")
            Result.failure(e)
        }
    }

    // Convenient overload for simple queries
    suspend fun exploreWorks(
        type: String? = null,
        genre: String? = null,
        status: String? = null,
        sortBy: String = "popularity",
        limit: Int = 24,
        offset: Int = 0
    ): Result<List<WorkCard>> = withContext(Dispatchers.IO) {
        val criteria = TursoQueryBuilder.ExploreFilterCriteria(
            types = if (type != null) listOf(type) else emptyList(),
            genres = if (genre != null) listOf(genre) else emptyList(),
            statuses = if (status != null) listOf(status) else emptyList(),
            sortBy = sortBy
        )
        exploreWorks(criteria, limit, offset)
    }

    suspend fun searchWorks(
        rawQuery: String,
        type: String? = null,
        genre: String? = null,
        limit: Int = 24,
        offset: Int = 0
    ): Result<List<WorkCard>> = withContext(Dispatchers.IO) {
        val criteria = TursoQueryBuilder.ExploreFilterCriteria(
            query = rawQuery,
            types = if (type != null) listOf(type) else emptyList(),
            genres = if (genre != null) listOf(genre) else emptyList()
        )
        exploreWorks(criteria, limit, offset)
    }

    /**
     * Parallel COUNT query for Explore with 10-minute in-memory cache
     */
    suspend fun getExploreCount(
        criteria: TursoQueryBuilder.ExploreFilterCriteria
    ): Result<Int> = withContext(Dispatchers.IO) {
        val cacheKey = criteria.toString()
        val now = System.currentTimeMillis()
        val cached = exploreCountCache[cacheKey]
        if (cached != null && (now - cached.second) < COUNT_CACHE_TTL_MS) {
            return@withContext Result.success(cached.first)
        }

        ensureCapabilitiesChecked()
        try {
            val (sql, args) = TursoQueryBuilder.buildExploreCountQuery(
                criteria = criteria,
                useAuxGenreTable = hasAuxGenreTable,
                useAuxTagTable = hasAuxTagTable
            )
            val rows = tursoClient.query(sql, args)
            val total = rows.firstOrNull()?.getInt("total") ?: 0
            exploreCountCache[cacheKey] = Pair(total, now)
            Result.success(total)
        } catch (e: TursoAuthException) {
            Log.w(TAG, "Count: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.w(TAG, "Count error: ${e.message}")
            Result.failure(e)
        }
    }

    val isAuxTablesAvailable: Boolean
        get() = hasAuxGenreTable

    suspend fun getGenres(mode: AdultMode = AdultMode.OFF): List<String> = withContext(Dispatchers.IO) {
        val cached = userPreferencesRepository.getCachedGenres(mode)
        if (!cached.isNullOrEmpty()) {
            return@withContext if (mode == AdultMode.OFF) {
                cached.filter { it !in TursoQueryBuilder.SENSITIVE_GENRES }
            } else cached
        }

        return@withContext try {
            ensureCapabilitiesChecked()
            val sql = if (hasAuxGenreTable) {
                """SELECT DISTINCT "genre" AS value FROM "WorkGenre" ORDER BY "genre" ASC"""
            } else {
                if (mode == AdultMode.OFF) {
                    """SELECT DISTINCT value FROM "Work", json_each("Work".genres) WHERE isAdult = 0 ORDER BY value ASC"""
                } else {
                    """SELECT DISTINCT value FROM "Work", json_each("Work".genres) ORDER BY value ASC"""
                }
            }
            val rows = tursoClient.query(sql)
            val list = rows.mapNotNull { it.getString("value") }
                .filter { it.isNotBlank() }
                .let { rawList ->
                    if (mode == AdultMode.OFF) rawList.filter { it !in TursoQueryBuilder.SENSITIVE_GENRES } else rawList
                }
                .sorted()

            if (list.isNotEmpty()) {
                userPreferencesRepository.saveCachedGenres(list, mode)
                list
            } else {
                defaultGenres(mode)
            }
        } catch (e: Exception) {
            defaultGenres(mode)
        }
    }

    suspend fun getScheduleWorks(adultMode: AdultMode = AdultMode.OFF): Result<List<ScheduledEpisode>> = withContext(Dispatchers.IO) {
        try {
            ensureCapabilitiesChecked()
            val (sql, args) = TursoQueryBuilder.buildScheduleQuery(
                limit = 100,
                useAuxGenreTable = hasAuxGenreTable,
                adultMode = adultMode
            )
            val rows = tursoClient.query(sql, args)
            val nowMillis = System.currentTimeMillis()
            val list = mutableListOf<ScheduledEpisode>()

            var sampleEpisodeLogged = false

            for (row in rows) {
                val card = tursoClient.mapRowToCard(row)
                val nextAt = row.getString("nextEpisodeAt") ?: ""
                val nextNum = row.getInt("nextEpisodeNum")

                if (!sampleEpisodeLogged && nextAt.isNotBlank()) {
                    userPreferencesRepository.recordDiagnostic(null, 0L, sampleEpisode = "Raw sample: $nextAt (ep $nextNum)")
                    sampleEpisodeLogged = true
                }

                val parsed = parseScheduleTime(nextAt) ?: continue

                val diffMillis = parsed.epochMillis - nowMillis
                val oneDayMillis = 24 * 60 * 60 * 1000L
                val sevenDaysMillis = 7 * 24 * 60 * 60 * 1000L
                if (diffMillis < -oneDayMillis || diffMillis > sevenDaysMillis) {
                    continue
                }

                val countdown = formatCountdown(diffMillis)
                list.add(
                    ScheduledEpisode(
                        work = card,
                        nextEpisodeAtRaw = nextAt,
                        nextEpisodeNum = nextNum,
                        displayTime = parsed.displayTime,
                        dayGroup = parsed.dayGroup,
                        countdownText = countdown,
                        epochMillis = parsed.epochMillis
                    )
                )
            }

            list.sortBy { it.epochMillis }
            Result.success(list)
        } catch (e: TursoAuthException) {
            Log.w(TAG, "Schedule: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.w(TAG, "Schedule error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getWorkDetail(id: String, adultMode: AdultMode = AdultMode.OFF): Result<Pair<WorkDetail, List<WorkCard>>> = withContext(Dispatchers.IO) {
        try {
            ensureCapabilitiesChecked()
            val (sql, args) = TursoQueryBuilder.buildDetailQuery(
                id = id,
                useAuxGenreTable = hasAuxGenreTable,
                adultMode = adultMode
            )
            val rows = tursoClient.query(sql, args)
            if (rows.isEmpty()) {
                throw NoSuchElementException("Obra não encontrada no catálogo.")
            }
            val detail = tursoClient.mapRowToDetail(rows[0])

            val similarWorks = if (detail.genres.isNotEmpty()) {
                try {
                    val firstGenre = if (adultMode == AdultMode.OFF) {
                        detail.genres.firstOrNull { it !in TursoQueryBuilder.SENSITIVE_GENRES }
                    } else {
                        detail.genres.firstOrNull()
                    }
                    if (firstGenre != null) {
                        val (simSql, simArgs) = TursoQueryBuilder.buildSimilarWorksQuery(
                            detail.id, detail.type, firstGenre, limit = 10, useAuxGenreTable = hasAuxGenreTable, adultMode = adultMode
                        )
                        val simRows = tursoClient.query(simSql, simArgs)
                        simRows.map { tursoClient.mapRowToCard(it) }
                    } else emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            } else emptyList()

            Result.success(Pair(detail, similarWorks))
        } catch (e: TursoAuthException) {
            Log.w(TAG, "Detail: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.w(TAG, "Detail error for $id: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        workDao.clearAll()
        exploreCountCache.clear()
    }

    private data class ParsedSchedule(
        val displayTime: String,
        val dayGroup: String,
        val epochMillis: Long
    )

    private fun parseScheduleTime(raw: String): ParsedSchedule? {
        if (raw.isBlank()) return null

        return try {
            val epoch = raw.toLongOrNull()
            val date: Date? = if (epoch != null) {
                Date(if (epoch < 100000000000L) epoch * 1000L else epoch)
            } else {
                val clean = raw.trim()
                val formats = listOf(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss",
                    "yyyy-MM-dd HH:mm:ss"
                )
                var parsedDate: Date? = null
                for (fmt in formats) {
                    try {
                        val sdf = SimpleDateFormat(fmt, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                        parsedDate = sdf.parse(clean)
                        if (parsedDate != null) break
                    } catch (_: Exception) {}
                }
                parsedDate
            }

            if (date != null) {
                val now = Calendar.getInstance()
                val target = Calendar.getInstance().apply { time = date }

                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val timeStr = timeFormat.format(date)

                val dayGroup = when {
                    isSameDay(now, target) -> "Hoje"
                    isNextDay(now, target) -> "Amanhã"
                    else -> SimpleDateFormat("EEE, dd/MM", Locale.forLanguageTag("pt-BR")).format(date)
                        .replaceFirstChar { it.uppercase() }
                }

                ParsedSchedule(timeStr, dayGroup, date.time)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    internal fun formatCountdown(diffMillis: Long): String {
        val fiveMinMillis = 5 * 60 * 1000L
        if (diffMillis in -fiveMinMillis..fiveMinMillis) {
            return "Agora"
        }
        if (diffMillis < 0) {
            val pastMillis = -diffMillis
            val pastHours = pastMillis / (1000 * 60 * 60)
            val pastDays = pastHours / 24
            return if (pastDays > 0) {
                "há ${pastDays}d"
            } else if (pastHours > 0) {
                "há ${pastHours}h"
            } else {
                "Lançado"
            }
        }
        val hours = diffMillis / (1000 * 60 * 60)
        val minutes = (diffMillis / (1000 * 60)) % 60
        val days = hours / 24
        return if (days > 0) {
            "em ${days}d"
        } else if (hours > 0) {
            if (minutes > 0) "em ${hours}h ${minutes}m" else "em ${hours}h"
        } else {
            "em ${minutes}m"
        }
    }

    private fun isSameDay(c1: Calendar, c2: Calendar): Boolean {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isNextDay(c1: Calendar, c2: Calendar): Boolean {
        val clone = c1.clone() as Calendar
        clone.add(Calendar.DAY_OF_YEAR, 1)
        return isSameDay(clone, c2)
    }

    private fun defaultGenres(mode: AdultMode = AdultMode.OFF): List<String> {
        val anilistGenres = listOf(
            "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy",
            "Horror", "Mahou Shoujo", "Mecha", "Music", "Mystery",
            "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports",
            "Supernatural", "Thriller"
        )
        return if (mode != AdultMode.OFF) anilistGenres + TursoQueryBuilder.SENSITIVE_GENRES else anilistGenres
    }

    suspend fun getCachedExtraSections(): List<ExtraHomeSection> = withContext(Dispatchers.IO) {
        try {
            val oldCacheFile = File(userPreferencesRepository.context.cacheDir, OLD_EXTRA_SECTIONS_CACHE_FILE)
            if (oldCacheFile.exists()) {
                try { oldCacheFile.delete() } catch (_: Exception) {}
            }
            val cacheFile = File(userPreferencesRepository.context.cacheDir, EXTRA_SECTIONS_CACHE_FILE)
            if (cacheFile.exists() && cacheFile.length() > 0) {
                val jsonStr = cacheFile.readText()
                return@withContext parseExtraSectionsJson(jsonStr)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to read extra sections cache: ${e.message}")
        }
        emptyList()
    }

    suspend fun fetchExtraSections(): List<ExtraHomeSection> = withContext(Dispatchers.IO) {
        try {
            val oldCacheFile = File(userPreferencesRepository.context.cacheDir, OLD_EXTRA_SECTIONS_CACHE_FILE)
            if (oldCacheFile.exists()) {
                try { oldCacheFile.delete() } catch (_: Exception) {}
            }

            val sql = """SELECT key, title, position, itemsJson, updatedAt FROM "HomeSection" ORDER BY position"""
            val rows = tursoClient.query(sql)
            if (rows.isEmpty()) {
                val cacheFile = File(userPreferencesRepository.context.cacheDir, EXTRA_SECTIONS_CACHE_FILE)
                if (cacheFile.exists()) {
                    try { cacheFile.delete() } catch (_: Exception) {}
                }
                return@withContext emptyList()
            }

            val cacheArray = JSONArray()
            val resultSections = mutableListOf<ExtraHomeSection>()

            for (row in rows) {
                val key = TursoClient.sanitizeText(row.getString("key")) ?: ""
                val title = TursoClient.sanitizeText(row.getString("title")) ?: ""
                val position = row.getInt("position") ?: 0
                val itemsJson = row.getString("itemsJson") ?: ""
                val updatedAt = TursoClient.sanitizeText(row.getString("updatedAt"))

                // Cache raw row
                val rowObj = JSONObject().apply {
                    put("key", key)
                    put("title", title)
                    put("position", position)
                    put("itemsJson", itemsJson)
                    put("updatedAt", updatedAt ?: "")
                }
                cacheArray.put(rowObj)

                val items = tursoClient.parseWorkCardsFromItemsJson(itemsJson)
                if (items.size >= 6) {
                    resultSections.add(
                        ExtraHomeSection(
                            key = key,
                            title = title,
                            position = position,
                            items = items,
                            updatedAt = updatedAt
                        )
                    )
                }
            }

            // Save to cacheDir (home_extra_sections_cache_v2.json)
            if (cacheArray.length() > 0) {
                try {
                    val cacheFile = File(userPreferencesRepository.context.cacheDir, EXTRA_SECTIONS_CACHE_FILE)
                    cacheFile.writeText(cacheArray.toString())
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to write extra sections cache: ${e.message}")
                }
            }

            resultSections.sortedBy { it.position }.take(12)
        } catch (e: Exception) {
            // Silently ignore table not existing or query errors
            Log.d(TAG, "Extra sections query ignored: ${e.message}")
            emptyList()
        }
    }

    fun parseExtraSectionsJson(jsonStr: String): List<ExtraHomeSection> {
        if (jsonStr.isBlank()) return emptyList()
        val list = mutableListOf<ExtraHomeSection>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val key = TursoClient.sanitizeText(obj.opt("key")) ?: ""
                val title = TursoClient.sanitizeText(obj.opt("title")) ?: ""
                val position = obj.optInt("position", 0)
                val itemsJson = obj.optString("itemsJson", "")
                val updatedAt = TursoClient.sanitizeText(obj.opt("updatedAt"))
                val items = tursoClient.parseWorkCardsFromItemsJson(itemsJson)
                if (items.size >= 6) {
                    list.add(
                        ExtraHomeSection(
                            key = key,
                            title = title,
                            position = position,
                            items = items,
                            updatedAt = updatedAt
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list.sortedBy { it.position }.take(12)
    }

    fun filterAndDeduplicateExtraSections(
        rawSections: List<ExtraHomeSection>,
        fixedLists: List<List<WorkCard>>
    ): List<ExtraHomeSection> {
        val fixedIds = fixedLists.flatMap { it.mapNotNull { card -> card.id.takeIf { id -> id.isNotBlank() } } }.toSet()
        return filterAndDeduplicateExtraSections(rawSections, fixedIds)
    }

    fun filterAndDeduplicateExtraSections(
        rawSections: List<ExtraHomeSection>,
        existingIds: Set<String>
    ): List<ExtraHomeSection> {
        val seenIds = existingIds.filter { it.isNotBlank() }.toMutableSet()
        val result = mutableListOf<ExtraHomeSection>()
        val sortedSections = rawSections.sortedBy { it.position }
        for (sec in sortedSections) {
            val uniqueItems = sec.items.filter { it.id.isNotBlank() && it.id !in seenIds }
            if (uniqueItems.size >= 6) {
                uniqueItems.forEach { seenIds.add(it.id) }
                result.add(sec.copy(items = uniqueItems))
                if (result.size == 12) break
            }
        }
        return result
    }
}
