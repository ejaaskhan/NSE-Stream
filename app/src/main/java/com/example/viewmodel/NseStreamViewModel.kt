package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.NseDatabase
import com.example.data.local.WatchProgressEntity
import com.example.data.remote.ApiConfig
import com.example.data.remote.NseMovieApiClient
import com.example.data.repository.NseStreamRepository
import com.example.model.ApiResult
import com.example.model.CategorySectionState
import com.example.model.MovieCategory
import com.example.model.MovieItem
import com.example.model.PaginatedCatalogPage
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
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
    val homeGlobalError: String? = null,
    val catalogErrorMessage: String? = null,
    val categorySections: Map<MovieCategory, CategorySectionState> =
        MovieCategory.dedicatedContentCategories.associateWith { cat ->
            CategorySectionState(category = cat, isLoadingInitial = true)
        },
    val currentCatalogPage: PaginatedCatalogPage = PaginatedCatalogPage(
        items = emptyList(),
        currentPage = 1,
        totalPages = 1,
        totalResults = 0,
        pageSize = NseMovieApiClient.PAGE_SIZE,
        isFromCache = false,
        queryOrCategoryKey = MovieCategory.HOME.title
    ),
    val featuredHeroMovie: MovieItem? = null,
    val selectedMovieForDetail: MovieItem? = null,
    val isLoadingDetailStreams: Boolean = false,
    val detailStreamError: String? = null,
    val relatedMoviesForDetail: List<MovieItem> = emptyList(),
    val activePlayingMovie: MovieItem? = null,
    val activePlayableStreamUrl: String = "",
    val activeResumePositionMs: Long = 0L,
    val isResolvingStream: Boolean = false,
    val activeStreamError: String? = null,
    val playbackAttemptId: Int = 0,
    val currentApiBaseUrl: String = ApiConfig.DEFAULT_BASE_URL,
    val showServerSettingsDialog: Boolean = false,
    val statusBannerMessage: String? = null
)

class NseStreamViewModel(
    application: Application,
    private val repository: NseStreamRepository
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        repository = NseStreamRepository(
            nseDao = NseDatabase.getInstance(application).nseDao(),
            apiClient = NseMovieApiClient(),
            appContext = application.applicationContext
        )
    )

    private val _uiState = MutableStateFlow(
        NseStreamUiState(currentApiBaseUrl = ApiConfig.getActiveBaseUrl(application))
    )
    val uiState: StateFlow<NseStreamUiState> = _uiState.asStateFlow()

    val watchlistMovies: StateFlow<List<MovieItem>> = repository.watchlistMoviesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchProgressList: StateFlow<List<WatchProgressEntity>> = repository.watchProgressFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var homeLoadJob: Job? = null
    private var pageLoadJob: Job? = null
    private var searchDebounceJob: Job? = null
    private var infiniteScrollJob: Job? = null
    private var detailLoadJob: Job? = null
    private var playbackResolutionJob: Job? = null

    init {
        ApiConfig.init(application)
        _uiState.update { it.copy(currentApiBaseUrl = ApiConfig.getActiveBaseUrl(application)) }
        loadAllHomeCategorySections()
    }

    /**
     * Loads dedicated sections for Bangla, Hollywood, Bollywood, South Indian, and Dual Audio
     * in parallel. Surfaces real network/server errors without faking movie data.
     */
    fun loadAllHomeCategorySections(forceRefresh: Boolean = false) {
        homeLoadJob?.cancel()
        homeLoadJob = viewModelScope.launch {
            _uiState.update { state ->
                val loadingSections = MovieCategory.dedicatedContentCategories.associateWith { cat ->
                    val existing = state.categorySections[cat]
                    (existing ?: CategorySectionState(category = cat)).copy(
                        isLoadingInitial = forceRefresh || existing?.items.isNullOrEmpty(),
                        errorMessage = null
                    )
                }
                state.copy(
                    isLoadingPage = forceRefresh || state.categorySections.values.all { it.items.isEmpty() },
                    homeGlobalError = null,
                    categorySections = loadingSections
                )
            }

            val results = MovieCategory.dedicatedContentCategories.map { category ->
                async {
                    when (val res = repository.getPaginatedCategoryPage(category, page = 1, forceRefresh = forceRefresh)) {
                        is ApiResult.Success -> category to CategorySectionState(
                            category = category,
                            items = res.data.items,
                            currentPage = res.data.currentPage,
                            totalPages = res.data.totalPages,
                            isLoadingInitial = false,
                            isLoadingMore = false,
                            errorMessage = null
                        )
                        is ApiResult.Failure -> category to CategorySectionState(
                            category = category,
                            items = emptyList(),
                            currentPage = 1,
                            totalPages = 1,
                            isLoadingInitial = false,
                            isLoadingMore = false,
                            errorMessage = res.message
                        )
                    }
                }
            }.awaitAll().toMap()

            val allFailed = results.values.all { it.items.isEmpty() && it.errorMessage != null }
            val firstError = results.values.firstNotNullOfOrNull { it.errorMessage }

            val heroCandidate = results[MovieCategory.HOLLYWOOD]?.items?.firstOrNull()
                ?: results[MovieCategory.BOLLYWOOD]?.items?.firstOrNull()
                ?: results[MovieCategory.BANGLA]?.items?.firstOrNull()
                ?: results.values.firstNotNullOfOrNull { it.items.firstOrNull() }

            _uiState.update { state ->
                state.copy(
                    isLoadingPage = false,
                    homeGlobalError = if (allFailed) firstError else null,
                    categorySections = results,
                    featuredHeroMovie = if (forceRefresh) heroCandidate else (state.featuredHeroMovie ?: heroCandidate)
                )
            }
        }
    }

    fun retryCategorySection(category: MovieCategory) {
        viewModelScope.launch {
            _uiState.update { state ->
                val existing = state.categorySections[category] ?: CategorySectionState(category = category)
                state.copy(
                    categorySections = state.categorySections + (
                        category to existing.copy(isLoadingInitial = true, errorMessage = null)
                        )
                )
            }

            when (val res = repository.getPaginatedCategoryPage(category, page = 1, forceRefresh = true)) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        val updated = CategorySectionState(
                            category = category,
                            items = res.data.items,
                            currentPage = res.data.currentPage,
                            totalPages = res.data.totalPages,
                            isLoadingInitial = false,
                            isLoadingMore = false,
                            errorMessage = null
                        )
                        state.copy(
                            homeGlobalError = null,
                            categorySections = state.categorySections + (category to updated),
                            featuredHeroMovie = state.featuredHeroMovie ?: res.data.items.firstOrNull()
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _uiState.update { state ->
                        val existing = state.categorySections[category] ?: CategorySectionState(category = category)
                        state.copy(
                            categorySections = state.categorySections + (
                                category to existing.copy(isLoadingInitial = false, errorMessage = res.message)
                                )
                        )
                    }
                }
            }
        }
    }

    /**
     * Horizontal infinite scroll for a Home category row.
     */
    fun loadMoreForHomeSection(category: MovieCategory) {
        val section = _uiState.value.categorySections[category] ?: return
        if (section.isLoadingInitial || section.isLoadingMore || section.errorMessage != null || section.currentPage >= section.totalPages) {
            return
        }

        _uiState.update { state ->
            state.copy(
                categorySections = state.categorySections + (category to section.copy(isLoadingMore = true))
            )
        }

        viewModelScope.launch {
            val nextPageNum = section.currentPage + 1
            when (val res = repository.getPaginatedCategoryPage(category, page = nextPageNum)) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        val current = state.categorySections[category] ?: section
                        val mergedItems = (current.items + res.data.items).distinctBy { it.id }
                        val updated = current.copy(
                            items = mergedItems,
                            currentPage = res.data.currentPage,
                            totalPages = res.data.totalPages,
                            isLoadingMore = false
                        )
                        state.copy(categorySections = state.categorySections + (category to updated))
                    }
                }
                is ApiResult.Failure -> {
                    _uiState.update { state ->
                        val current = state.categorySections[category] ?: section
                        state.copy(
                            categorySections = state.categorySections + (category to current.copy(isLoadingMore = false)),
                            statusBannerMessage = res.message
                        )
                    }
                }
            }
        }
    }

    fun selectCategory(category: MovieCategory) {
        searchDebounceJob?.cancel()
        _uiState.update {
            it.copy(
                activeCategory = category,
                isSearchActive = false,
                searchQuery = "",
                isLoadingMoreGrid = false,
                catalogErrorMessage = null
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
        searchDebounceJob?.cancel()
        _uiState.update { it.copy(searchQuery = query, catalogErrorMessage = null) }
        if (query.isBlank()) {
            _uiState.update { it.copy(isSearchActive = false, isLoadingMoreGrid = false) }
            if (_uiState.value.activeCategory != MovieCategory.HOME) {
                loadDedicatedCategoryInitial(_uiState.value.activeCategory)
            }
        } else {
            _uiState.update { it.copy(isSearchActive = true, isLoadingMoreGrid = false) }
            // Debounce live search to avoid duplicate requests per keystroke
            searchDebounceJob = viewModelScope.launch {
                delay(350L)
                loadSearchInitial(query = query.trim())
            }
        }
    }

    fun submitSearch(query: String) {
        searchDebounceJob?.cancel()
        val clean = query.trim()
        if (clean.isEmpty()) {
            clearSearch()
            return
        }
        _uiState.update {
            it.copy(
                searchQuery = clean,
                isSearchActive = true,
                isLoadingMoreGrid = false,
                catalogErrorMessage = null
            )
        }
        loadSearchInitial(query = clean, forceRefresh = true)
    }

    fun clearSearch() {
        searchDebounceJob?.cancel()
        pageLoadJob?.cancel()
        _uiState.update {
            it.copy(
                searchQuery = "",
                isSearchActive = false,
                isLoadingMoreGrid = false,
                catalogErrorMessage = null
            )
        }
        val active = _uiState.value.activeCategory
        if (active != MovieCategory.HOME) {
            loadDedicatedCategoryInitial(active)
        }
    }

    fun retryCurrentCatalog() {
        val state = _uiState.value
        if (state.isSearchActive && state.searchQuery.isNotBlank()) {
            loadSearchInitial(state.searchQuery, forceRefresh = true)
        } else if (state.activeCategory == MovieCategory.HOME) {
            loadAllHomeCategorySections(forceRefresh = true)
        } else {
            loadDedicatedCategoryInitial(state.activeCategory, forceRefresh = true)
        }
    }

    fun loadNextPageInfiniteScroll() {
        val state = _uiState.value
        if (state.isLoadingPage || state.isLoadingMoreGrid || state.catalogErrorMessage != null) return
        val currentCatalog = state.currentCatalogPage
        if (currentCatalog.currentPage >= currentCatalog.totalPages) return

        infiniteScrollJob?.cancel()
        infiniteScrollJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreGrid = true) }
            val nextPageNumber = currentCatalog.currentPage + 1

            val result = when {
                state.isSearchActive && state.searchQuery.isNotBlank() ->
                    repository.searchMoviesPaginated(query = state.searchQuery, page = nextPageNumber)
                state.activeCategory != MovieCategory.HOME && state.activeCategory != MovieCategory.MY_LIST ->
                    repository.getPaginatedCategoryPage(category = state.activeCategory, page = nextPageNumber)
                else -> {
                    _uiState.update { it.copy(isLoadingMoreGrid = false) }
                    return@launch
                }
            }

            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { cur ->
                        val combined = (cur.currentCatalogPage.items + result.data.items).distinctBy { it.id }
                        cur.copy(
                            isLoadingMoreGrid = false,
                            currentCatalogPage = result.data.copy(items = combined)
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _uiState.update { cur ->
                        cur.copy(
                            isLoadingMoreGrid = false,
                            statusBannerMessage = result.message
                        )
                    }
                }
            }
        }
    }

    private fun loadDedicatedCategoryInitial(category: MovieCategory, forceRefresh: Boolean = false) {
        pageLoadJob?.cancel()
        pageLoadJob = viewModelScope.launch {
            if (category == MovieCategory.MY_LIST) {
                val savedList = watchlistMovies.value
                _uiState.update {
                    it.copy(
                        isLoadingPage = false,
                        isLoadingMoreGrid = false,
                        catalogErrorMessage = null,
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

            val preloadedSection = _uiState.value.categorySections[category]
            if (!forceRefresh && preloadedSection != null && preloadedSection.items.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoadingPage = false,
                        isLoadingMoreGrid = false,
                        catalogErrorMessage = null,
                        currentCatalogPage = PaginatedCatalogPage(
                            items = preloadedSection.items,
                            currentPage = preloadedSection.currentPage,
                            totalPages = preloadedSection.totalPages,
                            totalResults = preloadedSection.totalPages * NseMovieApiClient.PAGE_SIZE,
                            pageSize = NseMovieApiClient.PAGE_SIZE,
                            isFromCache = true,
                            queryOrCategoryKey = category.title
                        ),
                        featuredHeroMovie = preloadedSection.items.firstOrNull()
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoadingPage = true,
                    isLoadingMoreGrid = false,
                    catalogErrorMessage = null
                )
            }

            when (val result = repository.getPaginatedCategoryPage(category = category, page = 1, forceRefresh = forceRefresh)) {
                is ApiResult.Success -> {
                    val catalogPage = result.data
                    _uiState.update { state ->
                        val updatedSection = CategorySectionState(
                            category = category,
                            items = catalogPage.items,
                            currentPage = catalogPage.currentPage,
                            totalPages = catalogPage.totalPages,
                            isLoadingInitial = false,
                            isLoadingMore = false,
                            errorMessage = null
                        )
                        state.copy(
                            isLoadingPage = false,
                            catalogErrorMessage = null,
                            currentCatalogPage = catalogPage,
                            featuredHeroMovie = catalogPage.items.firstOrNull() ?: state.featuredHeroMovie,
                            categorySections = state.categorySections + (category to updatedSection)
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _uiState.update { state ->
                        state.copy(
                            isLoadingPage = false,
                            catalogErrorMessage = result.message,
                            currentCatalogPage = PaginatedCatalogPage(
                                items = emptyList(),
                                currentPage = 1,
                                totalPages = 1,
                                totalResults = 0,
                                pageSize = NseMovieApiClient.PAGE_SIZE,
                                isFromCache = false,
                                queryOrCategoryKey = category.title
                            )
                        )
                    }
                }
            }
        }
    }

    private fun loadSearchInitial(query: String, forceRefresh: Boolean = false) {
        pageLoadJob?.cancel()
        pageLoadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingPage = true,
                    isLoadingMoreGrid = false,
                    catalogErrorMessage = null
                )
            }
            when (val result = repository.searchMoviesPaginated(query = query, page = 1, forceRefresh = forceRefresh)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingPage = false,
                            catalogErrorMessage = null,
                            currentCatalogPage = result.data
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoadingPage = false,
                            catalogErrorMessage = result.message,
                            currentCatalogPage = PaginatedCatalogPage(
                                items = emptyList(),
                                currentPage = 1,
                                totalPages = 1,
                                totalResults = 0,
                                pageSize = NseMovieApiClient.PAGE_SIZE,
                                isFromCache = false,
                                queryOrCategoryKey = "Search: \"$query\""
                            )
                        )
                    }
                }
            }
        }
    }

    fun openMovieDetail(movie: MovieItem, forceRefresh: Boolean = false) {
        detailLoadJob?.cancel()
        val related = repository.getRelatedMovies(movie)
        _uiState.update {
            it.copy(
                selectedMovieForDetail = movie,
                isLoadingDetailStreams = forceRefresh || movie.qualityOptions.isEmpty(),
                detailStreamError = null,
                relatedMoviesForDetail = related
            )
        }
        detailLoadJob = viewModelScope.launch {
            when (val res = repository.resolvePlayableMovie(movie, forceRefresh = forceRefresh)) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        if (state.selectedMovieForDetail?.id == movie.id) {
                            state.copy(
                                selectedMovieForDetail = res.data,
                                isLoadingDetailStreams = false,
                                detailStreamError = null,
                                relatedMoviesForDetail = repository.getRelatedMovies(res.data)
                            )
                        } else {
                            state
                        }
                    }
                }
                is ApiResult.Failure -> {
                    _uiState.update { state ->
                        if (state.selectedMovieForDetail?.id == movie.id) {
                            state.copy(
                                isLoadingDetailStreams = false,
                                detailStreamError = res.message
                            )
                        } else {
                            state
                        }
                    }
                }
            }
        }
    }

    fun retryDetailStreams() {
        val current = _uiState.value.selectedMovieForDetail ?: return
        openMovieDetail(current.copy(streamUrl = "", qualityOptions = emptyList()), forceRefresh = true)
    }

    fun closeMovieDetail() {
        detailLoadJob?.cancel()
        _uiState.update {
            it.copy(
                selectedMovieForDetail = null,
                isLoadingDetailStreams = false,
                detailStreamError = null
            )
        }
    }

    /**
     * Launches playback strictly bound to `movie.id`. Cancels any prior resolution job, clears
     * stale stream URLs/errors, and surfaces clear error messages if no valid stream exists.
     */
    fun startMoviePlayback(movie: MovieItem, forceRefresh: Boolean = false) {
        playbackResolutionJob?.cancel()

        val immediateUrl = if (!forceRefresh) {
            movie.streamUrl.ifBlank { movie.qualityOptions.firstOrNull()?.streamUrl.orEmpty() }
        } else {
            ""
        }

        val nextAttempt = _uiState.value.playbackAttemptId + 1
        _uiState.update {
            it.copy(
                activePlayingMovie = movie,
                activePlayableStreamUrl = immediateUrl,
                activeResumePositionMs = 0L,
                isResolvingStream = immediateUrl.isBlank(),
                activeStreamError = null,
                playbackAttemptId = nextAttempt
            )
        }

        playbackResolutionJob = viewModelScope.launch {
            val savedPosition = repository.getSavedPositionMs(movie.id)

            when (val res = repository.resolvePlayableMovie(movie = movie, forceRefresh = forceRefresh)) {
                is ApiResult.Success -> {
                    val resolvedMovie = res.data
                    val resolvedUrl = resolvedMovie.streamUrl.ifBlank {
                        resolvedMovie.qualityOptions.firstOrNull()?.streamUrl.orEmpty()
                    }
                    _uiState.update { state ->
                        if (state.activePlayingMovie?.id == movie.id) {
                            if (resolvedUrl.isNotBlank()) {
                                state.copy(
                                    activePlayingMovie = resolvedMovie,
                                    activePlayableStreamUrl = resolvedUrl,
                                    activeResumePositionMs = savedPosition,
                                    isResolvingStream = false,
                                    activeStreamError = null
                                )
                            } else {
                                state.copy(
                                    activePlayingMovie = resolvedMovie,
                                    activePlayableStreamUrl = "",
                                    isResolvingStream = false,
                                    activeStreamError = "No playable video stream is available for \"${movie.title}\"."
                                )
                            }
                        } else {
                            state
                        }
                    }
                }
                is ApiResult.Failure -> {
                    _uiState.update { state ->
                        if (state.activePlayingMovie?.id == movie.id) {
                            state.copy(
                                isResolvingStream = false,
                                activeStreamError = res.message
                            )
                        } else {
                            state
                        }
                    }
                }
            }
        }
    }

    fun retryActivePlayback() {
        val currentMovie = _uiState.value.activePlayingMovie ?: return
        startMoviePlayback(currentMovie, forceRefresh = true)
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
            releaseYear = 2024,
            rating = 8.0,
            maturityRating = "HD",
            durationMinutes = 110,
            primaryCategory = MovieCategory.HOME,
            audioLanguage = "Original Audio",
            genres = listOf("Feature Film"),
            director = "NSE Archive",
            cast = emptyList(),
            nativeAspectRatio = progress.nativeAspectRatio,
            videoResolution = "HD",
            movieLinkSlug = progress.movieId,
            streamUrl = progress.streamUrl,
            backupStreamUrl = progress.streamUrl,
            streamFormat = if (progress.streamUrl.contains(".m3u8", ignoreCase = true)) "HLS" else "MP4"
        )
        startMoviePlayback(movie, forceRefresh = progress.streamUrl.isBlank())
    }

    fun saveCurrentPlaybackProgress(movie: MovieItem, positionMs: Long, durationMs: Long) {
        viewModelScope.launch {
            repository.saveWatchProgress(movie, positionMs, durationMs)
        }
    }

    fun closePlayer(finalPositionMs: Long, durationMs: Long) {
        playbackResolutionJob?.cancel()
        val currentMovie = _uiState.value.activePlayingMovie
        if (currentMovie != null && durationMs > 0L) {
            saveCurrentPlaybackProgress(currentMovie, finalPositionMs, durationMs)
        }
        _uiState.update {
            it.copy(
                activePlayingMovie = null,
                activePlayableStreamUrl = "",
                activeResumePositionMs = 0L,
                isResolvingStream = false,
                activeStreamError = null
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

    fun openServerSettings() {
        _uiState.update {
            it.copy(
                showServerSettingsDialog = true,
                currentApiBaseUrl = ApiConfig.getActiveBaseUrl(getApplication())
            )
        }
    }

    fun dismissServerSettings() {
        _uiState.update { it.copy(showServerSettingsDialog = false) }
    }

    fun saveServerBaseUrl(rawUrl: String): String? {
        val result = ApiConfig.setCustomBaseUrl(getApplication(), rawUrl)
        return result.fold(
            onSuccess = { savedUrl ->
                _uiState.update {
                    it.copy(
                        currentApiBaseUrl = savedUrl,
                        showServerSettingsDialog = false,
                        featuredHeroMovie = null,
                        statusBannerMessage = "Server updated to $savedUrl"
                    )
                }
                reloadAllAfterServerChange()
                null
            },
            onFailure = { err ->
                err.message ?: "Invalid server URL."
            }
        )
    }

    fun resetServerBaseUrl() {
        val defaultUrl = ApiConfig.resetToDefault(getApplication())
        _uiState.update {
            it.copy(
                currentApiBaseUrl = defaultUrl,
                showServerSettingsDialog = false,
                featuredHeroMovie = null,
                statusBannerMessage = "Server reset to default catalog ($defaultUrl)"
            )
        }
        reloadAllAfterServerChange()
    }

    private fun reloadAllAfterServerChange() {
        viewModelScope.launch {
            repository.clearAllCaches()
            val state = _uiState.value
            if (state.isSearchActive && state.searchQuery.isNotBlank()) {
                loadSearchInitial(state.searchQuery, forceRefresh = true)
            } else if (state.activeCategory == MovieCategory.HOME) {
                loadAllHomeCategorySections(forceRefresh = true)
            } else {
                loadDedicatedCategoryInitial(state.activeCategory, forceRefresh = true)
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }
}
