package com.familymovies.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.familymovies.app.ui.detail.MovieDetailScreen
import com.familymovies.app.ui.home.HomeScreen
import com.familymovies.app.ui.home.HomeViewModel
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
            // HomeViewModel scoped al backstack entry de "home" — se crea solo cuando llega aquí
            HomeScreen(
                onNavigateToDetail = { movieId ->
                    navController.navigate("detail/$movieId")
                },
                onSignOut = {
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "detail/{movieId}",
            arguments = listOf(navArgument("movieId") { type = NavType.StringType })
        ) { backStackEntry ->
            val movieId = backStackEntry.arguments?.getString("movieId") ?: return@composable
            // Compartir el mismo HomeViewModel que ya existe en "home" (home sigue en el backstack)
            val homeEntry = remember(backStackEntry) { navController.getBackStackEntry("home") }
            val homeViewModel: HomeViewModel = viewModel(homeEntry)
            MovieDetailScreen(
                movieId = movieId,
                homeViewModel = homeViewModel,
                onPlay = { manifestUrl, token, id ->
                    val encUrl = URLEncoder.encode(manifestUrl, StandardCharsets.UTF_8.toString())
                    val encToken = URLEncoder.encode(token, StandardCharsets.UTF_8.toString())
                    val encId = URLEncoder.encode(id, StandardCharsets.UTF_8.toString())
                    navController.navigate("player/$encUrl/$encToken/$encId")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "player/{manifestUrl}/{token}/{movieId}",
            arguments = listOf(
                navArgument("manifestUrl") { type = NavType.StringType },
                navArgument("token") { type = NavType.StringType },
                navArgument("movieId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            fun decode(key: String) = URLDecoder.decode(
                backStackEntry.arguments?.getString(key) ?: "",
                StandardCharsets.UTF_8.toString()
            )
            PlayerScreen(
                manifestUrl = decode("manifestUrl"),
                token = decode("token"),
                movieId = decode("movieId"),
                onBack = { navController.popBackStack() }
            )
        }
    }
}
