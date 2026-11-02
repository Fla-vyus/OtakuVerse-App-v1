package com.example.data.sync

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.auth.AuthRepository
import com.example.data.local.LibraryDao
import com.example.data.local.LibraryEntity
import com.example.data.model.AdultMode
import com.example.data.repository.UserPreferencesRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

class FirestoreSyncRepository(
    private val context: Context,
    private val authRepository: AuthRepository,
    private val libraryDao: LibraryDao,
    private val preferencesRepository: UserPreferencesRepository
) : SyncRepository {

    companion object {
        private const val TAG = "FirestoreSyncRepo"
    }

    private val _syncState = MutableStateFlow(SyncState.IDLE)
    override val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(0L)
    override val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    override val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val isFirebaseInitialized: Boolean
        get() = try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }

    private val firestore: FirebaseFirestore?
        get() = if (isFirebaseInitialized) {
            try {
                val dbId = try {
                    val resId = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
                    if (resId != 0) context.getString(resId) else null
                } catch (_: Exception) { null }

                if (!dbId.isNullOrBlank()) {
                    FirebaseFirestore.getInstance(FirebaseApp.getInstance(), dbId)
                } else {
                    FirebaseFirestore.getInstance()
                }
            } catch (e: Exception) {
                Log.w(TAG, "FirebaseFirestore instance error: ${e.message}")
                null
            }
        } else null

    override suspend fun syncNow(): Result<Unit> = withContext(Dispatchers.IO) {
        val user = authRepository.currentUser
        if (user == null) {
            _syncState.value = SyncState.IDLE
            return@withContext Result.success(Unit)
        }

        val db = firestore
        if (db == null) {
            // Firebase backend not yet active/configured: graceful fallback without fake simulation
            _syncState.value = SyncState.IDLE
            return@withContext Result.success(Unit)
        }

        _syncState.value = SyncState.SYNCING
        _syncError.value = null

        try {
            val uid = user.uid
            val libraryCollection = db.collection("users").document(uid).collection("library")

            // 1. Fetch remote library
            val remoteDocs = libraryCollection.get().await().documents
            val remoteMap = mutableMapOf<String, Map<String, Any?>>()
            for (doc in remoteDocs) {
                remoteMap[doc.id] = doc.data ?: emptyMap()
            }

            // 2. Fetch local library (including deleted tombstones)
            val localItems = libraryDao.getAllSnapshotIncludingDeleted()
            val localMap = localItems.associateBy { it.id }

            // 3. Reconcile Local -> Remote & Conflict Resolution by updatedAt
            for (local in localItems) {
                val remoteData = remoteMap[local.id]
                if (remoteData != null) {
                    val remoteUpdatedAt = (remoteData["updatedAt"] as? Number)?.toLong() ?: 0L
                    if (remoteUpdatedAt > local.updatedAt) {
                        val remoteDeleted = (remoteData["isDeleted"] as? Boolean)
                            ?: (remoteData["deleted"] as? Boolean)
                            ?: false

                        val updatedEntity = LibraryEntity(
                            id = local.id,
                            status = (remoteData["status"] as? String)?.takeIf { it != "null" },
                            isFavorite = remoteData["isFavorite"] as? Boolean ?: false,
                            currentProgress = (remoteData["currentProgress"] as? Number)?.toInt() ?: 0,
                            totalProgress = (remoteData["totalProgress"] as? Number)?.toInt(),
                            userRating = (remoteData["userRating"] as? Number)?.toInt(),
                            addedAt = (remoteData["addedAt"] as? Number)?.toLong() ?: local.addedAt,
                            updatedAt = remoteUpdatedAt,
                            workType = (remoteData["workType"] as? String) ?: local.workType,
                            format = (remoteData["format"] as? String) ?: local.format,
                            title = (remoteData["title"] as? String) ?: local.title,
                            titleEn = remoteData["titleEn"] as? String,
                            cover = (remoteData["cover"] as? String) ?: local.cover,
                            coverColor = remoteData["coverColor"] as? String,
                            avgScore = (remoteData["avgScore"] as? Number)?.toInt(),
                            workStatus = (remoteData["workStatus"] as? String) ?: local.workStatus,
                            genresJson = (remoteData["genresJson"] as? String) ?: local.genresJson,
                            isDeleted = remoteDeleted,
                            duration = (remoteData["duration"] as? Number)?.toInt() ?: local.duration,
                            nextEpisodeAt = remoteData["nextEpisodeAt"] as? String ?: local.nextEpisodeAt
                        )
                        libraryDao.insertOrUpdate(updatedEntity)
                    } else if (local.updatedAt > remoteUpdatedAt) {
                        libraryCollection.document(local.id).set(local.toFirestoreMap()).await()
                    }
                } else {
                    libraryCollection.document(local.id).set(local.toFirestoreMap()).await()
                }
            }

            // 4. Remote items that do not exist locally: insert into Room
            for ((remoteId, remoteData) in remoteMap) {
                if (!localMap.containsKey(remoteId)) {
                    val remoteDeleted = (remoteData["isDeleted"] as? Boolean)
                        ?: (remoteData["deleted"] as? Boolean)
                        ?: false
                    if (!remoteDeleted) {
                        val newEntity = LibraryEntity(
                            id = remoteId,
                            status = (remoteData["status"] as? String)?.takeIf { it != "null" },
                            isFavorite = remoteData["isFavorite"] as? Boolean ?: false,
                            currentProgress = (remoteData["currentProgress"] as? Number)?.toInt() ?: 0,
                            totalProgress = (remoteData["totalProgress"] as? Number)?.toInt(),
                            userRating = (remoteData["userRating"] as? Number)?.toInt(),
                            addedAt = (remoteData["addedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                            updatedAt = (remoteData["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                            workType = (remoteData["workType"] as? String) ?: "anime",
                            format = (remoteData["format"] as? String) ?: "",
                            title = (remoteData["title"] as? String) ?: "",
                            titleEn = remoteData["titleEn"] as? String,
                            cover = (remoteData["cover"] as? String) ?: "",
                            coverColor = remoteData["coverColor"] as? String,
                            avgScore = (remoteData["avgScore"] as? Number)?.toInt(),
                            workStatus = (remoteData["workStatus"] as? String) ?: "",
                            genresJson = (remoteData["genresJson"] as? String) ?: "",
                            isDeleted = false,
                            duration = (remoteData["duration"] as? Number)?.toInt(),
                            nextEpisodeAt = remoteData["nextEpisodeAt"] as? String
                        )
                        libraryDao.insertOrUpdate(newEntity)
                    }
                }
            }

            // 5. Synchronize Profile (A4 & B2)
            syncProfileWithFirestore(uid, db)

            val now = System.currentTimeMillis()
            _lastSyncTime.value = now
            preferencesRepository.setLastSyncTimestamp(now)
            _syncState.value = SyncState.SUCCESS
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Erro na sincronização: ${e.message}")
            _syncState.value = SyncState.ERROR
            _syncError.value = e.localizedMessage ?: "Erro de conexão ao sincronizar"
            Result.failure(e)
        }
    }

    private suspend fun syncProfileWithFirestore(uid: String, db: FirebaseFirestore) {
        val userDoc = db.collection("users").document(uid)
        val profileMainRef = userDoc.collection("profile").document("main")
        val avatarDocRef = userDoc.collection("profile").document("avatar")
        val bannerDocRef = userDoc.collection("profile").document("banner")

        val remoteProfileSnap = profileMainRef.get().await()

        val localNickname = preferencesRepository.userNickname.first()
        val localBio = preferencesRepository.userBio.first()
        val localFavWork = preferencesRepository.favoriteWorkId.first()
        val localAccent = preferencesRepository.profileAccentColor.first()
        val localAdultMode = preferencesRepository.adultMode.first()
        val localBlurAdultCovers = preferencesRepository.blurAdultCovers.first()

        val avatarFile = File(context.filesDir, "avatar.webp")
        val localAvatarHash = preferencesRepository.computeFileSha256(avatarFile)
        val storedAvatarHash = preferencesRepository.avatarFileHash.first()

        val bannerFile = File(context.filesDir, "banner.jpg")
        val localBannerHash = preferencesRepository.computeFileSha256(bannerFile)
        val storedBannerHash = preferencesRepository.bannerFileHash.first()

        var remoteAvatarHash: String? = null
        var remoteBannerHash: String? = null

        if (remoteProfileSnap.exists()) {
            val remoteData = remoteProfileSnap.data ?: emptyMap()
            val remoteUpdated = (remoteData["updatedAt"] as? Number)?.toLong() ?: 0L
            val localLastSync = preferencesRepository.lastSyncTimestamp.first()

            // Migration support: check if legacy base64 exists in profile/main
            val legacyAvatarB64 = remoteData["avatarBase64"] as? String
            val legacyBannerB64 = remoteData["bannerBase64"] as? String

            if (!legacyAvatarB64.isNullOrBlank()) {
                val migHash = localAvatarHash ?: "migrated_avatar"
                avatarDocRef.set(mapOf("base64" to legacyAvatarB64, "hash" to migHash, "updatedAt" to remoteUpdated)).await()
                profileMainRef.update(mapOf("avatarHash" to migHash, "avatarBase64" to com.google.firebase.firestore.FieldValue.delete())).await()
                remoteAvatarHash = migHash
            } else {
                remoteAvatarHash = remoteData["avatarHash"] as? String
            }

            if (!legacyBannerB64.isNullOrBlank()) {
                val migHash = localBannerHash ?: "migrated_banner"
                bannerDocRef.set(mapOf("base64" to legacyBannerB64, "hash" to migHash, "updatedAt" to remoteUpdated)).await()
                profileMainRef.update(mapOf("bannerHash" to migHash, "bannerBase64" to com.google.firebase.firestore.FieldValue.delete())).await()
                remoteBannerHash = migHash
            } else {
                remoteBannerHash = remoteData["bannerHash"] as? String
            }

            if (remoteUpdated > localLastSync) {
                // Remote text/preferences are newer: update local
                val rNick = remoteData["nickname"] as? String
                if (!rNick.isNullOrBlank()) preferencesRepository.setNickname(rNick)

                val rBio = remoteData["bio"] as? String
                if (rBio != null) preferencesRepository.setBio(rBio)

                val rFav = remoteData["favoriteWorkId"] as? String
                preferencesRepository.setFavoriteWorkId(rFav)

                val rAccent = remoteData["themeColorHex"] as? String
                if (!rAccent.isNullOrBlank()) preferencesRepository.setProfileAccentColor(rAccent)

                val rAdult = remoteData["adultMode"] as? String
                if (!rAdult.isNullOrBlank()) preferencesRepository.setAdultMode(AdultMode.fromId(rAdult))

                val rBlur = remoteData["blurAdultCovers"] as? Boolean
                if (rBlur != null) preferencesRepository.setBlurAdultCovers(rBlur)
            }
        }

        // Compare avatar hash and download only if remote hash differs from local file hash
        if (!remoteAvatarHash.isNullOrBlank() && remoteAvatarHash != localAvatarHash) {
            try {
                val snap = avatarDocRef.get().await()
                val b64 = snap.getString("base64")
                if (!b64.isNullOrBlank()) {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    avatarFile.writeBytes(bytes)
                    preferencesRepository.setAvatarImagePath(avatarFile.absolutePath)
                    preferencesRepository.setAvatarFileHash(remoteAvatarHash)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao baixar avatar: ${e.message}")
            }
        } else if (localAvatarHash != null && localAvatarHash != storedAvatarHash) {
            // Local avatar changed: upload to users/{uid}/profile/avatar
            try {
                val b64 = Base64.encodeToString(avatarFile.readBytes(), Base64.NO_WRAP)
                avatarDocRef.set(mapOf("base64" to b64, "hash" to localAvatarHash, "updatedAt" to System.currentTimeMillis())).await()
                preferencesRepository.setAvatarFileHash(localAvatarHash)
                remoteAvatarHash = localAvatarHash
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao enviar avatar: ${e.message}")
            }
        }

        // Compare banner hash and download only if remote hash differs from local file hash
        if (!remoteBannerHash.isNullOrBlank() && remoteBannerHash != localBannerHash) {
            try {
                val snap = bannerDocRef.get().await()
                val b64 = snap.getString("base64")
                if (!b64.isNullOrBlank()) {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    bannerFile.writeBytes(bytes)
                    preferencesRepository.setBannerImagePath(bannerFile.absolutePath)
                    preferencesRepository.setBannerFileHash(remoteBannerHash)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao baixar capa: ${e.message}")
            }
        } else if (localBannerHash != null && localBannerHash != storedBannerHash) {
            // Local banner changed: upload to users/{uid}/profile/banner
            try {
                val b64 = Base64.encodeToString(bannerFile.readBytes(), Base64.NO_WRAP)
                bannerDocRef.set(mapOf("base64" to b64, "hash" to localBannerHash, "updatedAt" to System.currentTimeMillis())).await()
                preferencesRepository.setBannerFileHash(localBannerHash)
                remoteBannerHash = localBannerHash
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao enviar capa: ${e.message}")
            }
        }

        // Upload text profile & preferences to users/{uid}/profile/main
        val currentLocalAvatarHash = preferencesRepository.avatarFileHash.first()
        val currentLocalBannerHash = preferencesRepository.bannerFileHash.first()
        val profilePayload = mapOf(
            "uid" to uid,
            "nickname" to localNickname,
            "bio" to localBio,
            "favoriteWorkId" to localFavWork,
            "themeColorHex" to localAccent,
            "adultMode" to localAdultMode.id,
            "blurAdultCovers" to localBlurAdultCovers,
            "avatarHash" to currentLocalAvatarHash,
            "bannerHash" to currentLocalBannerHash,
            "updatedAt" to System.currentTimeMillis()
        )
        profileMainRef.set(profilePayload, SetOptions.merge()).await()
    }

    override suspend fun uploadProfile(): Result<Unit> = withContext(Dispatchers.IO) {
        val user = authRepository.currentUser ?: return@withContext Result.success(Unit)
        val db = firestore ?: return@withContext Result.success(Unit)
        try {
            syncProfileWithFirestore(user.uid, db)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadProfile(): Result<UserProfileSync?> = withContext(Dispatchers.IO) {
        val user = authRepository.currentUser ?: return@withContext Result.success(null)
        val db = firestore ?: return@withContext Result.success(null)
        try {
            val snap = db.collection("users").document(user.uid).collection("profile").document("main").get().await()
            if (!snap.exists()) return@withContext Result.success(null)
            val d = snap.data ?: return@withContext Result.success(null)
            val profile = UserProfileSync(
                nickname = d["nickname"] as? String ?: "Otaku",
                bio = d["bio"] as? String ?: "",
                avatarHash = d["avatarHash"] as? String,
                bannerHash = d["bannerHash"] as? String,
                favoriteWorkId = d["favoriteWorkId"] as? String,
                themeColorHex = d["themeColorHex"] as? String,
                adultMode = d["adultMode"] as? String ?: "off",
                blurAdultCovers = d["blurAdultCovers"] as? Boolean ?: true,
                updatedAt = (d["updatedAt"] as? Number)?.toLong() ?: 0L
            )
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteRemoteUserData(uid: String): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.success(Unit)
        try {
            val userDoc = db.collection("users").document(uid)
            val libraryDocs = userDoc.collection("library").get().await()
            for (doc in libraryDocs.documents) {
                doc.reference.delete().await()
            }
            val profileDocs = userDoc.collection("profile").get().await()
            for (pDoc in profileDocs.documents) {
                pDoc.reference.delete().await()
            }
            userDoc.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao deletar dados remotos: ${e.message}")
            Result.failure(e)
        }
    }

    private fun LibraryEntity.toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "status" to status,
            "isFavorite" to isFavorite,
            "currentProgress" to currentProgress,
            "totalProgress" to totalProgress,
            "userRating" to userRating,
            "addedAt" to addedAt,
            "updatedAt" to updatedAt,
            "workType" to workType,
            "format" to format,
            "title" to title,
            "titleEn" to titleEn,
            "cover" to cover,
            "coverColor" to coverColor,
            "avgScore" to avgScore,
            "workStatus" to workStatus,
            "genresJson" to genresJson,
            "isDeleted" to isDeleted,
            "duration" to duration,
            "nextEpisodeAt" to nextEpisodeAt
        )
    }
}
