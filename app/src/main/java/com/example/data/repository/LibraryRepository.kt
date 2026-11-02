package com.example.data.repository

import com.example.data.local.LibraryDao
import com.example.data.local.LibraryEntity
import com.example.data.model.WorkCard
import com.example.data.model.WorkDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

enum class LibraryTab(val title: String) {
    ALL("Todos"),
    FAVORITES("Favoritos"),
    PLANNING("Quero ver/ler"),
    READING("Em andamento"),
    COMPLETED("Concluídos")
}

data class LibraryStats(
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val readingCount: Int = 0,
    val planningCount: Int = 0,
    val averagePersonalRating: Double? = null,
    val estimatedTimeMinutes: Long = 0L,
    val topGenres: List<Pair<String, Int>> = emptyList()
)

class LibraryRepository(private val libraryDao: LibraryDao) {

    val libraryMap: Flow<Map<String, LibraryEntity>> = libraryDao.getAllWorks().map { works ->
        works.associateBy { it.id }
    }

    fun getWorksForTab(tab: LibraryTab): Flow<List<LibraryEntity>> {
        return libraryDao.getAllWorks().map { works ->
            when (tab) {
                LibraryTab.ALL -> works
                LibraryTab.FAVORITES -> works.filter { it.isFavorite }
                LibraryTab.PLANNING -> works.filter { it.status == "PLANNING" }
                LibraryTab.READING -> works.filter { it.status == "READING" }
                LibraryTab.COMPLETED -> works.filter { it.status == "COMPLETED" }
            }
        }
    }

    fun observeWorkStatus(id: String): Flow<LibraryEntity?> {
        return libraryDao.observeById(id)
    }

    suspend fun getWorkStatus(id: String): LibraryEntity? {
        return libraryDao.getById(id)
    }

    fun getAllSavedIdsFlow(): Flow<Set<String>> {
        return libraryDao.getAllWorks().map { works -> works.map { it.id }.toSet() }
    }

    val stats: Flow<LibraryStats> = libraryDao.getAllWorks().map { works ->
        val total = works.size
        val completed = works.count { it.status == "COMPLETED" }
        val reading = works.count { it.status == "READING" }
        val planning = works.count { it.status == "PLANNING" }
        val ratedWorks = works.mapNotNull { it.userRating }
        val avgRating = if (ratedWorks.isNotEmpty()) ratedWorks.average() else null

        // Estimated time: sum of (episodes/chapters * duration)
        var totalMinutes = 0L
        works.filter { it.status == "COMPLETED" }.forEach { work ->
            val count = (work.totalProgress ?: work.currentProgress).coerceAtLeast(1)
            val dur = (work.duration ?: if (work.workType.equals("anime", ignoreCase = true)) 24 else 8)
            totalMinutes += (count * dur).toLong()
        }

        // Top 5 genres
        val genreCounts = mutableMapOf<String, Int>()
        works.forEach { work ->
            if (work.genresJson.isNotBlank()) {
                work.genresJson.split(";;").filter { it.isNotBlank() }.forEach { g ->
                    genreCounts[g] = (genreCounts[g] ?: 0) + 1
                }
            }
        }
        val topGenres = genreCounts.toList().sortedByDescending { it.second }.take(5)

        LibraryStats(
            totalCount = total,
            completedCount = completed,
            readingCount = reading,
            planningCount = planning,
            averagePersonalRating = avgRating,
            estimatedTimeMinutes = totalMinutes,
            topGenres = topGenres
        )
    }

    fun getTabCounts(): Flow<Map<LibraryTab, Int>> {
        return libraryDao.getAllWorks().map { works ->
            mapOf(
                LibraryTab.ALL to works.size,
                LibraryTab.FAVORITES to works.count { it.isFavorite },
                LibraryTab.PLANNING to works.count { it.status == "PLANNING" },
                LibraryTab.READING to works.count { it.status == "READING" },
                LibraryTab.COMPLETED to works.count { it.status == "COMPLETED" }
            )
        }
    }

    suspend fun saveOrUpdateWork(
        work: WorkDetail,
        status: String? = "PLANNING",
        isFavorite: Boolean? = null,
        progress: Int? = null,
        rating: Int? = null
    ) {
        val existing = libraryDao.getByIdIncludingDeleted(work.id)
        val now = System.currentTimeMillis()

        val entity = LibraryEntity(
            id = work.id,
            status = status ?: existing?.status,
            isFavorite = isFavorite ?: existing?.isFavorite ?: false,
            currentProgress = progress ?: existing?.currentProgress ?: 0,
            totalProgress = work.episodes ?: work.chapters ?: existing?.totalProgress,
            userRating = rating ?: existing?.userRating,
            addedAt = existing?.addedAt ?: now,
            updatedAt = now,
            // Snapshot
            workType = work.type,
            format = work.format,
            title = work.title,
            titleEn = work.titleEn,
            cover = work.cover,
            coverColor = work.coverColor,
            avgScore = work.avgScore,
            workStatus = work.status,
            genresJson = work.genres.joinToString(";;"),
            isDeleted = false,
            duration = work.duration ?: existing?.duration,
            nextEpisodeAt = work.nextEpisodeAt ?: existing?.nextEpisodeAt
        )
        libraryDao.insertOrUpdate(entity)
    }

    suspend fun saveOrUpdateCard(
        card: WorkCard,
        status: String? = "PLANNING",
        isFavorite: Boolean? = null
    ) {
        val existing = libraryDao.getById(card.id)
        val now = System.currentTimeMillis()

        val entity = LibraryEntity(
            id = card.id,
            status = status ?: existing?.status,
            isFavorite = isFavorite ?: existing?.isFavorite ?: false,
            currentProgress = existing?.currentProgress ?: 0,
            totalProgress = existing?.totalProgress,
            userRating = existing?.userRating,
            addedAt = existing?.addedAt ?: now,
            updatedAt = now,
            workType = card.type,
            format = card.format,
            title = card.title,
            titleEn = card.titleEn,
            cover = card.cover,
            coverColor = card.coverColor,
            avgScore = card.avgScore,
            workStatus = card.status,
            genresJson = card.genres.joinToString(";;")
        )
        libraryDao.insertOrUpdate(entity)
    }

    suspend fun toggleFavorite(id: String) {
        val existing = libraryDao.getById(id) ?: return
        libraryDao.insertOrUpdate(existing.copy(isFavorite = !existing.isFavorite, updatedAt = System.currentTimeMillis()))
    }

    suspend fun toggleFavorite(card: WorkCard) {
        val existing = libraryDao.getById(card.id)
        if (existing != null) {
            libraryDao.insertOrUpdate(existing.copy(isFavorite = !existing.isFavorite, updatedAt = System.currentTimeMillis()))
        } else {
            saveOrUpdateCard(card, status = null, isFavorite = true)
        }
    }

    suspend fun togglePlanning(card: WorkCard) {
        val existing = libraryDao.getById(card.id)
        if (existing != null) {
            val newStatus = if (existing.status == "PLANNING") null else "PLANNING"
            libraryDao.insertOrUpdate(existing.copy(status = newStatus, updatedAt = System.currentTimeMillis()))
        } else {
            saveOrUpdateCard(card, status = "PLANNING", isFavorite = false)
        }
    }

    suspend fun setStatus(id: String, status: String?) {
        val existing = libraryDao.getById(id) ?: return
        libraryDao.insertOrUpdate(existing.copy(status = status, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateProgress(id: String, newProgress: Int) {
        val existing = libraryDao.getById(id) ?: return
        val clamped = newProgress.coerceAtLeast(0)
        val shouldSuggestCompleted = existing.totalProgress != null && clamped >= existing.totalProgress && existing.status != "COMPLETED"
        libraryDao.insertOrUpdate(
            existing.copy(
                currentProgress = clamped,
                status = if (shouldSuggestCompleted) "COMPLETED" else existing.status,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateRating(id: String, rating: Int?) {
        val existing = libraryDao.getById(id) ?: return
        val clamped = rating?.coerceIn(1, 10)
        libraryDao.insertOrUpdate(existing.copy(userRating = clamped, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateSnapshot(detail: WorkDetail) {
        val existing = libraryDao.getById(detail.id) ?: return
        libraryDao.insertOrUpdate(
            existing.copy(
                workType = detail.type,
                format = detail.format,
                title = detail.title,
                titleEn = detail.titleEn,
                cover = detail.cover,
                coverColor = detail.coverColor,
                avgScore = detail.avgScore,
                workStatus = detail.status,
                genresJson = detail.genres.joinToString(";;"),
                totalProgress = detail.episodes ?: detail.chapters ?: existing.totalProgress,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteById(id: String) {
        libraryDao.markDeleted(id)
    }

    suspend fun removeFromLibrary(id: String) {
        libraryDao.markDeleted(id)
    }

    suspend fun deletePermanently(id: String) {
        libraryDao.deletePermanently(id)
    }

    suspend fun restoreWork(entity: LibraryEntity) {
        libraryDao.insertOrUpdate(entity.copy(isDeleted = false, updatedAt = System.currentTimeMillis()))
    }

    suspend fun insertOrUpdate(entity: LibraryEntity) {
        libraryDao.insertOrUpdate(entity)
    }

    suspend fun clearAll() {
        libraryDao.clearAll()
    }

    suspend fun getAllSnapshotIncludingDeleted(): List<LibraryEntity> {
        return libraryDao.getAllSnapshotIncludingDeleted()
    }

    suspend fun exportToJson(): String {
        val all = libraryDao.getAllSnapshot()
        val root = JSONObject()
        root.put("version", 2)
        root.put("exportedAt", System.currentTimeMillis())

        val arr = JSONArray()
        all.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("status", item.status)
                put("isFavorite", item.isFavorite)
                put("currentProgress", item.currentProgress)
                put("totalProgress", item.totalProgress)
                put("userRating", item.userRating)
                put("addedAt", item.addedAt)
                put("updatedAt", item.updatedAt)
                put("workType", item.workType)
                put("format", item.format)
                put("title", item.title)
                put("titleEn", item.titleEn)
                put("cover", item.cover)
                put("coverColor", item.coverColor)
                put("avgScore", item.avgScore)
                put("workStatus", item.workStatus)
                put("genresJson", item.genresJson)
            }
            arr.put(obj)
        }
        root.put("works", arr)
        return root.toString(2)
    }

    suspend fun importFromJson(jsonString: String): Result<Int> {
        return try {
            val root = JSONObject(jsonString)
            val arr = root.optJSONArray("works") ?: JSONArray()
            var imported = 0
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                if (id.isBlank()) continue

                val entity = LibraryEntity(
                    id = id,
                    status = obj.optString("status").takeIf { it.isNotBlank() && it != "null" },
                    isFavorite = obj.optBoolean("isFavorite", false),
                    currentProgress = obj.optInt("currentProgress", 0),
                    totalProgress = if (obj.has("totalProgress") && !obj.isNull("totalProgress")) obj.optInt("totalProgress") else null,
                    userRating = if (obj.has("userRating") && !obj.isNull("userRating")) obj.optInt("userRating") else null,
                    addedAt = obj.optLong("addedAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                    workType = obj.optString("workType", "anime"),
                    format = obj.optString("format", ""),
                    title = obj.optString("title", ""),
                    titleEn = obj.optString("titleEn").takeIf { it.isNotBlank() && it != "null" },
                    cover = obj.optString("cover", ""),
                    coverColor = obj.optString("coverColor").takeIf { it.isNotBlank() && it != "null" },
                    avgScore = if (obj.has("avgScore") && !obj.isNull("avgScore")) obj.optInt("avgScore") else null,
                    workStatus = obj.optString("workStatus", ""),
                    genresJson = obj.optString("genresJson", "")
                )
                libraryDao.insertOrUpdate(entity)
                imported++
            }
            Result.success(imported)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
