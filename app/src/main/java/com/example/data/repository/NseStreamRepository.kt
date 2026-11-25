package com.example.data.repository

import android.content.Context
import com.example.data.local.CachedPageEntity
import com.example.data.local.NseDao
import com.example.data.local.WatchProgressEntity
import com.example.data.local.WatchlistEntity
import com.example.data.remote.ApiConfig
import com.example.data.remote.NseMovieApiClient
import com.example.model.ApiResult
import com.example.model.MovieCategory
import com.example.model.MovieItem
import com.example.model.PaginatedCatalogPage
import com.example.model.StreamQualityOption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

/**
 * Central repository managing categorized catalog loading, search, stream resolution,
 * request deduplication, connectivity checks, and local Watchlist / Watch Progress storage.
 */
class NseStreamRepository(
    private val nseDao: NseDao,
    private val apiClient: NseMovieApiClient = NseMovieApiClient(),
    private val appContext: Context? = null
) {
    private val memoryPageCache = ConcurrentHashMap<String, PaginatedCatalogPage>()
    private val inFlightCatalogRequests = ConcurrentHashMap<String, CompletableDeferred<ApiResult<PaginatedCatalogPage>>>()
    private val inFlightStreamRequests = ConcurrentHashMap<String, CompletableDeferred<ApiResult<MovieItem>>>()

    val watchlistMoviesFlow: Flow<List<MovieItem>> = nseDao.getAllWatchlist().map { list ->
        list.map { it.toDomainModel() }
    }

    val watchProgressFlow: Flow<List<WatchProgressEntity>> = nseDao.getAllWatchProgress()

    suspend fun clearAllCaches() {
        memoryPageCache.clear()
        inFlightCatalogRequests.clear()
        inFlightStreamRequests.clear()
        apiClient.clearCaches()
        nseDao.clearAllCachedPages()
    }

    suspend fun getPaginatedCategoryPage(
        category: MovieCategory,
        page: Int,
        pageSize: Int = NseMovieApiClient.PAGE_SIZE,
        forceRefresh: Boolean = false
    ): ApiResult<PaginatedCatalogPage> {
        val pageKey = "cat_${category.id}_p_${page}_sz_$pageSize"

        if (!forceRefresh) {
            memoryPageCache[pageKey]?.let { cached ->
                if (cached.items.isNotEmpty()) {
                    return ApiResult.Success(cached.copy(isFromCache = true))
                }
            }
        }

        if (!ApiConfig.isNetworkAvailable(appContext)) {
            return ApiResult.Failure(
                message = "No internet connection. Please check your network and tap Retry.",
                isNetworkError = true
            )
        }

        // Deduplicate simultaneous identical API requests
        val existing = inFlightCatalogRequests[pageKey]
        if (existing != null && !forceRefresh) {
            return existing.await()
        }

        val deferred = CompletableDeferred<ApiResult<PaginatedCatalogPage>>()
        inFlightCatalogRequests[pageKey] = deferred

        return try {
            val result = apiClient.fetchCatalogPage(
                category = category,
                page = page,
                pageSize = pageSize
            )
            if (result is ApiResult.Success && result.data.items.isNotEmpty()) {
                memoryPageCache[pageKey] = result.data
                nseDao.insertCachedPage(
                    CachedPageEntity(
                        pageKey = pageKey,
                        queryOrCategoryKey = category.id,
                        pageNumber = result.data.currentPage,
                        totalPages = result.data.totalPages,
                        totalResults = result.data.totalResults,
                        movieIdsCsv = result.data.items.joinToString(",") { it.id }
                    )
                )
            }
            deferred.complete(result)
            result
        } catch (e: Exception) {
            val failure = ApiResult.Failure(
                message = e.localizedMessage ?: "Failed to load ${category.title}.",
                isNetworkError = true
            )
            deferred.complete(failure)
            failure
        } finally {
            inFlightCatalogRequests.remove(pageKey, deferred)
        }
    }

    suspend fun searchMoviesPaginated(
        query: String,
        page: Int,
        pageSize: Int = NseMovieApiClient.PAGE_SIZE,
        forceRefresh: Boolean = false
    ): ApiResult<PaginatedCatalogPage> {
        val normalizedQuery = query.trim().lowercase()
        val pageKey = "search_${normalizedQuery}_p_${page}_sz_$pageSize"

        if (!forceRefresh) {
            memoryPageCache[pageKey]?.let { cached ->
                if (cached.items.isNotEmpty()) {
                    return ApiResult.Success(cached.copy(isFromCache = true))
                }
            }
        }

        if (!ApiConfig.isNetworkAvailable(appContext)) {
            return ApiResult.Failure(
                message = "You are offline. Connect to the internet to search movies.",
                isNetworkError = true
            )
        }

        val existing = inFlightCatalogRequests[pageKey]
        if (existing != null && !forceRefresh) {
            return existing.await()
        }

        val deferred = CompletableDeferred<ApiResult<PaginatedCatalogPage>>()
        inFlightCatalogRequests[pageKey] = deferred

        return try {
            val result = apiClient.searchMoviesPaginated(
                query = query,
                page = page,
                pageSize = pageSize
            )
            if (result is ApiResult.Success && result.data.items.isNotEmpty()) {
                memoryPageCache[pageKey] = result.data
            }
            deferred.complete(result)
            result
        } catch (e: Exception) {
            val failure = ApiResult.Failure(
                message = e.localizedMessage ?: "Search request failed. Please retry.",
                isNetworkError = true
            )
            deferred.complete(failure)
            failure
        } finally {
            inFlightCatalogRequests.remove(pageKey, deferred)
        }
    }

    suspend fun resolvePlayableMovie(
        movie: MovieItem,
        forceRefresh: Boolean = false
    ): ApiResult<MovieItem> {
        if (!forceRefresh && apiClient.isValidStreamUrl(movie.streamUrl) && movie.qualityOptions.isNotEmpty()) {
            return ApiResult.Success(movie)
        }

        if (!ApiConfig.isNetworkAvailable(appContext)) {
            return ApiResult.Failure(
                message = "No internet connection. Reconnect and tap Retry to play \"${movie.title}\".",
                isNetworkError = true
            )
        }

        val existing = inFlightStreamRequests[movie.id]
        if (existing != null && !forceRefresh) {
            return existing.await()
        }

        val deferred = CompletableDeferred<ApiResult<MovieItem>>()
        inFlightStreamRequests[movie.id] = deferred

        return try {
            val result = apiClient.resolvePlayableMovie(movie = movie, forceRefresh = forceRefresh)
            deferred.complete(result)
            result
        } catch (e: Exception) {
            val failure = ApiResult.Failure(
                message = e.localizedMessage ?: "Unable to resolve stream for \"${movie.title}\".",
                isNetworkError = true
            )
            deferred.complete(failure)
            failure
        } finally {
            inFlightStreamRequests.remove(movie.id, deferred)
        }
    }

    fun getRelatedMovies(movie: MovieItem): List<MovieItem> {
        return apiClient.getAllMoviesPool()
            .filter {
                it.id != movie.id && (
                    it.primaryCategory == movie.primaryCategory ||
                        it.genres.any { g -> movie.genres.contains(g) }
                    )
            }
            .take(6)
    }

    suspend fun toggleWatchlist(movie: MovieItem): Boolean {
        val exists = nseDao.isInWatchlist(movie.id)
        return if (exists) {
            nseDao.deleteFromWatchlist(movie.id)
            false
        } else {
            nseDao.insertWatchlist(movie.toWatchlistEntity())
            true
        }
    }

    suspend fun saveWatchProgress(movie: MovieItem, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        nseDao.upsertWatchProgress(
            WatchProgressEntity(
                movieId = movie.id,
                title = movie.title,
                posterUrl = movie.posterUrl,
                backdropUrl = movie.backdropUrl,
                streamUrl = movie.streamUrl,
                nativeAspectRatio = movie.nativeAspectRatio,
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs
            )
        )
    }

    suspend fun getSavedPositionMs(movieId: String): Long {
        return nseDao.getWatchProgressForMovie(movieId)?.positionMs ?: 0L
    }

    fun findMovieById(movieId: String): MovieItem? = apiClient.getMovieById(movieId)

    private fun MovieItem.toWatchlistEntity() = WatchlistEntity(
        movieId = id,
        title = title,
        tagline = tagline,
        overview = overview,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        releaseYear = releaseYear,
        rating = rating,
        maturityRating = maturityRating,
        durationMinutes = durationMinutes,
        primaryCategoryId = primaryCategory.id,
        audioLanguage = audioLanguage,
        genresCsv = genres.joinToString("|"),
        director = director,
        castCsv = cast.joinToString("|"),
        nativeAspectRatio = nativeAspectRatio,
        videoResolution = videoResolution,
        movieLinkSlug = movieLinkSlug,
        streamUrl = streamUrl,
        backupStreamUrl = backupStreamUrl,
        streamFormat = streamFormat
    )

    private fun WatchlistEntity.toDomainModel(): MovieItem {
        val catalogMatch = apiClient.getMovieById(movieId)
        val fallbackOptions = catalogMatch?.qualityOptions?.ifEmpty { null }
            ?: if (streamUrl.isNotBlank()) {
                listOf(
                    StreamQualityOption(
                        qualityLabel = "HD",
                        badgeDetail = videoResolution.ifBlank { "Direct Stream" },
                        streamUrl = streamUrl,
                        streamFormat = streamFormat
                    )
                )
            } else {
                emptyList()
            }
        val resolvedCategory = MovieCategory.entries.find { it.id == primaryCategoryId } ?: MovieCategory.HOME
        return MovieItem(
            id = movieId,
            title = title,
            tagline = tagline,
            overview = overview,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            releaseYear = releaseYear,
            rating = rating,
            maturityRating = maturityRating,
            durationMinutes = durationMinutes,
            primaryCategory = resolvedCategory,
            audioLanguage = audioLanguage,
            genres = genresCsv.split("|").filter { it.isNotBlank() },
            director = director,
            cast = castCsv.split("|").filter { it.isNotBlank() },
            nativeAspectRatio = nativeAspectRatio,
            videoResolution = videoResolution,
            movieLinkSlug = movieLinkSlug,
            streamUrl = catalogMatch?.streamUrl?.ifBlank { streamUrl } ?: streamUrl,
            backupStreamUrl = catalogMatch?.backupStreamUrl?.ifBlank { backupStreamUrl } ?: backupStreamUrl,
            streamFormat = streamFormat,
            qualityOptions = fallbackOptions
        )
    }
}
