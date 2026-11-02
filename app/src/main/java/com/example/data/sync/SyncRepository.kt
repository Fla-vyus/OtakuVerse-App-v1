package com.example.data.sync

import kotlinx.coroutines.flow.StateFlow

enum class SyncState {
    IDLE,
    SYNCING,
    SUCCESS,
    ERROR
}

data class UserProfileSync(
    val nickname: String = "Otaku",
    val bio: String = "",
    val avatarHash: String? = null,
    val bannerHash: String? = null,
    val favoriteWorkId: String? = null,
    val themeColorHex: String? = "#3B82F6",
    val adultMode: String = "off",
    val blurAdultCovers: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

interface SyncRepository {
    val syncState: StateFlow<SyncState>
    val lastSyncTime: StateFlow<Long>
    val syncError: StateFlow<String?>

    suspend fun syncNow(): Result<Unit>
    suspend fun uploadProfile(): Result<Unit>
    suspend fun downloadProfile(): Result<UserProfileSync?>
    suspend fun deleteRemoteUserData(uid: String): Result<Unit>
}
