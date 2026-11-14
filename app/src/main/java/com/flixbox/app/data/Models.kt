package com.flixbox.app.data

enum class MediaCategory(val label: String, val folderName: String) {
    ANIME("Anime", "Anime"),
    MOVIE("Movie", "Movie"),
    SERIES("Series", "Series"),
    LIVE_ACTION("Live Action", "Live Action"),
    ALL("Semua", "")
}

data class MediaModel(
    val title: String,
    val folderPath: String,
    val episodeCount: Int,          // total semua episode dari semua season
    val firstEpisodePath: String,
    val category: MediaCategory,
    val posterPath: String = "",
    val hasSeasons: Boolean = false  // true jika punya subfolder season
)

data class SeasonModel(
    val name: String,               // "Season 1", "Season 2", dst
    val folderPath: String,
    val episodes: List<EpisodeModel>,
    val posterPath: String = ""
)

data class EpisodeModel(
    val title: String,
    val path: String,
    val duration: Long,
    val size: Long,
    val episodeNumber: Int
)
