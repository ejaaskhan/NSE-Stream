package com.example.model

/**
 * Typed network/API result wrapper so server errors, timeouts, offline connectivity,
 * and malformed JSON responses are surfaced clearly with Retry recovery instead of
 * silently failing or returning fake data.
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(
        val message: String,
        val isNetworkError: Boolean = false,
        val statusCode: Int? = null
    ) : ApiResult<Nothing>()
}

enum class MovieCategory(val id: String, val title: String, val slug: String) {
    HOME("home", "Home", "home"),
    BANGLA("bangla", "Bangla Movies", "bangla"),
    HOLLYWOOD("hollywood", "Hollywood Movies", "hollywood"),
    BOLLYWOOD("bollywood", "Bollywood Movies", "bollywood"),
    SOUTH_INDIAN("south_indian", "South Indian Movies", "south-indian"),
    DUAL_AUDIO("dual_audio", "Dual Audio Movies", "dual-audio"),
    MY_LIST("my_list", "My List", "my-list");

    companion object {
        val dedicatedContentCategories: List<MovieCategory> = listOf(
            BANGLA,
            HOLLYWOOD,
            BOLLYWOOD,
            SOUTH_INDIAN,
            DUAL_AUDIO
        )
    }
}

data class StreamQualityOption(
    val qualityLabel: String, // "1080p", "720p", "480p", "HLS"
    val badgeDetail: String,  // e.g. "720p • 432 MB (MP4)"
    val streamUrl: String,
    val streamFormat: String = "MP4"
)

data class MovieItem(
    val id: String,
    val title: String,
    val tagline: String,
    val overview: String,
    val posterUrl: String,
    val backdropUrl: String,
    val releaseYear: Int,
    val rating: Double,
    val maturityRating: String,
    val durationMinutes: Int,
    val primaryCategory: MovieCategory,
    val audioLanguage: String,
    val genres: List<String>,
    val director: String,
    val cast: List<String>,
    val nativeAspectRatio: String,
    val videoResolution: String,
    val movieLinkSlug: String,
    val streamUrl: String,
    val backupStreamUrl: String,
    val streamFormat: String,
    val qualityOptions: List<StreamQualityOption> = emptyList(),
    val isFeaturedHero: Boolean = false
)

data class CategorySectionState(
    val category: MovieCategory,
    val items: List<MovieItem> = emptyList(),
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val isLoadingInitial: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null
)

data class PaginatedCatalogPage(
    val items: List<MovieItem>,
    val currentPage: Int,
    val totalPages: Int,
    val totalResults: Int,
    val pageSize: Int,
    val isFromCache: Boolean,
    val queryOrCategoryKey: String
)
