package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AdultMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest

private val Context.dataStore by preferencesDataStore(name = "otakuverse_prefs")

enum class ThemeOption(val id: String, val title: String, val isDark: Boolean) {
    FOLLOW_SYSTEM("follow_system", "Seguir o sistema", false),
    NOITE_AZUL("noite_azul", "Noite azul", true),
    AZUL_BRANCO("azul_branco", "Azul-branco", false),
    SAKURA("sakura", "Sakura", true),
    ESMERALDA("esmeralda", "Esmeralda", true),
    MEIA_NOITE_VIOLETA("meia_noite_violeta", "Meia-noite violeta", true),
    CAFE_CREME("cafe_creme", "Café & creme", false),
    NEON_CYBER("neon_cyber", "Neon cyber", true);

    companion object {
        fun fromId(id: String?): ThemeOption {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NOITE_AZUL
        }
    }
}

class UserPreferencesRepository(val context: Context) {

    private object Keys {
        val APP_THEME = stringPreferencesKey("app_theme")
        val USER_NICKNAME = stringPreferencesKey("user_nickname")
        val AVATAR_COLOR_HEX = stringPreferencesKey("avatar_color_hex")
        val TURSO_CUSTOM_TOKEN = stringPreferencesKey("turso_custom_token")

        // Preferences (D3)
        val PREFERRED_TITLE_LANG = stringPreferencesKey("preferred_title_lang") // "english", "romaji", "native"
        val PREFER_SYNOPSIS_PT = booleanPreferencesKey("prefer_synopsis_pt")
        val GRID_COLUMNS = intPreferencesKey("grid_columns") // 2 or 3

        // Profile customization (Etapa 5)
        val USER_BIO = stringPreferencesKey("user_bio")
        val AVATAR_IMAGE_PATH = stringPreferencesKey("avatar_image_path")
        val BANNER_IMAGE_PATH = stringPreferencesKey("banner_image_path")
        val FAVORITE_WORK_ID = stringPreferencesKey("favorite_work_id")
        val PROFILE_ACCENT_COLOR = stringPreferencesKey("profile_accent_color")

        // Display & Content
        val FONT_SIZE_SCALE = stringPreferencesKey("font_size_scale") // "small", "normal", "large"
        val HIDE_LIBRARY_IN_EXPLORE = booleanPreferencesKey("hide_library_in_explore")

        // Notifications (WorkManager)
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val NOTIFICATIONS_QUIET_START = intPreferencesKey("notifications_quiet_start") // default 22 (22:00)
        val NOTIFICATIONS_QUIET_END = intPreferencesKey("notifications_quiet_end") // default 8 (08:00)

        // Sync
        val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")

        // Adult Mode (B2)
        val ADULT_MODE = stringPreferencesKey("adult_mode")
        val HAS_CONFIRMED_AGE = booleanPreferencesKey("has_confirmed_age")
        val BLUR_ADULT_COVERS = booleanPreferencesKey("blur_adult_covers")

        // Avatar & Banner hashes (A4)
        val AVATAR_FILE_HASH = stringPreferencesKey("avatar_file_hash")
        val BANNER_FILE_HASH = stringPreferencesKey("banner_file_hash")

        // Genre cache (A7 - 7 days TTL)
        val GENRES_CACHE_JSON = stringPreferencesKey("genres_cache_json")
        val GENRES_CACHED_AT = longPreferencesKey("genres_cached_at")

        // Diagnostic logs (D5)
        val LAST_ERROR_MSG = stringPreferencesKey("last_error_msg")
        val LAST_QUERY_TIME_MS = longPreferencesKey("last_query_time_ms")
        val LAST_NEXT_EPISODE_SAMPLE = stringPreferencesKey("last_next_episode_sample")
    }

    val selectedTheme: Flow<ThemeOption> = context.dataStore.data.map { prefs ->
        ThemeOption.fromId(prefs[Keys.APP_THEME])
    }

    val userNickname: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.USER_NICKNAME] ?: "Otaku"
    }

    val userBio: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.USER_BIO] ?: ""
    }

    val avatarColorHex: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.AVATAR_COLOR_HEX] ?: "#3B82F6"
    }

    val avatarImagePath: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.AVATAR_IMAGE_PATH]
    }

    val bannerImagePath: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.BANNER_IMAGE_PATH]
    }

    val favoriteWorkId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.FAVORITE_WORK_ID]
    }

    val profileAccentColor: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.PROFILE_ACCENT_COLOR] ?: "#3B82F6"
    }

    val fontSizeScale: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.FONT_SIZE_SCALE] ?: "normal"
    }

    val hideLibraryInExplore: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.HIDE_LIBRARY_IN_EXPLORE] ?: false
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.NOTIFICATIONS_ENABLED] ?: false
    }

    val quietHours: Flow<Pair<Int, Int>> = context.dataStore.data.map { prefs ->
        Pair(
            prefs[Keys.NOTIFICATIONS_QUIET_START] ?: 22,
            prefs[Keys.NOTIFICATIONS_QUIET_END] ?: 8
        )
    }

    val lastSyncTimestamp: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_SYNC_TIMESTAMP] ?: 0L
    }

    val adultMode: Flow<AdultMode> = context.dataStore.data.map { prefs ->
        AdultMode.fromId(prefs[Keys.ADULT_MODE])
    }

    val hasConfirmedAge: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.HAS_CONFIRMED_AGE] ?: false
    }

    val blurAdultCovers: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.BLUR_ADULT_COVERS] ?: true
    }

    val avatarFileHash: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.AVATAR_FILE_HASH]
    }

    val bannerFileHash: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.BANNER_FILE_HASH]
    }

    val customToken: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.TURSO_CUSTOM_TOKEN] ?: ""
    }

    val preferredTitleLang: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.PREFERRED_TITLE_LANG] ?: "english"
    }

    val preferSynopsisPt: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.PREFER_SYNOPSIS_PT] ?: true
    }

    val gridColumns: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.GRID_COLUMNS] ?: 3
    }

    val diagnosticInfo: Flow<Triple<String?, Long, String?>> = context.dataStore.data.map { prefs ->
        Triple(
            prefs[Keys.LAST_ERROR_MSG],
            prefs[Keys.LAST_QUERY_TIME_MS] ?: 0L,
            prefs[Keys.LAST_NEXT_EPISODE_SAMPLE]
        )
    }

    suspend fun setTheme(theme: ThemeOption) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APP_THEME] = theme.id
        }
    }

    suspend fun setBio(bio: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_BIO] = bio.trim().take(120)
        }
    }

    suspend fun setAvatarImagePath(path: String?) {
        context.dataStore.edit { prefs ->
            if (path == null) prefs.remove(Keys.AVATAR_IMAGE_PATH) else prefs[Keys.AVATAR_IMAGE_PATH] = path
        }
    }

    suspend fun setBannerImagePath(path: String?) {
        context.dataStore.edit { prefs ->
            if (path == null) prefs.remove(Keys.BANNER_IMAGE_PATH) else prefs[Keys.BANNER_IMAGE_PATH] = path
        }
    }

    suspend fun setFavoriteWorkId(workId: String?) {
        context.dataStore.edit { prefs ->
            if (workId == null) prefs.remove(Keys.FAVORITE_WORK_ID) else prefs[Keys.FAVORITE_WORK_ID] = workId
        }
    }

    suspend fun setProfileAccentColor(hex: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PROFILE_ACCENT_COLOR] = hex
        }
    }

    suspend fun setFontSizeScale(scale: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FONT_SIZE_SCALE] = scale
        }
    }

    suspend fun setHideLibraryInExplore(hide: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HIDE_LIBRARY_IN_EXPLORE] = hide
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setQuietHours(startHour: Int, endHour: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_QUIET_START] = startHour.coerceIn(0, 23)
            prefs[Keys.NOTIFICATIONS_QUIET_END] = endHour.coerceIn(0, 23)
        }
    }

    suspend fun setLastSyncTimestamp(ts: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_TIMESTAMP] = ts
        }
    }

    suspend fun resetAllPreferences() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    suspend fun setNickname(nickname: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_NICKNAME] = nickname.trim().take(20)
        }
    }

    suspend fun setAvatarColorHex(colorHex: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AVATAR_COLOR_HEX] = colorHex
        }
    }

    suspend fun setCustomToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TURSO_CUSTOM_TOKEN] = token.trim()
        }
    }

    suspend fun setPreferredTitleLang(lang: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PREFERRED_TITLE_LANG] = lang
        }
    }

    suspend fun setPreferSynopsisPt(prefer: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PREFER_SYNOPSIS_PT] = prefer
        }
    }

    suspend fun setGridColumns(cols: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GRID_COLUMNS] = cols.coerceIn(2, 4)
        }
    }

    suspend fun recordDiagnostic(error: String?, queryTimeMs: Long, sampleEpisode: String? = null) {
        context.dataStore.edit { prefs ->
            if (error != null) prefs[Keys.LAST_ERROR_MSG] = error
            if (queryTimeMs > 0) prefs[Keys.LAST_QUERY_TIME_MS] = queryTimeMs
            if (sampleEpisode != null) prefs[Keys.LAST_NEXT_EPISODE_SAMPLE] = sampleEpisode
        }
    }

    suspend fun setAdultMode(mode: AdultMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ADULT_MODE] = mode.id
        }
    }

    suspend fun setHasConfirmedAge(confirmed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAS_CONFIRMED_AGE] = confirmed
        }
    }

    suspend fun setBlurAdultCovers(blur: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BLUR_ADULT_COVERS] = blur
        }
    }

    suspend fun setAvatarFileHash(hash: String?) {
        context.dataStore.edit { prefs ->
            if (hash == null) prefs.remove(Keys.AVATAR_FILE_HASH) else prefs[Keys.AVATAR_FILE_HASH] = hash
        }
    }

    suspend fun setBannerFileHash(hash: String?) {
        context.dataStore.edit { prefs ->
            if (hash == null) prefs.remove(Keys.BANNER_FILE_HASH) else prefs[Keys.BANNER_FILE_HASH] = hash
        }
    }

    fun computeFileSha256(file: File): String? {
        if (!file.exists()) return null
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    md.update(buffer, 0, bytesRead)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getCachedGenres(mode: AdultMode = AdultMode.OFF): List<String>? {
        val prefs = context.dataStore.data.first()
        val cachedAtKey = longPreferencesKey("genres_cached_at_v2_${mode.id}")
        val cacheJsonKey = stringPreferencesKey("genres_cache_json_v2_${mode.id}")
        val cachedAt = prefs[cachedAtKey] ?: 0L
        val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
        if (System.currentTimeMillis() - cachedAt > sevenDaysMs) return null

        val jsonStr = prefs[cacheJsonKey] ?: return null
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            list.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveCachedGenres(genres: List<String>, mode: AdultMode = AdultMode.OFF) {
        context.dataStore.edit { prefs ->
            val arr = JSONArray(genres)
            val cachedAtKey = longPreferencesKey("genres_cached_at_v2_${mode.id}")
            val cacheJsonKey = stringPreferencesKey("genres_cache_json_v2_${mode.id}")
            prefs[cacheJsonKey] = arr.toString()
            prefs[cachedAtKey] = System.currentTimeMillis()
        }
    }
}
