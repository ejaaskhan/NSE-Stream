package com.example.data.repository

import com.example.data.local.CachedPageEntity
import com.example.data.local.NseDao
import com.example.data.local.WatchProgressEntity
import com.example.data.local.WatchlistEntity
import com.example.data.remote.MovieLinkBdClient
import com.example.model.MovieCategory
import com.example.model.MovieItem
import com.example.model.PaginatedCatalogPage
import com.example.model.StreamQualityOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

class NseStreamRepository(
    private val nseDao: NseDao,
    private val movieLinkClient: MovieLinkBdClient = MovieLinkBdClient()
) {
    private val memoryPageCache = ConcurrentHashMap<String, PaginatedCatalogPage>()

    val watchlistMoviesFlow: Flow<List<MovieItem>> = nseDao.getAllWatchlist().map { list ->
        list.map { it.toDomainModel() }
    }

    val watchProgressFlow: Flow<List<WatchProgressEntity>> = nseDao.getAllWatchProgress()

    suspend fun getPaginatedCategoryPage(
        category: MovieCategory,
        page: Int,
        pageSize: Int = MovieLinkBdClient.PAGE_SIZE,
        forceRefresh: Boolean = false
    ): PaginatedCatalogPage {
        val pageKey = "cat_${category.id}_p_${page}_sz_$pageSize"

        if (!forceRefresh) {
            memoryPageCache[pageKey]?.let { cached ->
                if (cached.items.isNotEmpty()) {
                    return cached.copy(isFromCache = true)
                }
            }
        }

        val livePage = movieLinkClient.fetchCatalogPage(category = category, page = page)
        if (livePage.items.isNotEmpty()) {
            memoryPageCache[pageKey] = livePage
            nseDao.insertCachedPage(
                CachedPageEntity(
                    pageKey = pageKey,
                    queryOrCategoryKey = category.id,
                    pageNumber = livePage.currentPage,
                    totalPages = livePage.totalPages,
                    totalResults = livePage.totalResults,
                    movieIdsCsv = livePage.items.joinToString(",") { it.id }
                )
            )
            return livePage
        }

        val dbCached = nseDao.getCachedPage(pageKey)
        if (dbCached != null) {
            val allMap = movieLinkClient.getAllMoviesPool().associateBy { it.id }
            val items = dbCached.movieIdsCsv.split(",")
                .mapNotNull { id -> allMap[id] }
            if (items.isNotEmpty()) {
                return PaginatedCatalogPage(
                    items = items,
                    currentPage = dbCached.pageNumber,
                    totalPages = dbCached.totalPages,
                    totalResults = dbCached.totalResults,
                    pageSize = pageSize,
                    isFromCache = true,
                    queryOrCategoryKey = category.title
                )
            }
        }

        return livePage
    }

    suspend fun searchMoviesPaginated(
        query: String,
        page: Int,
        pageSize: Int = MovieLinkBdClient.PAGE_SIZE,
        forceRefresh: Boolean = false
    ): PaginatedCatalogPage {
        val normalizedQuery = query.trim().lowercase()
        val pageKey = "search_${normalizedQuery}_p_${page}_sz_$pageSize"

        if (!forceRefresh) {
            memoryPageCache[pageKey]?.let { cached ->
                if (cached.items.isNotEmpty()) {
                    return cached.copy(isFromCache = true)
                }
            }
        }

        val resultPage = movieLinkClient.searchMoviesPaginated(query = query, page = page)
        if (resultPage.items.isNotEmpty()) {
            memoryPageCache[pageKey] = resultPage
        }
        return resultPage
    }

    suspend fun resolveFastStartMovie(movie: MovieItem): MovieItem {
        return movieLinkClient.resolveFastStartMovie(movie)
    }

    suspend fun enrichMovieWithStreams(movie: MovieItem): MovieItem {
        return movieLinkClient.enrichMovieWithPlayableStreams(movie)
    }

    suspend fun resolvePlayableStreamUrl(movie: MovieItem): String {
        return movieLinkClient.resolveBestStreamUrl(movie)
    }

    fun getRelatedMovies(movie: MovieItem): List<MovieItem> {
        return movieLinkClient.getAllMoviesPool()
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

    fun findMovieById(movieId: String): MovieItem? = movieLinkClient.getMovieById(movieId)

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
        val catalogMatch = movieLinkClient.getMovieById(movieId)
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
