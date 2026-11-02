package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class WorkCard(
    val id: String,
    val type: String = "anime", // anime, manga, manhwa, manhua, lightnovel, webnovel
    val format: String = "",
    val title: String = "",
    val titleEn: String? = null,
    val cover: String = "",
    val coverColor: String? = null,
    val banner: String? = null,
    val avgScore: Int? = null,
    val popularity: Int? = null,
    val status: String = "",
    val genres: List<String> = emptyList(),
    val startDate: String? = null,
    val nextEpisodeAt: String? = null,
    val nextEpisodeNum: Int? = null,
    val favourites: Int? = null,
    val isAdult: Boolean = false
) {
    val is18Plus: Boolean
        get() = isAdult || genres.any { g ->
            g.equals("Adult", ignoreCase = true) ||
            g.equals("Hentai", ignoreCase = true) ||
            g.equals("Erotica", ignoreCase = true) ||
            g.equals("Smut", ignoreCase = true)
        }
    val displayTitle: String
        get() = titleEn?.takeIf { it.isNotBlank() } ?: title.takeIf { it.isNotBlank() } ?: "Sem título"

    val scoreFormatted: String
        get() = avgScore?.let { String.format("%.1f", it / 10.0) } ?: "-"

    val typeLabel: String
        get() = when (type.lowercase()) {
            "anime" -> "Anime"
            "manga" -> "Mangá"
            "manhwa" -> "Manhwa"
            "manhua" -> "Manhua"
            "lightnovel" -> "Light Novel"
            "webnovel" -> "Web Novel"
            "novel" -> "Light Novel"
            else -> type.replaceFirstChar { it.uppercase() }
        }

    val releaseYear: String?
        get() = startDate?.trim()?.take(4)?.takeIf { it.length == 4 && it.all { c -> c.isDigit() } }

    val popularCountFormatted: String
        get() {
            val count = favourites ?: popularity ?: return ""
            return if (count >= 1000) {
                String.format(Locale.US, "%.1fk", count / 1000.0)
            } else {
                count.toString()
            }
        }

    val agendaBadge: AgendaBadgeInfo?
        get() = parseAgendaBadge(startDate, nextEpisodeAt, nextEpisodeNum)
}

data class AgendaBadgeInfo(
    val dateText: String,
    val tagText: String
)

fun parseAgendaBadge(startDate: String?, nextEpisodeAt: String?, nextEpisodeNum: Int?): AgendaBadgeInfo? {
    val ptBr = Locale.forLanguageTag("pt-BR")
    if (!nextEpisodeAt.isNullOrBlank()) {
        val raw = nextEpisodeAt.trim()
        val tag = nextEpisodeNum?.let { "Ep. $it" } ?: "Próximo Ep."
        val epoch = raw.toLongOrNull()
        if (epoch != null) {
            val ms = if (epoch < 100000000000L) epoch * 1000L else epoch
            val dateStr = SimpleDateFormat("dd MMM", ptBr).format(Date(ms))
            return AgendaBadgeInfo(dateStr, tag)
        }
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val d = sdf.parse(raw)
                if (d != null) {
                    val dateStr = SimpleDateFormat("dd MMM", ptBr).format(d)
                    return AgendaBadgeInfo(dateStr, tag)
                }
            } catch (_: Exception) {}
        }
        if (raw.length >= 10 && raw[4] == '-' && raw[7] == '-') {
            try {
                val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw.take(10))
                if (d != null) {
                    val dateStr = SimpleDateFormat("dd MMM", ptBr).format(d)
                    return AgendaBadgeInfo(dateStr, tag)
                }
            } catch (_: Exception) {}
        }
        if (raw.length >= 7 && raw[4] == '-') {
            try {
                val d = SimpleDateFormat("yyyy-MM", Locale.US).parse(raw.take(7))
                if (d != null) {
                    val dateStr = SimpleDateFormat("MMM yyyy", ptBr).format(d)
                    return AgendaBadgeInfo(dateStr, "Data estimada")
                }
            } catch (_: Exception) {}
        }
    }
    if (!startDate.isNullOrBlank()) {
        val raw = startDate.trim()
        val epoch = raw.toLongOrNull()
        if (epoch != null) {
            val ms = if (epoch < 100000000000L) epoch * 1000L else epoch
            val dateStr = SimpleDateFormat("dd MMM", ptBr).format(Date(ms))
            return AgendaBadgeInfo(dateStr, "Estreia")
        }
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val d = sdf.parse(raw)
                if (d != null) {
                    val dateStr = SimpleDateFormat("dd MMM", ptBr).format(d)
                    return AgendaBadgeInfo(dateStr, "Estreia")
                }
            } catch (_: Exception) {}
        }
        if (raw.length >= 10 && raw[4] == '-' && raw[7] == '-') {
            try {
                val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw.take(10))
                if (d != null) {
                    val dateStr = SimpleDateFormat("dd MMM", ptBr).format(d)
                    return AgendaBadgeInfo(dateStr, "Estreia")
                }
            } catch (_: Exception) {}
        }
        if (raw.length >= 7 && raw[4] == '-') {
            try {
                val d = SimpleDateFormat("yyyy-MM", Locale.US).parse(raw.take(7))
                if (d != null) {
                    val dateStr = SimpleDateFormat("MMM yyyy", ptBr).format(d)
                    return AgendaBadgeInfo(dateStr, "Data estimada")
                }
            } catch (_: Exception) {}
        }
        if (raw.length == 4 && raw.all { it.isDigit() }) {
            return AgendaBadgeInfo(raw, "Data estimada")
        }
    }
    return null
}

fun getAgendaTimestamp(work: WorkCard): Long? {
    if (!work.nextEpisodeAt.isNullOrBlank()) {
        val raw = work.nextEpisodeAt.trim()
        val epoch = raw.toLongOrNull()
        if (epoch != null) {
            return if (epoch < 100000000000L) epoch * 1000L else epoch
        }
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                val d = sdf.parse(raw)
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
        if (raw.length >= 7 && raw[4] == '-') {
            try {
                val d = SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(raw.take(7))
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
    }
    if (!work.startDate.isNullOrBlank()) {
        val raw = work.startDate.trim()
        val epoch = raw.toLongOrNull()
        if (epoch != null) {
            return if (epoch < 100000000000L) epoch * 1000L else epoch
        }
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                val d = sdf.parse(raw)
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
        if (raw.length >= 7 && raw[4] == '-') {
            try {
                val d = SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(raw.take(7))
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
        if (raw.length == 4 && raw.all { it.isDigit() }) {
            try {
                val d = SimpleDateFormat("yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(raw)
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
    }
    return null
}

data class AuthorItem(
    val name: String,
    val role: String? = null
)

data class ExtLinkItem(
    val site: String,
    val url: String,
    val type: String? = null,
    val color: String? = null,
    val icon: String? = null
)

data class WorkDetail(
    val id: String,
    val malId: Int? = null,
    val type: String = "anime",
    val format: String = "",
    val title: String = "",
    val titleEn: String? = null,
    val titleNative: String? = null,
    val synonyms: List<String> = emptyList(),
    val synopsis: String? = null,
    val synopsisPT: String? = null,
    val cover: String = "",
    val coverAlt: String? = null,
    val coverColor: String? = null,
    val banner: String? = null,
    val status: String = "",
    val chapters: Int? = null,
    val volumes: Int? = null,
    val episodes: Int? = null,
    val duration: Int? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val country: String? = null,
    val isAdult: Boolean = false,
    val classification: String? = null,
    val genres: List<String> = emptyList(),
    val tags: List<TagItem> = emptyList(),
    val avgScore: Int? = null,
    val popularity: Int? = null,
    val favourites: Int? = null,
    val authors: List<AuthorItem> = emptyList(),
    val extLinks: List<ExtLinkItem> = emptyList(),
    val magazine: String? = null,
    val network: String? = null,
    val source: String? = null,
    val siteUrl: String? = null,
    val lastUpdate: Long? = null,
    val nextEpisodeAt: String? = null,
    val nextEpisodeNum: Int? = null,
    val coverAlts: List<String> = emptyList(),
    val trending: Int? = null,
    val follows: Int? = null
) {
    val displayTitle: String
        get() = titleEn?.takeIf { it.isNotBlank() } ?: title.takeIf { it.isNotBlank() } ?: "Sem título"

    val displaySynopsis: String
        get() = synopsisPT?.takeIf { it.isNotBlank() } ?: synopsis?.takeIf { it.isNotBlank() } ?: "Sinopse não disponível."

    val scoreFormatted: String
        get() = avgScore?.let { String.format("%.1f", it / 10.0) } ?: "-"

    fun toCard(): WorkCard = WorkCard(
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
        genres = genres,
        startDate = startDate,
        nextEpisodeAt = nextEpisodeAt,
        nextEpisodeNum = nextEpisodeNum,
        favourites = favourites,
        isAdult = isAdult
    )
}

data class TagItem(
    val name: String,
    val rank: Int = 0
)

data class ScheduledEpisode(
    val work: WorkCard,
    val nextEpisodeAtRaw: String,
    val nextEpisodeNum: Int?,
    val displayTime: String,
    val dayGroup: String, // Hoje, Amanhã, ou dia da semana
    val countdownText: String = "",
    val epochMillis: Long = 0L
)

data class ExtraHomeSection(
    val key: String,
    val title: String,
    val position: Int = 0,
    val items: List<WorkCard> = emptyList(),
    val updatedAt: String? = null
)

