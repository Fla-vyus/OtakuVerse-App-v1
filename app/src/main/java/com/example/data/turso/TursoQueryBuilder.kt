package com.example.data.turso

import com.example.data.model.AdultMode

object TursoQueryBuilder {

    const val CARD_COLS = """w."id", w."type", w."format", w."title", w."titleEn", w."cover", w."coverColor", w."banner", w."avgScore", w."popularity", w."status", w."genres", w."startDate", w."nextEpisodeAt", w."nextEpisodeNum", w."favourites", w."isAdult""""
    const val HERO_COLS = """w."id", w."type", w."format", w."title", w."titleEn", w."cover", w."coverColor", w."banner", w."avgScore", w."popularity", w."status", w."genres", w."startDate", w."nextEpisodeAt", w."nextEpisodeNum", w."favourites", w."isAdult""""

    val SENSITIVE_GENRES = listOf("Adult", "Hentai", "Erotica", "Smut")

    /**
     * Safety filter handling AdultMode (B3):
     * - OFF: isAdult = 0 AND NOT EXISTS sensitive genres
     * - INCLUDE: 1=1 (all content included)
     * - ONLY: isAdult = 1 OR EXISTS sensitive genres
     */
    fun contentSafetyFilter(
        alias: String = "w",
        useAuxGenreTable: Boolean = false,
        adultMode: AdultMode = AdultMode.OFF
    ): String {
        val table = if (alias.isNotBlank()) "$alias." else ""
        return when (adultMode) {
            AdultMode.OFF -> {
                if (useAuxGenreTable) {
                    """${table}"isAdult" = 0 AND NOT EXISTS (SELECT 1 FROM "WorkGenre" wg WHERE wg."workId" = ${table}"id" AND wg."genre" IN ('Adult', 'Hentai', 'Erotica', 'Smut'))"""
                } else {
                    """${table}"isAdult" = 0 AND NOT EXISTS (SELECT 1 FROM json_each(${table}"genres") WHERE value IN ('Adult', 'Hentai', 'Erotica', 'Smut'))"""
                }
            }
            AdultMode.INCLUDE -> {
                "1=1"
            }
            AdultMode.ONLY -> {
                if (useAuxGenreTable) {
                    """(${table}"isAdult" = 1 OR EXISTS (SELECT 1 FROM "WorkGenre" wg WHERE wg."workId" = ${table}"id" AND wg."genre" IN ('Adult', 'Hentai', 'Erotica', 'Smut')))"""
                } else {
                    """(${table}"isAdult" = 1 OR EXISTS (SELECT 1 FROM json_each(${table}"genres") WHERE value IN ('Adult', 'Hentai', 'Erotica', 'Smut')))"""
                }
            }
        }
    }

    fun buildAuxiliaryTablesCheckQuery(): String {
        return """SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('WorkGenre', 'WorkTag')"""
    }

    fun buildProbeChaptersQuery(): String {
        return """SELECT COUNT(*) AS total, COUNT("chapters") AS chaptersCount FROM "Work" WHERE "isAdult" = 0 AND "type" IN ('manga','manhwa','manhua') AND "status" = 'RELEASING'"""
    }

    /**
     * Sanitizes search input for FTS5:
     * - Removes quotes, parentheses, stars, AND/OR/NOT
     * - Formats terms as "term"*
     */
    fun sanitizeFtsQuery(input: String): String {
        val cleaned = input
            .replace(Regex("[\"'\t\n\r\\*\\(\\)\\{\\}\\[\\]\\^~:]"), " ")
            .replace(Regex("\\b(AND|OR|NOT)\\b", RegexOption.IGNORE_CASE), " ")
            .trim()

        val tokens = cleaned.split(Regex("\\s+")).filter { it.length >= 2 }
        if (tokens.isEmpty()) return ""

        return tokens.joinToString(" ") { token -> "\"$token\"*" }
    }

    // ==========================================
    // ETAPA 1: HERO & HOME SECTIONS (1 TO 10)
    // ==========================================

    fun buildHeroQuery(
        todayMinus180DaysIso: String,
        limit: Int = 8,
        useAuxGenreTable: Boolean = false
    ): Pair<String, List<TursoArg>> {
        val safeLimit = limit.coerceIn(1, 24)
        val safety = contentSafetyFilter("w", useAuxGenreTable)
        val sql = """
            SELECT $HERO_COLS
            FROM "Work" w
            WHERE $safety
              AND w."banner" IS NOT NULL AND w."banner" != ''
              AND w."startDate" >= ?
            ORDER BY w."trending" DESC
            LIMIT ?
        """.trimIndent()
        return Pair(sql, listOf(TursoArg.Text(todayMinus180DaysIso), TursoArg.Integer(safeLimit.toLong())))
    }

    fun buildHeroFallbackQuery(
        limit: Int = 8,
        useAuxGenreTable: Boolean = false
    ): Pair<String, List<TursoArg>> {
        val safeLimit = limit.coerceIn(1, 24)
        val safety = contentSafetyFilter("w", useAuxGenreTable)
        val sql = """
            SELECT $HERO_COLS
            FROM "Work" w
            WHERE $safety
            ORDER BY (w."banner" IS NOT NULL AND w."banner" != '') DESC, w."trending" DESC
            LIMIT ?
        """.trimIndent()
        return Pair(sql, listOf(TursoArg.Integer(safeLimit.toLong())))
    }

    // 1. "Lançamentos recentes": startDate entre (hoje − 90 dias) e hoje, ORDER BY popularity DESC.
    fun buildRecentReleasesQuery(
        startDateFrom: String,
        startDateTo: String,
        limit: Int = 20,
        useAuxGenreTable: Boolean = false
    ): Pair<String, List<TursoArg>> {
        val safeLimit = limit.coerceIn(1, 24)
        val safety = contentSafetyFilter("w", useAuxGenreTable)
        val sql = """
            SELECT $CARD_COLS
            FROM "Work" w
            WHERE $safety
              AND w."startDate" >= ? AND w."startDate" <= ?
            ORDER BY w."popularity" DESC
            LIMIT ?
        """.trimIndent()
        return Pair(sql, listOf(TursoArg.Text(startDateFrom), TursoArg.Text(startDateTo), TursoArg.Integer(safeLimit.toLong())))
    }

    // 2. "Bombando: novos hits": type IN ('manga','manhwa','manhua'), status = 'RELEASING', ORDER BY trending DESC, popularity DESC.
    fun buildNewHitsQuery(
        useChaptersCount: Boolean,
        minStartDate: String,
        limit: Int = 20,
        useAuxGenreTable: Boolean = false
    ): Pair<String, List<TursoArg>> {
        val safeLimit = limit.coerceIn(1, 24)
        val safety = contentSafetyFilter("w", useAuxGenreTable)
        val (extraCondition, extraArgs) = if (useChaptersCount) {
            Pair("""AND w."chapters" IS NOT NULL AND w."chapters" <= 80""", emptyList())
        } else {
            Pair("""AND w."startDate" >= ?""", listOf(TursoArg.Text(minStartDate)))
        }

        val sql = """
            SELECT $CARD_COLS
            FROM "Work" w
            WHERE $safety
              AND w."type" IN ('manga', 'manhwa', 'manhua')
              AND w."status" = 'RELEASING'
              $extraCondition
            ORDER BY w."trending" DESC, w."popularity" DESC
            LIMIT ?
        """.trimIndent()

        val args = extraArgs + TursoArg.Integer(safeLimit.toLong())
        return Pair(sql, args)
    }

    // 3. "Agenda do mês": obras com estreia (startDate) ou próximo episódio/capítulo (nextEpisodeAt) nos próximos 30 dias.
    fun buildMonthScheduleQuery(
        windowStart: String,
        windowEnd: String,
        limit: Int = 30,
        useAuxGenreTable: Boolean = false
    ): Pair<String, List<TursoArg>> {
        val safeLimit = limit.coerceIn(1, 40)
        val safety = contentSafetyFilter("w", useAuxGenreTable)
        val sql = """
            SELECT $CARD_COLS
            FROM "Work" w
            WHERE $safety
              AND (
                (w."startDate" >= ? AND w."startDate" <= ?)
                OR (w."nextEpisodeAt" >= ? AND w."nextEpisodeAt" <= ?)
                OR (w."status" = 'RELEASING' AND w."nextEpisodeAt" IS NOT NULL AND w."nextEpisodeAt" != '')
              )
            ORDER BY COALESCE(NULLIF(w."nextEpisodeAt", ''), w."startDate") ASC
            LIMIT ?
        """.trimIndent()

        val args = listOf(
            TursoArg.Text(windowStart),
            TursoArg.Text(windowEnd),
            TursoArg.Text(windowStart),
            TursoArg.Text(windowEnd),
            TursoArg.Integer(safeLimit.toLong())
        )
        return Pair(sql, args)
    }

    // 4. "Top popularidade": type = 'anime', ORDER BY popularity DESC.
    fun buildTopPopularityQuery(
        limit: Int = 20,
        useAuxGenreTable: Boolean = false
    ): Pair<String, List<TursoArg>> {
        val safeLimit = limit.coerceIn(1, 24)
        val safety = contentSafetyFilter("w", useAuxGenreTable)
        val sql = """
            SELECT $CARD_COLS
            FROM "Work" w
            WHERE $safety
              AND w."type" = 'anime'
            ORDER BY w."popularity" DESC
            LIMIT ?
        """.trimIndent()
        return Pair(sql, listOf(TursoArg.Integer(safeLimit.toLong())))
    }

    // 5. "Manhwas em alta": type = 'manhwa', ORDER BY trending DESC, popularity DESC.
    fun buildTrendingManhwaQuery(
        limit: Int = 20,
        useAuxGenreTable: Boolean = false
    ): Pair<String, List<TursoArg>> {
        val safeLimit = limit.coerceIn(1, 24)
        val safety = contentSafetyFilter("w", useAuxGenreTable)
        val sql = """
            SELECT $CARD_COLS
            FROM "Work" w
            WHERE $safety
              AND w."type" = 'manhwa'
            ORDER BY w."trending" DESC, w."popularity" DESC
            LIMIT ?
        """.trimIndent()
        return Pair(sql, listOf(TursoArg.Integer(safeLimit.toLong())))
    }

    // 6. "Mangás mais populares": type = 'manga', ORDER BY popularity DESC.
    fun buildPopularMangaQuery(
        limit: Int = 20,
        useAuxGenreTable: Boolean = false
    ): Pair<String, List<TursoArg>> {
        val safeLimit = limit.coerceIn(1, 24)
        val safety = contentSafetyFilter("w", useAuxGenreTable)
        val sql = """
            SELECT $CARD_COLS
            FROM "Work" w
            WHERE $safety
              AND w."type" = 'manga'
            ORDER BY w."popularity" DESC
            LIMIT ?
        """.trimIndent()
        return Pair(sql, listOf(TursoArg.Integer(safeLimit.toLong())))
    }

    // 7. "Melhores notas em mangás e manhwas": type IN ('manga','manhwa') AND popularity >= 20000, ORDER BY avgScore DESC.
    fun buildTopRatedMangaManhwaQuery(
        limit: Int = 20,
        useAuxGenreTable: Boolean = false
    ): Pair<String, List<TursoArg>> {
        val safeLimit = limit.coerceIn(1, 24)
        val safety = contentSafetyFilter("w", useAuxGenreTable)
        val sql = """
            SELECT $CARD_COLS
            FROM "Work" w
            WHERE $safety
              AND w."type" IN ('manga', 'manhwa')
              AND w."popularity" >= 20000
            ORDER BY w."avgScore" DESC
            LIMIT ?
        """.trimIndent()
        return Pair(sql, listOf(TursoArg.Integer(safeLimit.toLong())))
    }

    // 8. "Para maratonar": type IN ('manga','manhwa') AND status = 'FINISHED' AND avgScore >= 80, ORDER BY popularity DESC.
    fun buildBingeQuery(
        limit: Int = 20,
        useAuxGenreTable: Boolean = false
    ): Pair<String, List<TursoArg>> {
        val safeLimit = limit.coerceIn(1, 24)
        val safety = contentSafetyFilter("w", useAuxGenreTable)
        val sql = """
            SELECT $CARD_COLS
            FROM "Work" w
            WHERE $safety
              AND w."type" IN ('manga', 'manhwa')
              AND w."status" = 'FINISHED'
              AND w."avgScore" >= 80
            ORDER BY w."popularity" DESC
            LIMIT ?
        """.trimIndent()
        return Pair(sql, listOf(TursoArg.Integer(safeLimit.toLong())))
    }

    // 9. "Isekai e reencarnação": tags reais Isekai, Reincarnation; tipos manga, manhwa, manhua, lightnovel; ORDER BY popularity DESC.
    fun buildIsekaiReincarnationQuery(
        limit: Int = 20,
        useAuxGenreTable: Boolean = false,
        useAuxTagTable: Boolean = false
    ): Pair<String, List<TursoArg>> {
        val safeLimit = limit.coerceIn(1, 24)
        val safety = contentSafetyFilter("w", useAuxGenreTable)
        val tagCondition = if (useAuxTagTable) {
            """(w."id" IN (SELECT "workId" FROM "WorkTag" WHERE "name" IN ('Isekai', 'Reincarnation'))"""
        } else {
            """(EXISTS (SELECT 1 FROM json_each(w."tags") WHERE json_extract(value, '$.name') IN ('Isekai', 'Reincarnation'))"""
        }

        val sql = """
            SELECT $CARD_COLS
            FROM "Work" w
            WHERE $safety
              AND w."type" IN ('manga', 'manhwa', 'manhua', 'lightnovel')
              AND (
                $tagCondition
                OR w."genres" LIKE '%Isekai%'
                OR w."genres" LIKE '%Reincarnation%'
              )
            ORDER BY w."popularity" DESC
            LIMIT ?
        """.trimIndent()
        return Pair(sql, listOf(TursoArg.Integer(safeLimit.toLong())))
    }

    // 10. "Manhuas em destaque": type = 'manhua', ORDER BY popularity DESC.
    fun buildFeaturedManhuaQuery(
        limit: Int = 20,
        useAuxGenreTable: Boolean = false
    ): Pair<String, List<TursoArg>> {
        val safeLimit = limit.coerceIn(1, 24)
        val safety = contentSafetyFilter("w", useAuxGenreTable)
        val sql = """
            SELECT $CARD_COLS
            FROM "Work" w
            WHERE $safety
              AND w."type" = 'manhua'
            ORDER BY w."popularity" DESC
            LIMIT ?
        """.trimIndent()
        return Pair(sql, listOf(TursoArg.Integer(safeLimit.toLong())))
    }

    // ==========================================
    // ETAPA 2: ADVANCED EXPLORE & SEARCH
    // ==========================================

    data class ExploreFilterCriteria(
        val query: String = "",
        val types: List<String> = emptyList(), // e.g. ["Anime", "Manga"]
        val formats: List<String> = emptyList(), // TV, Movie, etc.
        val statuses: List<String> = emptyList(), // RELEASING, FINISHED, etc.
        val genres: List<String> = emptyList(),
        val genresModeAnd: Boolean = false, // true = AND, false = OR
        val excludedGenres: List<String> = emptyList(),
        val tags: List<String> = emptyList(),
        val yearMin: Int? = null,
        val yearMax: Int? = null,
        val minScore: Int? = null, // 0 to 100
        val episodeRange: String? = null, // "1-12", "13-24", "25-50", "50-100", "100+"
        val country: String? = null,
        val magazine: String? = null,
        val sortBy: String = "popularity", // popularity, avgScore, trending, startDate, lastUpdate, titleAsc, titleDesc
        val adultMode: AdultMode = AdultMode.OFF
    )

    private fun buildExploreWhereClause(
        criteria: ExploreFilterCriteria,
        useAuxGenreTable: Boolean,
        useAuxTagTable: Boolean
    ): Pair<String, List<TursoArg>> {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<TursoArg>()

        conditions.add(contentSafetyFilter("w", useAuxGenreTable, criteria.adultMode))

        // Search
        if (criteria.query.isNotBlank()) {
            val sanitized = sanitizeFtsQuery(criteria.query)
            if (sanitized.isNotBlank()) {
                conditions.add("""w."id" IN (SELECT workId FROM work_fts WHERE work_fts MATCH ?)""")
                args.add(TursoArg.Text(sanitized))
            } else {
                conditions.add("""(w."title" LIKE ? OR w."titleEn" LIKE ?)""")
                val param = "%${criteria.query.trim()}%"
                args.add(TursoArg.Text(param))
                args.add(TursoArg.Text(param))
            }
        }

        // Types (multi-select)
        val validTypes = criteria.types.filter { it.isNotBlank() && !it.equals("Todos", ignoreCase = true) }
        if (validTypes.isNotEmpty()) {
            val dbTypes = validTypes.map { mapTypeToDb(it) }
            val placeholders = dbTypes.joinToString(",") { "?" }
            conditions.add("""w."type" IN ($placeholders)""")
            dbTypes.forEach { args.add(TursoArg.Text(it)) }
        }

        // Formats (multi-select)
        if (criteria.formats.isNotEmpty()) {
            val placeholders = criteria.formats.joinToString(",") { "?" }
            conditions.add("""w."format" IN ($placeholders)""")
            criteria.formats.forEach { args.add(TursoArg.Text(it)) }
        }

        // Statuses (multi-select)
        if (criteria.statuses.isNotEmpty()) {
            val placeholders = criteria.statuses.joinToString(",") { "?" }
            conditions.add("""w."status" IN ($placeholders)""")
            criteria.statuses.forEach { args.add(TursoArg.Text(it)) }
        }

        // Included Genres (respecting AdultMode)
        val cleanGenres = if (criteria.adultMode == AdultMode.OFF) {
            criteria.genres.filter { it !in SENSITIVE_GENRES && it != "Todos" }
        } else {
            criteria.genres.filter { it != "Todos" }
        }
        if (cleanGenres.isNotEmpty()) {
            if (criteria.genresModeAnd) {
                // Must have ALL genres
                cleanGenres.forEach { g ->
                    if (useAuxGenreTable) {
                        conditions.add("""w."id" IN (SELECT "workId" FROM "WorkGenre" WHERE "genre" = ?)""")
                    } else {
                        conditions.add("""EXISTS (SELECT 1 FROM json_each(w."genres") WHERE value = ?)""")
                    }
                    args.add(TursoArg.Text(g))
                }
            } else {
                // Must have ANY genre
                if (useAuxGenreTable) {
                    val placeholders = cleanGenres.joinToString(",") { "?" }
                    conditions.add("""w."id" IN (SELECT "workId" FROM "WorkGenre" WHERE "genre" IN ($placeholders))""")
                    cleanGenres.forEach { args.add(TursoArg.Text(it)) }
                } else {
                    val orSubclauses = cleanGenres.map { "EXISTS (SELECT 1 FROM json_each(w.\"genres\") WHERE value = ?)" }
                    conditions.add("(${orSubclauses.joinToString(" OR ")})")
                    cleanGenres.forEach { args.add(TursoArg.Text(it)) }
                }
            }
        }

        // Excluded Genres
        if (criteria.excludedGenres.isNotEmpty()) {
            criteria.excludedGenres.forEach { g ->
                if (useAuxGenreTable) {
                    conditions.add("""w."id" NOT IN (SELECT "workId" FROM "WorkGenre" WHERE "genre" = ?)""")
                } else {
                    conditions.add("""NOT EXISTS (SELECT 1 FROM json_each(w."genres") WHERE value = ?)""")
                }
                args.add(TursoArg.Text(g))
            }
        }

        // Tags / Demographics
        if (criteria.tags.isNotEmpty()) {
            criteria.tags.forEach { tag ->
                if (useAuxTagTable) {
                    conditions.add("""w."id" IN (SELECT "workId" FROM "WorkTag" WHERE "name" = ?)""")
                } else {
                    conditions.add("""EXISTS (SELECT 1 FROM json_each(w."tags") WHERE json_extract(value, '$.name') = ?)""")
                }
                args.add(TursoArg.Text(tag))
            }
        }

        // Years Range
        if (criteria.yearMin != null) {
            conditions.add("""w."startDate" >= ?""")
            args.add(TursoArg.Text("${criteria.yearMin}-01-01"))
        }
        if (criteria.yearMax != null) {
            conditions.add("""w."startDate" <= ?""")
            args.add(TursoArg.Text("${criteria.yearMax}-12-31"))
        }

        // Min Score (0 to 100)
        if (criteria.minScore != null && criteria.minScore > 0) {
            conditions.add("""w."avgScore" >= ?""")
            args.add(TursoArg.Integer(criteria.minScore.toLong()))
        }

        // Episode / Chapter Range
        if (!criteria.episodeRange.isNullOrBlank()) {
            when (criteria.episodeRange) {
                "1-12" -> conditions.add("""(COALESCE(w."episodes", w."chapters") BETWEEN 1 AND 12)""")
                "13-24" -> conditions.add("""(COALESCE(w."episodes", w."chapters") BETWEEN 13 AND 24)""")
                "25-50" -> conditions.add("""(COALESCE(w."episodes", w."chapters") BETWEEN 25 AND 50)""")
                "50-100" -> conditions.add("""(COALESCE(w."episodes", w."chapters") BETWEEN 50 AND 100)""")
                "100+" -> conditions.add("""(COALESCE(w."episodes", w."chapters") >= 100)""")
            }
        }

        // Country
        if (!criteria.country.isNullOrBlank()) {
            conditions.add("""w."country" = ?""")
            args.add(TursoArg.Text(criteria.country.uppercase()))
        }

        // Magazine
        if (!criteria.magazine.isNullOrBlank()) {
            conditions.add("""w."magazine" LIKE ?""")
            args.add(TursoArg.Text("%${criteria.magazine.trim()}%"))
        }

        return Pair("WHERE " + conditions.joinToString(" AND "), args)
    }

    fun buildExploreQuery(
        criteria: ExploreFilterCriteria,
        limit: Int = 30,
        offset: Int = 0,
        useAuxGenreTable: Boolean = false,
        useAuxTagTable: Boolean = false
    ): Pair<String, List<TursoArg>> {
        val safeLimit = limit.coerceIn(1, 60)
        val (whereClause, whereArgs) = buildExploreWhereClause(criteria, useAuxGenreTable, useAuxTagTable)

        // NULLS LAST order clauses (using standard SQLite expressions: (col IS NULL), col DESC/ASC)
        val orderClause = when (criteria.sortBy) {
            "avgScore" -> """(w."avgScore" IS NULL), w."avgScore" DESC"""
            "trending" -> """(w."trending" IS NULL), w."trending" DESC"""
            "startDate" -> """(w."startDate" IS NULL), w."startDate" DESC"""
            "favourites" -> """(w."favourites" IS NULL), w."favourites" DESC"""
            "lastUpdate" -> """(w."lastUpdate" IS NULL), w."lastUpdate" DESC"""
            "titleAsc" -> """(w."title" IS NULL), w."title" ASC"""
            "titleDesc" -> """(w."title" IS NULL), w."title" DESC"""
            else -> """(w."popularity" IS NULL), w."popularity" DESC""" // popularity
        }

        val sql = """
            SELECT $CARD_COLS
            FROM "Work" w
            $whereClause
            ORDER BY $orderClause
            LIMIT ? OFFSET ?
        """.trimIndent()

        val args = whereArgs + listOf(TursoArg.Integer(safeLimit.toLong()), TursoArg.Integer(offset.toLong()))
        return Pair(sql, args)
    }

    fun buildExploreCountQuery(
        criteria: ExploreFilterCriteria,
        useAuxGenreTable: Boolean = false,
        useAuxTagTable: Boolean = false
    ): Pair<String, List<TursoArg>> {
        val (whereClause, whereArgs) = buildExploreWhereClause(criteria, useAuxGenreTable, useAuxTagTable)
        val sql = """
            SELECT COUNT(*) AS total
            FROM "Work" w
            $whereClause
        """.trimIndent()
        return Pair(sql, whereArgs)
    }

    // Schedule query: releasing works with nextEpisodeAt, up to 100 items
    fun buildScheduleQuery(
        limit: Int = 100,
        useAuxGenreTable: Boolean = false,
        adultMode: AdultMode = AdultMode.OFF
    ): Pair<String, List<TursoArg>> {
        val safeLimit = limit.coerceIn(1, 100)
        val safety = contentSafetyFilter("w", useAuxGenreTable, adultMode)
        val sql = """
            SELECT $CARD_COLS
            FROM "Work" w
            WHERE $safety
              AND w."status" = 'RELEASING'
              AND w."nextEpisodeAt" IS NOT NULL 
              AND w."nextEpisodeAt" != ''
            ORDER BY w."nextEpisodeAt" ASC
            LIMIT ?
        """.trimIndent()

        return Pair(sql, listOf(TursoArg.Integer(safeLimit.toLong())))
    }

    // Detail by ID
    fun buildDetailQuery(
        id: String,
        useAuxGenreTable: Boolean = false,
        adultMode: AdultMode = AdultMode.OFF
    ): Pair<String, List<TursoArg>> {
        val safety = contentSafetyFilter("w", useAuxGenreTable, adultMode)
        val sql = """
            SELECT *
            FROM "Work" w
            WHERE w."id" = ? AND $safety
            LIMIT 1
        """.trimIndent()
        return Pair(sql, listOf(TursoArg.Text(id)))
    }

    // Similar works
    fun buildSimilarWorksQuery(
        id: String,
        type: String,
        firstGenre: String,
        limit: Int = 10,
        useAuxGenreTable: Boolean = false,
        adultMode: AdultMode = AdultMode.OFF
    ): Pair<String, List<TursoArg>> {
        val safeLimit = limit.coerceIn(1, 24)
        val safety = contentSafetyFilter("w", useAuxGenreTable, adultMode)
        val genreCondition = if (useAuxGenreTable) {
            """w."id" IN (SELECT "workId" FROM "WorkGenre" WHERE "genre" = ?)"""
        } else {
            """EXISTS (SELECT 1 FROM json_each(w."genres") WHERE value = ?)"""
        }

        val sql = """
            SELECT $CARD_COLS
            FROM "Work" w
            WHERE $safety
              AND w."id" != ?
              AND w."type" = ?
              AND $genreCondition
            ORDER BY w."popularity" DESC
            LIMIT ?
        """.trimIndent()

        val args = listOf(
            TursoArg.Text(id),
            TursoArg.Text(type),
            TursoArg.Text(firstGenre),
            TursoArg.Integer(safeLimit.toLong())
        )
        return Pair(sql, args)
    }

    fun mapTypeToDb(type: String): String {
        return when (type.lowercase().replace(" ", "").replace("á", "a")) {
            "anime" -> "anime"
            "manga" -> "manga"
            "manhwa" -> "manhwa"
            "manhua" -> "manhua"
            "lightnovel", "novel" -> "lightnovel"
            "webnovel" -> "webnovel"
            else -> type.lowercase()
        }
    }
}
