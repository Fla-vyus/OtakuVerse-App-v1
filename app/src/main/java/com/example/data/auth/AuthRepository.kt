package com.example.data.auth

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authState: StateFlow<AuthState>
    val currentUser: AuthUser?

    suspend fun signInWithGoogle(activity: Activity): Result<AuthUser>
    suspend fun signInWithEmail(email: String, password: String): Result<AuthUser>
    suspend fun signUpWithEmail(email: String, password: String): Result<AuthUser>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun signOut(keepLocalData: Boolean): Result<Unit>
    suspend fun deleteAccountAndData(): Result<Unit>
    suspend fun getFirebaseStatus(): FirebaseStatus
}
