package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.WorkCard

@Entity(
    tableName = "cached_works",
    primaryKeys = ["section", "id"]
)
data class CachedWorkEntity(
    val section: String, // "carousel_hype", "carousel_top_rated", "carousel_new_releases", "popular", "top_rated", "trending", "updated"
    val id: String,
    val position: Int,
    val type: String,
    val format: String,
    val title: String,
    val titleEn: String?,
    val cover: String,
    val coverColor: String?,
    val avgScore: Int?,
    val popularity: Int?,
    val status: String,
    val genresJson: String,
    val synopsis: String? = null,
    val banner: String? = null,
    val startDate: String? = null,
    val nextEpisodeAt: String? = null,
    val nextEpisodeNum: Int? = null,
    val favourites: Int? = null,
    val isAdult: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis()
) {
    fun toWorkCard(): WorkCard {
        val genresList = if (genresJson.isNotBlank()) {
            genresJson.split(";;").filter { it.isNotBlank() }
        } else emptyList()

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

    companion object {
        fun fromWorkCard(card: WorkCard, section: String, position: Int, cachedAt: Long = System.currentTimeMillis()): CachedWorkEntity {
            return CachedWorkEntity(
                section = section,
                id = card.id,
                position = position,
                type = card.type,
                format = card.format,
                title = card.title,
                titleEn = card.titleEn,
                cover = card.cover,
                coverColor = card.coverColor,
                banner = card.banner,
                avgScore = card.avgScore,
                popularity = card.popularity,
                status = card.status,
                genresJson = card.genres.joinToString(";;"),
                startDate = card.startDate,
                nextEpisodeAt = card.nextEpisodeAt,
                nextEpisodeNum = card.nextEpisodeNum,
                favourites = card.favourites,
                isAdult = card.isAdult,
                cachedAt = cachedAt
            )
        }
    }
}

/**
 * LibraryEntity with redesigned schema (Stage C preparation and migration support):
 * Supports both listType (v1) and isFavorite/status (v2)
 */
@Entity(tableName = "library_works")
data class LibraryEntity(
    @PrimaryKey val id: String,
    val status: String? = "PLANNING", // PLANNING, READING, COMPLETED, or null
    val isFavorite: Boolean = false,
    val currentProgress: Int = 0,
    val totalProgress: Int? = null,
    val userRating: Int? = null, // 1 to 10
    val addedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Card snapshot fields
    val workType: String = "anime",
    val format: String = "",
    val title: String = "",
    val titleEn: String? = null,
    val cover: String = "",
    val coverColor: String? = null,
    val avgScore: Int? = null,
    val workStatus: String = "",
    val genresJson: String = "",
    val isDeleted: Boolean = false,
    val duration: Int? = null,
    val nextEpisodeAt: String? = null
) {
    fun toWorkCard(): WorkCard {
        val genresList = if (genresJson.isNotBlank()) {
            genresJson.split(";;").filter { it.isNotBlank() }
        } else emptyList()

        return WorkCard(
            id = id,
            type = workType,
            format = format,
            title = title,
            titleEn = titleEn,
            cover = cover,
            coverColor = coverColor,
            avgScore = avgScore,
            popularity = null,
            status = workStatus,
            genres = genresList
        )
    }
}
