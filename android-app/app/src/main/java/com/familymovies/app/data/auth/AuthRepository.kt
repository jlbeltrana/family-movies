package com.familymovies.app.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

class AuthRepository(private val context: Context) {

    // Web client ID (OAuth 2.0) from Firebase Console > Authentication > Sign-in method > Google
    // Replace with your actual Web client ID
    private val webClientId = "447337048510-0so110oqjeon62dlu9i9odp6vcbscpuk.apps.googleusercontent.com"

    suspend fun signInWithGoogle(): Result<String> {
        return try {
            val credentialManager = CredentialManager.create(context)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context = context, request = request)
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            val idToken = googleIdTokenCredential.idToken

            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = Firebase.auth.signInWithCredential(firebaseCredential).await()
            val email = authResult.user?.email
                ?: return Result.failure(Exception("No email returned from Firebase"))

            Result.success(email)
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        Firebase.auth.signOut()
    }

    fun getCurrentUserEmail(): String? = Firebase.auth.currentUser?.email
}
