package com.familymovies.app.data.firestore

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

class FirestoreRepository {

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
