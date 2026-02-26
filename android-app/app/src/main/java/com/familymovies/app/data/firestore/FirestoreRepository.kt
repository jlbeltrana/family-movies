package com.familymovies.app.data.firestore

import com.familymovies.app.data.model.Movie
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

class FirestoreRepository {

    suspend fun saveProgress(movieId: String, positionMs: Long) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        try {
            Firebase.firestore
                .collection("users").document(uid)
                .collection("progress").document(movieId)
                .set(mapOf("position" to positionMs, "updatedAt" to com.google.firebase.Timestamp.now()))
                .await()
        } catch (e: Exception) { /* fire-and-forget */ }
    }

    suspend fun getProgress(movieId: String): Long {
        val uid = Firebase.auth.currentUser?.uid ?: return 0L
        return try {
            val doc = Firebase.firestore
                .collection("users").document(uid)
                .collection("progress").document(movieId)
                .get().await()
            doc.getLong("position") ?: 0L
        } catch (e: Exception) { 0L }
    }

    suspend fun getMovies(): List<Movie> {
        val snapshot = Firebase.firestore.collection("movies").get().await()
        return snapshot.documents.mapNotNull { doc ->
            val title = doc.getString("title") ?: return@mapNotNull null
            val manifestPath = doc.getString("manifestPath") ?: return@mapNotNull null
            Movie(
                id = doc.id,
                title = title,
                manifestPath = manifestPath,
                posterPath = doc.getString("posterPath") ?: "movies/${doc.id}/poster.jpg",
                category = doc.getString("category") ?: "otros",
                year = doc.getLong("year")?.toInt() ?: 0,
                duration = doc.getLong("duration")?.toInt() ?: 0
            )
        }
    }

    suspend fun isUserAllowed(email: String): Boolean {
        return try {
            val doc = Firebase.firestore
                .collection("config")
                .document("allowedEmails")
                .get()
                .await()
            val emails = doc.get("emails") as? Map<*, *> ?: return false
            emails[email] == true
        } catch (e: Exception) {
            false
        }
    }
}
