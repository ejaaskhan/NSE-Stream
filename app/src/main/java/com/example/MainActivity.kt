package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.remote.ApiConfig
import com.example.model.CategorySectionState
import com.example.model.MovieCategory
import com.example.ui.components.CategorySectionRow
import com.example.ui.components.ContinueWatchingStrip
import com.example.ui.components.HeroSpotlightBanner
import com.example.ui.components.MovieDetailScreen
import com.example.ui.components.MoviePosterCard
import com.example.ui.components.NetworkErrorCard
import com.example.ui.components.NseTopNavigationBar
import com.example.ui.components.ServerSettingsDialog
import com.example.ui.components.YouTubeExoPlayerScreen
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NseCrimson
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SlateCardBorder
import com.example.viewmodel.NseStreamViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiConfig.init(this)
        coil.Coil.setImageLoader(NseSafeImageLoader.get(this))
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                NseStreamApp()
            }
        }
    }
}

@Composable
fun NseStreamApp(
    viewModel: NseStreamViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val watchlistMovies by viewModel.watchlistMovies.collectAsStateWithLifecycle()
    val watchProgressList by viewModel.watchProgressList.collectAsStateWithLifecycle()

    val watchlistIds = remember(watchlistMovies) { watchlistMovies.map { it.id }.toSet() }
    val progressByMovieId = remember(watchProgressList) {
        watchProgressList.associateBy { it.movieId }
    }

    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val gridState = rememberLazyGridState()

    LaunchedEffect(
        uiState.activeCategory,
        uiState.searchQuery
    ) {
        gridState.scrollToItem(0)
    }

    LaunchedEffect(uiState.statusBannerMessage) {
        uiState.statusBannerMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    // Automatic infinite scroll when browsing a dedicated category ("See All") or search results
    val shouldLoadMoreGrid by remember(
        uiState.currentCatalogPage.items.size,
        uiState.currentCatalogPage.currentPage,
        uiState.currentCatalogPage.totalPages,
        uiState.isLoadingMoreGrid,
        uiState.isLoadingPage,
        uiState.catalogErrorMessage,
        uiState.activeCategory,
        uiState.isSearchActive
    ) {
        derivedStateOf {
            val isGridActive = uiState.activeCategory != MovieCategory.HOME || uiState.isSearchActive
            if (!isGridActive ||
                uiState.activeCategory == MovieCategory.MY_LIST ||
                uiState.isLoadingPage ||
                uiState.isLoadingMoreGrid ||
                uiState.catalogErrorMessage != null ||
                uiState.currentCatalogPage.currentPage >= uiState.currentCatalogPage.totalPages ||
                uiState.currentCatalogPage.items.isEmpty()
            ) {
                false
            } else {
                val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= (uiState.currentCatalogPage.items.size - 6).coerceAtLeast(0)
            }
        }
    }

    LaunchedEffect(shouldLoadMoreGrid) {
        if (shouldLoadMoreGrid) {
            viewModel.loadNextPageInfiniteScroll()
        }
    }

    // 1. Full-screen Media3 ExoPlayer with HLS/MP4, Screen Rotation & Error Recovery
    val activePlayingMovie = uiState.activePlayingMovie
    if (activePlayingMovie != null) {
        YouTubeExoPlayerScreen(
            movie = activePlayingMovie,
            streamUrl = uiState.activePlayableStreamUrl,
            initialPositionMs = uiState.activeResumePositionMs,
            isResolvingStream = uiState.isResolvingStream,
            streamErrorMessage = uiState.activeStreamError,
            playbackAttemptId = uiState.playbackAttemptId,
            onRetryStream = { viewModel.retryActivePlayback() },
            onSaveProgress = { posMs, durMs ->
                viewModel.saveCurrentPlaybackProgress(activePlayingMovie, posMs, durMs)
            },
            onClosePlayer = { finalPosMs, durMs ->
                viewModel.closePlayer(finalPosMs, durMs)
            }
        )
        return
    }

    // 2. Movie Detail Screen
    val detailMovie = uiState.selectedMovieForDetail
    if (detailMovie != null) {
        MovieDetailScreen(
            movie = detailMovie,
            isInWatchlist = watchlistIds.contains(detailMovie.id),
            savedProgressMs = progressByMovieId[detailMovie.id]?.positionMs ?: 0L,
            isLoadingStreams = uiState.isLoadingDetailStreams,
            streamErrorMessage = uiState.detailStreamError,
            relatedMovies = uiState.relatedMoviesForDetail,
            onPlayMovie = { movie -> viewModel.startMoviePlayback(movie) },
            onRetryStreams = { viewModel.retryDetailStreams() },
            onToggleWatchlist = { movie -> viewModel.toggleWatchlist(movie) },
            onSelectRelatedMovie = { movie -> viewModel.openMovieDetail(movie) },
            onClose = { viewModel.closeMovieDetail() }
        )
        return
    }

    // Back handler when inside a dedicated category view or active search
    BackHandler(enabled = uiState.activeCategory != MovieCategory.HOME || uiState.isSearchActive) {
        if (uiState.isSearchActive) {
            isSearchExpanded = false
            viewModel.clearSearch()
        } else {
            viewModel.selectCategory(MovieCategory.HOME)
        }
    }

    // 3. Main Categorized Sections & Infinite-Scroll Discovery Interface
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack),
        containerColor = ObsidianBlack,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            NseTopNavigationBar(
                activeCategory = uiState.activeCategory,
                searchQuery = uiState.searchQuery,
                isSearchExpanded = isSearchExpanded,
                watchlistCount = watchlistMovies.size,
                onToggleSearch = {
                    isSearchExpanded = !isSearchExpanded
                    if (!isSearchExpanded && uiState.searchQuery.isNotBlank()) {
                        viewModel.clearSearch()
                    }
                },
                onOpenServerSettings = { viewModel.openServerSettings() },
                onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                onSearchSubmit = { viewModel.submitSearch(it) },
                onClearSearch = { viewModel.clearSearch() },
                onSelectCategory = { cat -> viewModel.selectCategory(cat) }
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ObsidianBlack)
        ) {
            val isHomeSectionsMode = uiState.activeCategory == MovieCategory.HOME && !uiState.isSearchActive

            if (isHomeSectionsMode) {
                if (uiState.homeGlobalError != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NetworkErrorCard(
                            title = "Unable to Connect to Server",
                            message = uiState.homeGlobalError!!,
                            onRetry = { viewModel.loadAllHomeCategorySections(forceRefresh = true) },
                            onOpenServerSettings = { viewModel.openServerSettings() },
                            retryTag = "home_global_retry_button"
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("home_category_sections_list")
                    ) {
                        val heroMovie = uiState.featuredHeroMovie
                        if (heroMovie != null) {
                            item(key = "hero_banner") {
                                HeroSpotlightBanner(
                                    movie = heroMovie,
                                    isInWatchlist = watchlistIds.contains(heroMovie.id),
                                    onPlayClick = { viewModel.startMoviePlayback(it) },
                                    onDetailsClick = { viewModel.openMovieDetail(it) },
                                    onToggleWatchlist = { viewModel.toggleWatchlist(it) },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }

                        if (watchProgressList.isNotEmpty()) {
                            item(key = "continue_watching") {
                                ContinueWatchingStrip(
                                    progressItems = watchProgressList,
                                    onResumeClick = { viewModel.resumeMovieFromProgress(it) },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }

                        items(
                            items = MovieCategory.dedicatedContentCategories,
                            key = { it.name }
                        ) { category ->
                            val sectionState = uiState.categorySections[category]
                                ?: CategorySectionState(category = category, isLoadingInitial = true)
                            CategorySectionRow(
                                section = sectionState,
                                onSeeAllClick = { viewModel.selectCategory(category) },
                                onMovieClick = { viewModel.openMovieDetail(it) },
                                onQuickPlayClick = { viewModel.startMoviePlayback(it) },
                                onLoadMore = { viewModel.loadMoreForHomeSection(category) },
                                onRetrySection = { viewModel.retryCategorySection(category) }
                            )
                        }

                        item(key = "bottom_spacer") {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            } else {
                val columns = when {
                    maxWidth >= 840.dp -> 4
                    maxWidth >= 600.dp -> 3
                    else -> 2
                }

                when {
                    uiState.isLoadingPage -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = NseCrimson)
                                Text(
                                    text = "Loading ${uiState.currentCatalogPage.queryOrCategoryKey}...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSilver
                                )
                            }
                        }
                    }
                    uiState.catalogErrorMessage != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            NetworkErrorCard(
                                title = "Failed to Load Movies",
                                message = uiState.catalogErrorMessage!!,
                                onRetry = { viewModel.retryCurrentCatalog() },
                                onOpenServerSettings = { viewModel.openServerSettings() },
                                retryTag = "catalog_retry_button"
                            )
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(columns),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("category_movie_grid")
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = CharcoalSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, SlateCardBorder, RoundedCornerShape(10.dp))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(ObsidianBlack)
                                                    .border(1.dp, SlateCardBorder, RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        if (uiState.isSearchActive) {
                                                            isSearchExpanded = false
                                                            viewModel.clearSearch()
                                                        } else {
                                                            viewModel.selectCategory(MovieCategory.HOME)
                                                        }
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                                    .testTag("back_to_home_sections_button")
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                        contentDescription = "Back to all categories",
                                                        tint = CrispWhite,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Sections",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = CrispWhite
                                                    )
                                                }
                                            }

                                            Text(
                                                text = uiState.currentCatalogPage.queryOrCategoryKey,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = CrispWhite,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Text(
                                            text = "${uiState.currentCatalogPage.items.size} Movies",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = NseCrimson,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            if (uiState.currentCatalogPage.items.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = CharcoalSurface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 28.dp)
                                            .border(1.dp, SlateCardBorder, RoundedCornerShape(14.dp))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(28.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MovieFilter,
                                                contentDescription = "No movies found",
                                                tint = NseCrimson,
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Text(
                                                text = if (uiState.activeCategory == MovieCategory.MY_LIST) {
                                                    "Your Watchlist is Empty"
                                                } else {
                                                    "No Matching Movies Found"
                                                },
                                                style = MaterialTheme.typography.titleLarge,
                                                color = CrispWhite
                                            )
                                            Text(
                                                text = if (uiState.activeCategory == MovieCategory.MY_LIST) {
                                                    "Tap 'My List' on any movie banner or detail screen to save titles locally on your device."
                                                } else {
                                                    "Try searching another movie title or switch to Bangla, Hollywood, or Bollywood Movies."
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MutedSilver,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(
                                    items = uiState.currentCatalogPage.items,
                                    key = { it.id }
                                ) { movie ->
                                    MoviePosterCard(
                                        movie = movie,
                                        onMovieClick = { viewModel.openMovieDetail(it) },
                                        onQuickPlayClick = { viewModel.startMoviePlayback(it) }
                                    )
                                }
                            }

                            if (uiState.isLoadingMoreGrid) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = NseCrimson,
                                            modifier = Modifier.size(28.dp),
                                            strokeWidth = 2.5.dp
                                        )
                                    }
                                }
                            }

                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showServerSettingsDialog) {
        ServerSettingsDialog(
            currentBaseUrl = uiState.currentApiBaseUrl,
            onDismiss = { viewModel.dismissServerSettings() },
            onSaveBaseUrl = { rawUrl -> viewModel.saveServerBaseUrl(rawUrl) },
            onResetDefault = { viewModel.resetServerBaseUrl() }
        )
    }
}
