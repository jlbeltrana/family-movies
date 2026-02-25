package com.familymovies.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.familymovies.app.navigation.AppNavGraph
import com.familymovies.app.ui.theme.FamilyMoviesTheme
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startDestination = if (Firebase.auth.currentUser != null) "home" else "login"
        setContent {
            FamilyMoviesTheme {
                AppNavGraph(startDestination = startDestination)
            }
        }
    }
}
