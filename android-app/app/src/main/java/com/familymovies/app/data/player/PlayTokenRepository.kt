package com.familymovies.app.data.player

import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

data class PlayTokenResult(
    val token: String,
    val baseUrl: String
)

class PlayTokenRepository {

    // Region must be europe-west1 — this is where the Cloud Function is deployed
    private val functions = Firebase.functions("europe-west1")

    suspend fun getPlayToken(movieId: String): Result<PlayTokenResult> {
        return try {
            val result = functions
                .getHttpsCallable("getPlayToken")
                .call(mapOf("movieId" to movieId))
                .await()

            @Suppress("UNCHECKED_CAST")
            val data = result.getData<Map<String, Any>>()
                ?: return Result.failure(Exception("No data in response"))

            val token = data["token"] as? String
                ?: return Result.failure(Exception("No token in response"))
            val baseUrl = data["baseUrl"] as? String
                ?: return Result.failure(Exception("No baseUrl in response"))

            Result.success(PlayTokenResult(token = token, baseUrl = baseUrl))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
