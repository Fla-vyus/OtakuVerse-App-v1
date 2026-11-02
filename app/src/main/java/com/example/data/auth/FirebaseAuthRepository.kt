package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.data.local.LibraryDao
import com.example.data.repository.UserPreferencesRepository
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class FirebaseAuthRepository(
    private val context: Context,
    private val libraryDao: LibraryDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val applicationScope: CoroutineScope
) : AuthRepository {

    companion object {
        private const val TAG = "FirebaseAuthRepo"
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val currentUser: AuthUser?
        get() = (_authState.value as? AuthState.LoggedIn)?.user

    private val credentialManager by lazy { CredentialManager.create(context) }

    private val isFirebaseInitialized: Boolean
        get() = try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }

    private val firebaseAuth: FirebaseAuth?
        get() = if (isFirebaseInitialized) {
            try {
                FirebaseAuth.getInstance()
            } catch (e: Exception) {
                Log.w(TAG, "FirebaseAuth.getInstance() failed: ${e.message}")
                null
            }
        } else null

    init {
        initAuthStateListener()
    }

    private fun initAuthStateListener() {
        val auth = firebaseAuth
        if (auth != null) {
            auth.addAuthStateListener { fbAuth ->
                val fbUser = fbAuth.currentUser
                if (fbUser != null) {
                    _authState.value = AuthState.LoggedIn(fbUser.toAuthUser())
                } else {
                    _authState.value = AuthState.LoggedOut
                }
            }
        } else {
            _authState.value = AuthState.LoggedOut
        }
    }

    private fun getDefaultWebClientId(): String? {
        return try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) context.getString(resId) else null
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun signInWithGoogle(activity: Activity): Result<AuthUser> = withContext(Dispatchers.IO) {
        val auth = firebaseAuth
        if (auth == null || !isFirebaseInitialized) {
            return@withContext Result.failure(IllegalStateException("Login indisponível: Firebase não configurado"))
        }

        val clientId = getDefaultWebClientId()
        if (clientId.isNullOrBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Login indisponível: Provedor Google não configurado (default_web_client_id ausente no google-services.json)")
            )
        }

        try {
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential

            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                val user = authResult.user?.toAuthUser()
                    ?: throw IllegalStateException("Usuário retornado pelo Google é nulo.")
                _authState.value = AuthState.LoggedIn(user)
                Result.success(user)
            } else {
                Result.failure(IllegalStateException("Tipo de credencial inesperado."))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelado ou dispensado: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.w(TAG, "Falha no Google Sign-In: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> = withContext(Dispatchers.IO) {
        val auth = firebaseAuth
        if (auth == null || !isFirebaseInitialized) {
            return@withContext Result.failure(IllegalStateException("Login indisponível: Firebase não configurado"))
        }

        try {
            val res = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = res.user?.toAuthUser() ?: throw IllegalStateException("Falha ao autenticar usuário.")
            _authState.value = AuthState.LoggedIn(user)
            Result.success(user)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao entrar com e-mail: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun signUpWithEmail(email: String, password: String): Result<AuthUser> = withContext(Dispatchers.IO) {
        val auth = firebaseAuth
        if (auth == null || !isFirebaseInitialized) {
            return@withContext Result.failure(IllegalStateException("Login indisponível: Firebase não configurado"))
        }

        try {
            val res = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = res.user?.toAuthUser() ?: throw IllegalStateException("Falha ao registrar usuário.")
            _authState.value = AuthState.LoggedIn(user)
            Result.success(user)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao criar conta: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        val auth = firebaseAuth
        if (auth == null || !isFirebaseInitialized) {
            return@withContext Result.failure(IllegalStateException("Login indisponível: Firebase não configurado"))
        }

        try {
            auth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao recuperar senha: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun signOut(keepLocalData: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firebaseAuth?.signOut()
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao limpar credenciais: ${e.message}")
            }
            if (!keepLocalData) {
                libraryDao.clearAll()
            }
            _authState.value = AuthState.LoggedOut
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao sair: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun deleteAccountAndData(): Result<Unit> = withContext(Dispatchers.IO) {
        val auth = firebaseAuth
        val currentFbUser = auth?.currentUser
        try {
            libraryDao.clearAll()
            preferencesRepository.resetAllPreferences()
            if (currentFbUser != null) {
                currentFbUser.delete().await()
            }
            _authState.value = AuthState.LoggedOut
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao excluir conta: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getFirebaseStatus(): FirebaseStatus = withContext(Dispatchers.IO) {
        val isAppInit = isFirebaseInitialized
        val clientId = getDefaultWebClientId()
        val isGoogleOk = !clientId.isNullOrBlank()

        var isFirestoreOk: Boolean? = null
        if (isAppInit) {
            try {
                val db = FirebaseFirestore.getInstance()
                // Quick timeout check
                val probe = withTimeoutOrNull(4000L) {
                    try {
                        db.collection("health_check").limit(1).get().await()
                        true
                    } catch (e: Exception) {
                        // Even if permission denied or empty, Firestore is reachable
                        e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ||
                                e.message?.contains("NOT_FOUND", ignoreCase = true) == true
                    }
                }
                isFirestoreOk = probe ?: false
            } catch (_: Exception) {
                isFirestoreOk = false
            }
        }

        val details = buildString {
            append("App Inicializado: ${if (isAppInit) "Sim" else "Não"}\n")
            append("Provedor Google: ${if (isGoogleOk) "Disponível ($clientId)" else "Indisponível (Falta default_web_client_id)"}\n")
            append("Firestore Alcançável: ${when (isFirestoreOk) { true -> "Sim (Online)"; false -> "Inacessível / Timeout"; else -> "Não verificado" }}")
        }

        FirebaseStatus(
            isAppInitialized = isAppInit,
            isGoogleProviderAvailable = isGoogleOk,
            isFirestoreReachable = isFirestoreOk,
            details = details
        )
    }

    private fun FirebaseUser.toAuthUser(): AuthUser {
        return AuthUser(
            uid = uid,
            email = email,
            displayName = displayName,
            photoUrl = photoUrl?.toString()
        )
    }
}
