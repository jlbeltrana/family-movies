package com.familymovies.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.familymovies.app.ui.home.HomeScreen
import com.familymovies.app.ui.login.LoginScreen
import com.familymovies.app.ui.player.PlayerScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun AppNavGraph(startDestination: String) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {

        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            HomeScreen(
                onPlayVideo = { manifestUrl, token ->
                    val encodedUrl = URLEncoder.encode(manifestUrl, StandardCharsets.UTF_8.toString())
                    val encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8.toString())
                    navController.navigate("player/$encodedUrl/$encodedToken")
                },
                onSignOut = {
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "player/{manifestUrl}/{token}",
            arguments = listOf(
                navArgument("manifestUrl") { type = NavType.StringType },
                navArgument("token") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val manifestUrl = URLDecoder.decode(
                backStackEntry.arguments?.getString("manifestUrl") ?: "",
                StandardCharsets.UTF_8.toString()
            )
            val token = URLDecoder.decode(
                backStackEntry.arguments?.getString("token") ?: "",
                StandardCharsets.UTF_8.toString()
            )
            PlayerScreen(
                manifestUrl = manifestUrl,
                token = token,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
