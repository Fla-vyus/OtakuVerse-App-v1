package com.example.data.auth

data class AuthUser(
    val uid: String,
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null
)

sealed class AuthState {
    object Loading : AuthState()
    object LoggedOut : AuthState()
    data class LoggedIn(val user: AuthUser) : AuthState()

    val isUserLoggedIn: Boolean
        get() = this is LoggedIn

    val currentUser: AuthUser?
        get() = (this as? LoggedIn)?.user
}

data class FirebaseStatus(
    val isAppInitialized: Boolean,
    val isGoogleProviderAvailable: Boolean,
    val isFirestoreReachable: Boolean?,
    val details: String = ""
)
