package com.example

import com.example.data.model.AdultMode
import com.example.data.turso.TursoQueryBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultModeUnitTest {

    @Test
    fun testContentSafetyFilter_offMode_withAuxTable() {
        val filter = TursoQueryBuilder.contentSafetyFilter("w", useAuxGenreTable = true, adultMode = AdultMode.OFF)
        assertTrue("OFF mode with aux table must exclude isAdult", filter.contains("w.\"isAdult\" = 0"))
        assertTrue("OFF mode with aux table must check WorkGenre", filter.contains("FROM \"WorkGenre\" wg"))
        assertTrue("OFF mode with aux table must exclude Hentai", filter.contains("'Hentai'"))
        assertFalse("OFF mode must not have 1=1", filter.contains("1=1"))
    }

    @Test
    fun testContentSafetyFilter_offMode_fallbackJsonEach() {
        val filter = TursoQueryBuilder.contentSafetyFilter("w", useAuxGenreTable = false, adultMode = AdultMode.OFF)
        assertTrue("OFF mode fallback must exclude isAdult", filter.contains("w.\"isAdult\" = 0"))
        assertTrue("OFF mode fallback must check json_each", filter.contains("json_each(w.\"genres\")"))
        assertTrue("OFF mode fallback must exclude Erotica", filter.contains("'Erotica'"))
    }

    @Test
    fun testContentSafetyFilter_includeMode() {
        val filterAux = TursoQueryBuilder.contentSafetyFilter("w", useAuxGenreTable = true, adultMode = AdultMode.INCLUDE)
        assertEquals("1=1", filterAux)

        val filterFallback = TursoQueryBuilder.contentSafetyFilter("w", useAuxGenreTable = false, adultMode = AdultMode.INCLUDE)
        assertEquals("1=1", filterFallback)
    }

    @Test
    fun testContentSafetyFilter_onlyMode_withAuxTable() {
        val filter = TursoQueryBuilder.contentSafetyFilter("w", useAuxGenreTable = true, adultMode = AdultMode.ONLY)
        assertTrue("ONLY mode must require isAdult = 1 OR sensitive genre", filter.contains("w.\"isAdult\" = 1 OR EXISTS"))
        assertTrue("ONLY mode with aux table must check WorkGenre", filter.contains("FROM \"WorkGenre\" wg"))
        assertTrue("ONLY mode must include Smut", filter.contains("'Smut'"))
    }

    @Test
    fun testContentSafetyFilter_onlyMode_fallbackJsonEach() {
        val filter = TursoQueryBuilder.contentSafetyFilter("w", useAuxGenreTable = false, adultMode = AdultMode.ONLY)
        assertTrue("ONLY mode fallback must require isAdult = 1 OR sensitive genre", filter.contains("w.\"isAdult\" = 1 OR EXISTS"))
        assertTrue("ONLY mode fallback must check json_each", filter.contains("json_each(w.\"genres\")"))
    }

    @Test
    fun testExploreQueryBuilder_embedsAdultModeCorrectly() {
        val criteriaOff = TursoQueryBuilder.ExploreFilterCriteria(adultMode = AdultMode.OFF)
        val (sqlOff, _) = TursoQueryBuilder.buildExploreQuery(criteriaOff, useAuxGenreTable = true)
        assertTrue(sqlOff.contains("isAdult\" = 0"))

        val criteriaInclude = TursoQueryBuilder.ExploreFilterCriteria(adultMode = AdultMode.INCLUDE)
        val (sqlInclude, _) = TursoQueryBuilder.buildExploreQuery(criteriaInclude, useAuxGenreTable = true)
        assertTrue(sqlInclude.contains("1=1"))

        val criteriaOnly = TursoQueryBuilder.ExploreFilterCriteria(adultMode = AdultMode.ONLY)
        val (sqlOnly, _) = TursoQueryBuilder.buildExploreQuery(criteriaOnly, useAuxGenreTable = true)
        assertTrue(sqlOnly.contains("isAdult\" = 1 OR EXISTS"))
    }

    @Test
    fun testAgeConfirmationFlow_logic() {
        // Simulates the ViewModel age confirmation check
        var hasConfirmedAge = false
        var currentAdultMode = AdultMode.OFF

        fun requestActivateMode(targetMode: AdultMode, onNeedConfirmation: () -> Unit) {
            if (targetMode != AdultMode.OFF && !hasConfirmedAge) {
                onNeedConfirmation()
            } else {
                currentAdultMode = targetMode
            }
        }

        var confirmationPromptShown = false
        requestActivateMode(AdultMode.INCLUDE) {
            confirmationPromptShown = true
        }

        assertTrue("Confirmation prompt must be shown when age is unconfirmed", confirmationPromptShown)
        assertEquals("Adult mode must stay OFF before confirmation", AdultMode.OFF, currentAdultMode)

        // User confirms age
        hasConfirmedAge = true
        requestActivateMode(AdultMode.INCLUDE) {
            confirmationPromptShown = true
        }

        assertEquals("Adult mode should be set to INCLUDE after confirmation", AdultMode.INCLUDE, currentAdultMode)
    }
}
