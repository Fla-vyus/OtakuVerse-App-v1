package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("OtakuVerse", appName)
  }

  @Test
  fun `test MainActivity launches successfully with OtakuVerseApp`() {
    val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    org.junit.Assert.assertNotNull(activity)
    org.junit.Assert.assertTrue(activity.application is OtakuVerseApp)
  }

  @Test
  fun `test auth models default state`() {
    val user = com.example.data.auth.AuthUser(uid = "test_uid", email = "test@example.com")
    val state = com.example.data.auth.AuthState.LoggedIn(user)
    assertEquals(true, state.isUserLoggedIn)
    assertEquals("test_uid", state.currentUser?.uid)
  }

  @Test
  fun `test genre cache separation across adult modes`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.repository.UserPreferencesRepository(context)

    val safeGenres = listOf("Action", "Comedy", "Fantasy")
    val adultGenres = listOf("Action", "Comedy", "Fantasy", "Erotica", "Hentai")
    val onlyAdultGenres = listOf("Erotica", "Hentai", "Smut")

    repo.saveCachedGenres(safeGenres, com.example.data.model.AdultMode.OFF)
    repo.saveCachedGenres(adultGenres, com.example.data.model.AdultMode.INCLUDE)
    repo.saveCachedGenres(onlyAdultGenres, com.example.data.model.AdultMode.ONLY)

    val cachedOff = repo.getCachedGenres(com.example.data.model.AdultMode.OFF)
    val cachedInclude = repo.getCachedGenres(com.example.data.model.AdultMode.INCLUDE)
    val cachedOnly = repo.getCachedGenres(com.example.data.model.AdultMode.ONLY)

    assertEquals(safeGenres, cachedOff)
    assertEquals(adultGenres, cachedInclude)
    assertEquals(onlyAdultGenres, cachedOnly)
  }

  @Test
  fun `test age confirmation and adult mode preferences persistence`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.repository.UserPreferencesRepository(context)

    repo.setHasConfirmedAge(true)
    repo.setAdultMode(com.example.data.model.AdultMode.INCLUDE)
    repo.setBlurAdultCovers(false)

    val confirmed = repo.hasConfirmedAge.first()
    val mode = repo.adultMode.first()
    val blur = repo.blurAdultCovers.first()

    assertEquals(true, confirmed)
    assertEquals(com.example.data.model.AdultMode.INCLUDE, mode)
    assertEquals(false, blur)
  }

  @Test
  fun `test favoritar obra sem status aparece em Todos e Favoritos com contadores iguais a lista`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.local.LibraryDatabase.getDatabase(context)
    val dao = db.libraryDao()
    val repo = com.example.data.repository.LibraryRepository(dao)

    dao.clearAll()

    val card = com.example.data.model.WorkCard(
        id = "work_fav_only",
        title = "Obra Apenas Favorita",
        type = "manga",
        cover = "https://example.com/cover.jpg"
    )

    // Favoritar obra sem status (status = null, isFavorite = true)
    repo.toggleFavorite(card)

    val allWorks = repo.getWorksForTab(com.example.data.repository.LibraryTab.ALL).first()
    val favWorks = repo.getWorksForTab(com.example.data.repository.LibraryTab.FAVORITES).first()
    val tabCounts = repo.getTabCounts().first()

    assertEquals(1, allWorks.size)
    assertEquals(1, favWorks.size)
    assertEquals("work_fav_only", allWorks[0].id)
    assertEquals("work_fav_only", favWorks[0].id)
    assertEquals(null, allWorks[0].status)
    assertEquals(true, allWorks[0].isFavorite)

    // Contadores devem ser exatamente iguais ao tamanho das listas
    assertEquals(allWorks.size, tabCounts[com.example.data.repository.LibraryTab.ALL])
    assertEquals(favWorks.size, tabCounts[com.example.data.repository.LibraryTab.FAVORITES])
    assertEquals(0, tabCounts[com.example.data.repository.LibraryTab.PLANNING])
    assertEquals(0, tabCounts[com.example.data.repository.LibraryTab.READING])
    assertEquals(0, tabCounts[com.example.data.repository.LibraryTab.COMPLETED])
  }

  @Test
  fun `test formatCountdown agenda labels`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.repository.WorkRepository(
        tursoClient = com.example.data.turso.TursoClient(),
        workDao = com.example.data.local.CacheDatabase.getDatabase(context).workDao(),
        userPreferencesRepository = com.example.data.repository.UserPreferencesRepository(context)
    )

    // Agora: dentro de +-5 minutos
    assertEquals("Agora", repo.formatCountdown(0L))
    assertEquals("Agora", repo.formatCountdown(4 * 60 * 1000L))
    assertEquals("Agora", repo.formatCountdown(-4 * 60 * 1000L))

    // Passado: Lançado ou há Xh / há Xd
    assertEquals("Lançado", repo.formatCountdown(-15 * 60 * 1000L))
    assertEquals("há 2h", repo.formatCountdown(-2 * 60 * 60 * 1000L))
    assertEquals("há 1d", repo.formatCountdown(-25 * 60 * 60 * 1000L))

    // Futuro: em Xh Ym, em Xm, em Xd
    assertEquals("em 20m", repo.formatCountdown(20 * 60 * 1000L))
    assertEquals("em 2h 15m", repo.formatCountdown(2 * 60 * 60 * 1000L + 15 * 60 * 1000L))
    assertEquals("em 2h", repo.formatCountdown(2 * 60 * 60 * 1000L))
    assertEquals("em 2d", repo.formatCountdown(2 * 24 * 60 * 60 * 1000L))
  }

  @Test
  fun `test parseWorkCardsFromItemsJson tolerates missing fields`() {
    val tursoClient = com.example.data.turso.TursoClient()
    val rawJson = """
      [
        {"id": "w1", "title": "Work One", "cover": "https://example.com/1.jpg"},
        {"id": "w2", "titleEn": "Work Two English", "type": "manga", "cover": "https://example.com/2.jpg", "avgScore": 8.5, "genres": ["Action", "Fantasy"]},
        {"id": "w3", "titleNative": "Work Three Native", "cover": "https://example.com/c.jpg", "genres": "Romance, Comedy"},
        {"invalid": "no_id"}
      ]
    """.trimIndent()

    val parsed = tursoClient.parseWorkCardsFromItemsJson(rawJson)
    assertEquals(3, parsed.size)
    assertEquals("w1", parsed[0].id)
    assertEquals("Work One", parsed[0].title)
    assertEquals("anime", parsed[0].type)

    assertEquals("w2", parsed[1].id)
    assertEquals("Work Two English", parsed[1].title)
    assertEquals("manga", parsed[1].type)
    assertEquals(85, parsed[1].avgScore)
    assertEquals(listOf("Action", "Fantasy"), parsed[1].genres)

    assertEquals("w3", parsed[2].id)
    assertEquals("Work Three Native", parsed[2].title)
    assertEquals(listOf("Romance", "Comedy"), parsed[2].genres)
  }

  @Test
  fun `test title null or empty and missing cover are discarded`() {
    val tursoClient = com.example.data.turso.TursoClient()
    val rawJson = """
      [
        {"id": "d1", "title": "null", "titleEn": "null", "cover": "https://example.com/c1.jpg"},
        {"id": "d2", "title": "", "titleEn": "   ", "cover": "https://example.com/c2.jpg"},
        {"id": "d3", "title": "Valid Title", "cover": "null"},
        {"id": "d4", "title": "Valid Title 2"},
        {"id": "d5", "title": "null", "titleEn": "Attack on Titan", "cover": "https://example.com/aot.jpg", "banner": "null", "format": "null"},
        {"id": "d6", "title": "", "titleEn": "null", "titleNative": "Shingeki", "cover": "https://example.com/shingeki.jpg"}
      ]
    """.trimIndent()

    val parsed = tursoClient.parseWorkCardsFromItemsJson(rawJson)
    // d1 (title="null"), d2 (title=""), d3 (cover="null"), d4 (no cover) must be discarded!
    // Only d5 (titleEn fallback) and d6 (titleNative fallback) should survive.
    assertEquals(2, parsed.size)

    assertEquals("d5", parsed[0].id)
    assertEquals("Attack on Titan", parsed[0].title)
    assertEquals(null, parsed[0].banner) // "null" string sanitized to null
    assertEquals("", parsed[0].format) // "null" string sanitized to empty

    assertEquals("d6", parsed[1].id)
    assertEquals("Shingeki", parsed[1].title)
  }

  @Test
  fun `test dedupe extra sections against fixed sections and previous extra sections`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.repository.WorkRepository(
        tursoClient = com.example.data.turso.TursoClient(),
        workDao = com.example.data.local.CacheDatabase.getDatabase(context).workDao(),
        userPreferencesRepository = com.example.data.repository.UserPreferencesRepository(context)
    )

    // Fixed sections with IDs: fixed_1, fixed_2
    val fixedSectionItems = listOf(
        listOf(
            com.example.data.model.WorkCard(id = "fixed_1", title = "Fixed 1", cover = "https://example.com/1.jpg"),
            com.example.data.model.WorkCard(id = "fixed_2", title = "Fixed 2", cover = "https://example.com/2.jpg")
        )
    )

    // Section 1 has 8 items: 2 duplicate with fixed, 6 unique (sec1_1 .. sec1_6)
    val sec1Items = listOf(
        com.example.data.model.WorkCard(id = "fixed_1", title = "Dup 1", cover = "https://example.com/c.jpg"),
        com.example.data.model.WorkCard(id = "fixed_2", title = "Dup 2", cover = "https://example.com/c.jpg"),
        com.example.data.model.WorkCard(id = "sec1_1", title = "S1-1", cover = "https://example.com/c.jpg"),
        com.example.data.model.WorkCard(id = "sec1_2", title = "S1-2", cover = "https://example.com/c.jpg"),
        com.example.data.model.WorkCard(id = "sec1_3", title = "S1-3", cover = "https://example.com/c.jpg"),
        com.example.data.model.WorkCard(id = "sec1_4", title = "S1-4", cover = "https://example.com/c.jpg"),
        com.example.data.model.WorkCard(id = "sec1_5", title = "S1-5", cover = "https://example.com/c.jpg"),
        com.example.data.model.WorkCard(id = "sec1_6", title = "S1-6", cover = "https://example.com/c.jpg")
    )
    val sec1 = com.example.data.model.ExtraHomeSection(
        key = "sec_1",
        title = "Section 1",
        position = 1,
        items = sec1Items
    )

    // Section 2 has 7 items: 2 duplicate with Section 1 (sec1_1, sec1_2), 5 unique (sec2_1 .. sec2_5)
    // After dedupe, Section 2 has only 5 items -> MUST BE HIDDEN (fewer than 6 items)!
    val sec2Items = listOf(
        com.example.data.model.WorkCard(id = "sec1_1", title = "Dup S1-1", cover = "https://example.com/c.jpg"),
        com.example.data.model.WorkCard(id = "sec1_2", title = "Dup S1-2", cover = "https://example.com/c.jpg"),
        com.example.data.model.WorkCard(id = "sec2_1", title = "S2-1", cover = "https://example.com/c.jpg"),
        com.example.data.model.WorkCard(id = "sec2_2", title = "S2-2", cover = "https://example.com/c.jpg"),
        com.example.data.model.WorkCard(id = "sec2_3", title = "S2-3", cover = "https://example.com/c.jpg"),
        com.example.data.model.WorkCard(id = "sec2_4", title = "S2-4", cover = "https://example.com/c.jpg"),
        com.example.data.model.WorkCard(id = "sec2_5", title = "S2-5", cover = "https://example.com/c.jpg")
    )
    val sec2 = com.example.data.model.ExtraHomeSection(
        key = "sec_2",
        title = "Section 2",
        position = 2,
        items = sec2Items
    )

    // Section 3 has 6 unique items -> MUST BE KEPT
    val sec3Items = (1..6).map { i ->
        com.example.data.model.WorkCard(id = "sec3_$i", title = "S3-$i", cover = "https://example.com/c.jpg")
    }
    val sec3 = com.example.data.model.ExtraHomeSection(
        key = "sec_3",
        title = "Section 3",
        position = 3,
        items = sec3Items
    )

    val deduped = repo.filterAndDeduplicateExtraSections(listOf(sec2, sec1, sec3), fixedSectionItems)

    // Section 1 kept (6 items), Section 2 omitted (< 6 items), Section 3 kept (6 items)
    assertEquals(2, deduped.size)
    assertEquals("sec_1", deduped[0].key)
    assertEquals(6, deduped[0].items.size)
    assertEquals("sec_3", deduped[1].key)
    assertEquals(6, deduped[1].items.size)
    assertEquals(false, deduped.any { it.key == "sec_2" })
  }

  @Test
  fun `test extra sections hidden when less than 6 items and capped at 12`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.repository.WorkRepository(
        tursoClient = com.example.data.turso.TursoClient(),
        workDao = com.example.data.local.CacheDatabase.getDatabase(context).workDao(),
        userPreferencesRepository = com.example.data.repository.UserPreferencesRepository(context)
    )

    // Build 15 sections: sec 0 has 5 items (should be hidden), secs 1..14 have 6 items each
    val jsonArr = org.json.JSONArray()
    for (s in 0 until 15) {
      val itemCount = if (s == 0) 5 else 6
      val itemsArr = org.json.JSONArray()
      for (i in 0 until itemCount) {
        itemsArr.put(org.json.JSONObject().apply {
          put("id", "w_${s}_$i")
          put("title", "Work $i")
          put("cover", "https://example.com/c.jpg")
        })
      }
      val secObj = org.json.JSONObject().apply {
        put("key", "sec_$s")
        put("title", "Section $s")
        put("position", s)
        put("itemsJson", itemsArr.toString())
      }
      jsonArr.put(secObj)
    }

    val parsed = repo.parseExtraSectionsJson(jsonArr.toString())

    // Section 0 had only 5 items -> filtered out
    assertEquals(false, parsed.any { it.key == "sec_0" })
    // Remaining valid sections: 14 -> capped at 12 maximum
    assertEquals(12, parsed.size)
    assertEquals("sec_1", parsed[0].key)
    assertEquals(6, parsed[0].items.size)
  }

  @Test
  fun `test cacheDir saving and loading extra sections with v2 and v1 deletion`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.repository.WorkRepository(
        tursoClient = com.example.data.turso.TursoClient(),
        workDao = com.example.data.local.CacheDatabase.getDatabase(context).workDao(),
        userPreferencesRepository = com.example.data.repository.UserPreferencesRepository(context)
    )

    val oldCacheFile = java.io.File(context.cacheDir, "home_extra_sections_cache.json")
    val v2CacheFile = java.io.File(context.cacheDir, "home_extra_sections_cache_v2.json")

    // Create old v1 cache file with obsolete sections
    oldCacheFile.writeText("""[{"key":"classics","title":"Clássicos"}]""")
    if (v2CacheFile.exists()) v2CacheFile.delete()

    // 1. Calling getCachedExtraSections deletes oldCacheFile
    val initialCached = repo.getCachedExtraSections()
    assertEquals(0, initialCached.size)
    assertEquals(false, oldCacheFile.exists())

    // 2. Write valid v2 cache file
    val testJson = """
      [
        {
          "key": "custom_award_winners",
          "title": "Vencedores de Prêmios",
          "position": 1,
          "itemsJson": "[{\"id\":\"a1\",\"title\":\"A1\",\"cover\":\"https://example.com/1.jpg\"},{\"id\":\"a2\",\"title\":\"A2\",\"cover\":\"https://example.com/2.jpg\"},{\"id\":\"a3\",\"title\":\"A3\",\"cover\":\"https://example.com/3.jpg\"},{\"id\":\"a4\",\"title\":\"A4\",\"cover\":\"https://example.com/4.jpg\"},{\"id\":\"a5\",\"title\":\"A5\",\"cover\":\"https://example.com/5.jpg\"},{\"id\":\"a6\",\"title\":\"A6\",\"cover\":\"https://example.com/6.jpg\"}]"
        }
      ]
    """.trimIndent()
    v2CacheFile.writeText(testJson)

    // Recreate old file to make sure getCachedExtraSections deletes it again
    oldCacheFile.writeText("obsolete")
    val loadedCached = repo.getCachedExtraSections()
    assertEquals(1, loadedCached.size)
    assertEquals("custom_award_winners", loadedCached[0].key)
    assertEquals("Vencedores de Prêmios", loadedCached[0].title)
    assertEquals(6, loadedCached[0].items.size)
    assertEquals(false, oldCacheFile.exists())
  }

  @Test
  fun `test export and import library json`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.local.LibraryDatabase.getDatabase(context)
    val dao = db.libraryDao()
    val repo = com.example.data.repository.LibraryRepository(dao)

    dao.clearAll()
    val card1 = com.example.data.model.WorkCard(
        id = "anime_export_1",
        title = "Sousou no Frieren",
        type = "anime",
        cover = "https://example.com/frieren.jpg"
    )
    val card2 = com.example.data.model.WorkCard(
        id = "manga_export_2",
        title = "Berserk",
        type = "manga",
        cover = "https://example.com/berserk.jpg"
    )

    repo.saveOrUpdateCard(card1, status = "READING", isFavorite = true)
    repo.saveOrUpdateCard(card2, status = "COMPLETED", isFavorite = false)

    val exportedJson = repo.exportToJson()
    assertEquals(true, exportedJson.contains("Sousou no Frieren"))
    assertEquals(true, exportedJson.contains("Berserk"))

    // Clear local db
    dao.clearAll()
    assertEquals(0, dao.getAllSnapshot().size)

    // Import from json
    val importResult = repo.importFromJson(exportedJson)
    assertEquals(true, importResult.isSuccess)
    assertEquals(2, importResult.getOrNull())

    val restoredWorks = dao.getAllSnapshot()
    assertEquals(2, restoredWorks.size)
    val frieren = restoredWorks.firstOrNull { it.id == "anime_export_1" }
    val berserk = restoredWorks.firstOrNull { it.id == "manga_export_2" }
    assertEquals("Sousou no Frieren", frieren?.title)
    assertEquals("READING", frieren?.status)
    assertEquals(true, frieren?.isFavorite)
    assertEquals("Berserk", berserk?.title)
    assertEquals("COMPLETED", berserk?.status)
    assertEquals(false, berserk?.isFavorite)
  }
}


