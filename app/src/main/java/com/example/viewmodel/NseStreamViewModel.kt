package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.NseDatabase
import com.example.data.local.WatchProgressEntity
import com.example.data.remote.MovieLinkBdClient
import com.example.data.repository.NseStreamRepository
import com.example.model.CategorySectionState
import com.example.model.MovieCategory
import com.example.model.MovieItem
import com.example.model.PaginatedCatalogPage
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NseStreamUiState(
    val activeCategory: MovieCategory = MovieCategory.HOME,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isLoadingPage: Boolean = false,
    val isLoadingMoreGrid: Boolean = false,
    val categorySections: Map<MovieCategory, CategorySectionState> =
        MovieCategory.dedicatedContentCategories.associateWith { cat ->
            CategorySectionState(category = cat, isLoadingInitial = true)
        },
    val currentCatalogPage: PaginatedCatalogPage = PaginatedCatalogPage(
        items = emptyList(),
        currentPage = 1,
        totalPages = 1,
        totalResults = 0,
        pageSize = MovieLinkBdClient.PAGE_SIZE,
        isFromCache = false,
        queryOrCategoryKey = MovieCategory.HOME.title
    ),
    val featuredHeroMovie: MovieItem? = null,
    val selectedMovieForDetail: MovieItem? = null,
    val relatedMoviesForDetail: List<MovieItem> = emptyList(),
    val activePlayingMovie: MovieItem? = null,
    val activePlayableStreamUrl: String = "",
    val activeResumePositionMs: Long = 0L,
    val statusBannerMessage: String? = null
)

class NseStreamViewModel(application: Application) : AndroidViewModel(application) {

    private val database = NseDatabase.getInstance(application)
    private val repository = NseStreamRepository(database.nseDao())

    private val _uiState = MutableStateFlow(NseStreamUiState())
    val uiState: StateFlow<NseStreamUiState> = _uiState.asStateFlow()

    val watchlistMovies: StateFlow<List<MovieItem>> = repository.watchlistMoviesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchProgressList: StateFlow<List<WatchProgressEntity>> = repository.watchProgressFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var pageLoadJob: Job? = null
    private var infiniteScrollJob: Job? = null

    init {
        loadAllHomeCategorySections()
    }

    /**
     * Loads dedicated sections for Bangla Movies, Hollywood Movies, Bollywood Movies,
     * South Indian Movies, and Dual Audio Movies in parallel for the organized Home screen.
     */
    fun loadAllHomeCategorySections() {
        viewModelScope.launch {
            _uiState.update { state ->
                val loadingSections = MovieCategory.dedicatedContentCategories.associateWith { cat ->
                    val existing = state.categorySections[cat]
                    (existing ?: CategorySectionState(category = cat)).copy(
                        isLoadingInitial = existing?.items.isNullOrEmpty()
                    )
                }
                state.copy(
                    isLoadingPage = state.categorySections.values.all { it.items.isEmpty() },
                    categorySections = loadingSections
                )
            }

            val results = MovieCategory.dedicatedContentCategories.map { category ->
                async {
                    val page1 = repository.getPaginatedCategoryPage(category = category, page = 1)
                    category to CategorySectionState(
                        category = category,
                        items = page1.items,
                        currentPage = page1.currentPage,
                        totalPages = page1.totalPages,
                        isLoadingInitial = false,
                        isLoadingMore = false
                    )
                }
            }.awaitAll().toMap()

            val heroCandidate = results[MovieCategory.HOLLYWOOD]?.items?.firstOrNull()
                ?: results[MovieCategory.BOLLYWOOD]?.items?.firstOrNull()
                ?: results[MovieCategory.BANGLA]?.items?.firstOrNull()
                ?: results.values.firstNotNullOfOrNull { it.items.firstOrNull() }

            _uiState.update { state ->
                state.copy(
                    isLoadingPage = false,
                    categorySections = results,
                    featuredHeroMovie = state.featuredHeroMovie ?: heroCandidate
                )
            }
        }
    }

    /**
     * Infinite horizontal scroll for a specific category row on the Home screen.
     */
    fun loadMoreForHomeSection(category: MovieCategory) {
        val section = _uiState.value.categorySections[category] ?: return
        if (section.isLoadingInitial || section.isLoadingMore || section.currentPage >= section.totalPages) {
            return
        }

        _uiState.update { state ->
            val updated = section.copy(isLoadingMore = true)
            state.copy(categorySections = state.categorySections + (category to updated))
        }

        viewModelScope.launch {
            val nextPageNum = section.currentPage + 1
            val nextPage = repository.getPaginatedCategoryPage(category = category, page = nextPageNum)
            _uiState.update { state ->
                val current = state.categorySections[category] ?: section
                val mergedItems = (current.items + nextPage.items).distinctBy { it.id }
                val updated = current.copy(
                    items = mergedItems,
                    currentPage = nextPage.currentPage,
                    totalPages = nextPage.totalPages,
                    isLoadingMore = false
                )
                state.copy(categorySections = state.categorySections + (category to updated))
            }
        }
    }

    fun selectCategory(category: MovieCategory) {
        _uiState.update {
            it.copy(
                activeCategory = category,
                isSearchActive = false,
                searchQuery = "",
                isLoadingMoreGrid = false
            )
        }
        if (category == MovieCategory.HOME) {
            if (_uiState.value.categorySections.values.all { it.items.isEmpty() }) {
                loadAllHomeCategorySections()
            }
        } else {
            loadDedicatedCategoryInitial(category = category)
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(isSearchActive = false, isLoadingMoreGrid = false) }
            if (_uiState.value.activeCategory != MovieCategory.HOME) {
                loadDedicatedCategoryInitial(_uiState.value.activeCategory)
            }
        } else {
            _uiState.update { it.copy(isSearchActive = true, isLoadingMoreGrid = false) }
            loadSearchInitial(query = query)
        }
    }

    fun submitSearch(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) {
            clearSearch()
            return
        }
        _uiState.update {
            it.copy(searchQuery = clean, isSearchActive = true, isLoadingMoreGrid = false)
        }
        loadSearchInitial(query = clean)
    }

    fun clearSearch() {
        _uiState.update {
            it.copy(searchQuery = "", isSearchActive = false, isLoadingMoreGrid = false)
        }
        val active = _uiState.value.activeCategory
        if (active != MovieCategory.HOME) {
            loadDedicatedCategoryInitial(active)
        }
    }

    /**
     * Automatic infinite scroll when reaching the end of a dedicated category grid or search results.
     */
    fun loadNextPageInfiniteScroll() {
        val state = _uiState.value
        if (state.isLoadingPage || state.isLoadingMoreGrid) return
        val currentCatalog = state.currentCatalogPage
        if (currentCatalog.currentPage >= currentCatalog.totalPages) return

        infiniteScrollJob?.cancel()
        infiniteScrollJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreGrid = true) }
            val nextPageNumber = currentCatalog.currentPage + 1

            if (state.isSearchActive && state.searchQuery.isNotBlank()) {
                val nextResult = repository.searchMoviesPaginated(
                    query = state.searchQuery,
                    page = nextPageNumber
                )
                _uiState.update { cur ->
                    val combined = (cur.currentCatalogPage.items + nextResult.items).distinctBy { it.id }
                    cur.copy(
                        isLoadingMoreGrid = false,
                        currentCatalogPage = nextResult.copy(items = combined)
                    )
                }
            } else if (state.activeCategory != MovieCategory.HOME && state.activeCategory != MovieCategory.MY_LIST) {
                val nextResult = repository.getPaginatedCategoryPage(
                    category = state.activeCategory,
                    page = nextPageNumber
                )
                _uiState.update { cur ->
                    val combined = (cur.currentCatalogPage.items + nextResult.items).distinctBy { it.id }
                    cur.copy(
                        isLoadingMoreGrid = false,
                        currentCatalogPage = nextResult.copy(items = combined)
                    )
                }
            } else {
                _uiState.update { it.copy(isLoadingMoreGrid = false) }
            }
        }
    }

    private fun loadDedicatedCategoryInitial(category: MovieCategory) {
        pageLoadJob?.cancel()
        pageLoadJob = viewModelScope.launch {
            if (category == MovieCategory.MY_LIST) {
                val savedList = watchlistMovies.value
                _uiState.update {
                    it.copy(
                        isLoadingPage = false,
                        isLoadingMoreGrid = false,
                        currentCatalogPage = PaginatedCatalogPage(
                            items = savedList,
                            currentPage = 1,
                            totalPages = 1,
                            totalResults = savedList.size,
                            pageSize = savedList.size.coerceAtLeast(1),
                            isFromCache = true,
                            queryOrCategoryKey = MovieCategory.MY_LIST.title
                        )
                    )
                }
                return@launch
            }

            // Use already-fetched Home section items immediately if available so transition is instant
            val preloadedSection = _uiState.value.categorySections[category]
            if (preloadedSection != null && preloadedSection.items.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoadingPage = false,
                        isLoadingMoreGrid = false,
                        currentCatalogPage = PaginatedCatalogPage(
                            items = preloadedSection.items,
                            currentPage = preloadedSection.currentPage,
                            totalPages = preloadedSection.totalPages,
                            totalResults = preloadedSection.totalPages * MovieLinkBdClient.PAGE_SIZE,
                            pageSize = MovieLinkBdClient.PAGE_SIZE,
                            isFromCache = true,
                            queryOrCategoryKey = category.title
                        ),
                        featuredHeroMovie = preloadedSection.items.firstOrNull()
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(isLoadingPage = true, isLoadingMoreGrid = false) }
            val catalogPage = repository.getPaginatedCategoryPage(category = category, page = 1)
            val hero = catalogPage.items.firstOrNull()

            _uiState.update { state ->
                val updatedSection = CategorySectionState(
                    category = category,
                    items = catalogPage.items,
                    currentPage = catalogPage.currentPage,
                    totalPages = catalogPage.totalPages,
                    isLoadingInitial = false,
                    isLoadingMore = false
                )
                state.copy(
                    isLoadingPage = false,
                    currentCatalogPage = catalogPage,
                    featuredHeroMovie = hero ?: state.featuredHeroMovie,
                    categorySections = state.categorySections + (category to updatedSection)
                )
            }
        }
    }

    private fun loadSearchInitial(query: String) {
        pageLoadJob?.cancel()
        pageLoadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPage = true, isLoadingMoreGrid = false) }
            val resultPage = repository.searchMoviesPaginated(query = query, page = 1)
            _uiState.update {
                it.copy(
                    isLoadingPage = false,
                    currentCatalogPage = resultPage
                )
            }
        }
    }

    fun openMovieDetail(movie: MovieItem) {
        val related = repository.getRelatedMovies(movie)
        _uiState.update {
            it.copy(
                selectedMovieForDetail = movie,
                relatedMoviesForDetail = related
            )
        }
        // Pre-resolve fast-start stream & full qualities while user views the detail screen!
        viewModelScope.launch {
            val fast = repository.resolveFastStartMovie(movie)
            _uiState.update { state ->
                if (state.selectedMovieForDetail?.id == movie.id) {
                    state.copy(
                        selectedMovieForDetail = fast,
                        relatedMoviesForDetail = repository.getRelatedMovies(fast)
                    )
                } else {
                    state
                }
            }
            val enriched = repository.enrichMovieWithStreams(fast)
            _uiState.update { state ->
                if (state.selectedMovieForDetail?.id == movie.id) {
                    state.copy(
                        selectedMovieForDetail = enriched,
                        relatedMoviesForDetail = repository.getRelatedMovies(enriched)
                    )
                } else {
                    state
                }
            }
        }
    }

    fun closeMovieDetail() {
        _uiState.update {
            it.copy(selectedMovieForDetail = null)
        }
    }

    /**
     * Two-stage playback launch:
     * 1. Resolves the single fastest H.264 MP4 stream (`resolveFastStartMovie`) so playback
     *    begins within ~1 second.
     * 2. Populates additional qualities (`enrichMovieWithStreams`) in the background for the
     *    in-player quality switcher without interrupting playback.
     */
    fun startMoviePlayback(movie: MovieItem) {
        val initialStream = movie.streamUrl.ifBlank {
            movie.qualityOptions.firstOrNull()?.streamUrl ?: ""
        }
        _uiState.update {
            it.copy(
                activePlayingMovie = movie,
                activePlayableStreamUrl = initialStream,
                activeResumePositionMs = 0L
            )
        }
        viewModelScope.launch {
            val savedPosition = repository.getSavedPositionMs(movie.id)

            // Stage 1: Fast-start stream resolution (~0.9s)
            val fastMovie = repository.resolveFastStartMovie(movie)
            val fastUrl = fastMovie.streamUrl.ifBlank {
                fastMovie.qualityOptions.firstOrNull()?.streamUrl ?: ""
            }
            _uiState.update { state ->
                if (state.activePlayingMovie?.id == movie.id) {
                    state.copy(
                        activePlayingMovie = fastMovie,
                        activePlayableStreamUrl = fastUrl,
                        activeResumePositionMs = savedPosition
                    )
                } else {
                    state
                }
            }

            // Stage 2: Background multi-quality enrichment for the quality switcher
            val fullMovie = repository.enrichMovieWithStreams(fastMovie)
            _uiState.update { state ->
                if (state.activePlayingMovie?.id == movie.id) {
                    val activeUrl = state.activePlayableStreamUrl.ifBlank { fullMovie.streamUrl }
                    state.copy(
                        activePlayingMovie = fullMovie,
                        activePlayableStreamUrl = activeUrl
                    )
                } else {
                    state
                }
            }
        }
    }

    fun resumeMovieFromProgress(progress: WatchProgressEntity) {
        val existing = repository.findMovieById(progress.movieId)
        val movie = existing ?: MovieItem(
            id = progress.movieId,
            title = progress.title,
            tagline = "Resume Playback",
            overview = progress.title,
            posterUrl = progress.posterUrl,
            backdropUrl = progress.backdropUrl,
            releaseYear = 2026,
            rating = 8.0,
            maturityRating = "HD",
            durationMinutes = 120,
            primaryCategory = MovieCategory.HOME,
            audioLanguage = "Original Audio",
            genres = listOf("Movie"),
            director = "MovieLinkBD",
            cast = emptyList(),
            nativeAspectRatio = progress.nativeAspectRatio,
            videoResolution = "1080p",
            movieLinkSlug = progress.title,
            streamUrl = progress.streamUrl,
            backupStreamUrl = progress.streamUrl,
            streamFormat = "MP4"
        )
        _uiState.update {
            it.copy(
                activePlayingMovie = movie,
                activePlayableStreamUrl = progress.streamUrl,
                activeResumePositionMs = progress.positionMs
            )
        }
        viewModelScope.launch {
            val fast = repository.resolveFastStartMovie(movie)
            _uiState.update { state ->
                if (state.activePlayingMovie?.id == movie.id) {
                    state.copy(
                        activePlayingMovie = fast,
                        activePlayableStreamUrl = state.activePlayableStreamUrl.ifBlank { fast.streamUrl }
                    )
                } else {
                    state
                }
            }
        }
    }

    fun saveCurrentPlaybackProgress(movie: MovieItem, positionMs: Long, durationMs: Long) {
        viewModelScope.launch {
            repository.saveWatchProgress(movie, positionMs, durationMs)
        }
    }

    fun closePlayer(finalPositionMs: Long, durationMs: Long) {
        val currentMovie = _uiState.value.activePlayingMovie
        if (currentMovie != null && durationMs > 0L) {
            saveCurrentPlaybackProgress(currentMovie, finalPositionMs, durationMs)
        }
        _uiState.update {
            it.copy(
                activePlayingMovie = null,
                activePlayableStreamUrl = "",
                activeResumePositionMs = 0L
            )
        }
    }

    fun toggleWatchlist(movie: MovieItem) {
        viewModelScope.launch {
            val added = repository.toggleWatchlist(movie)
            val msg = if (added) "Added \"${movie.title}\" to My List" else "Removed \"${movie.title}\" from My List"
            _uiState.update { it.copy(statusBannerMessage = msg) }
            if (_uiState.value.activeCategory == MovieCategory.MY_LIST) {
                loadDedicatedCategoryInitial(MovieCategory.MY_LIST)
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }
}
