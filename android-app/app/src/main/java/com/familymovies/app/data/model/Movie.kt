package com.familymovies.app.data.model

data class Movie(
    val id: String,
    val title: String,
    val manifestPath: String,
    val posterPath: String,
    val category: String,
    val year: Int,
    val duration: Int
)
