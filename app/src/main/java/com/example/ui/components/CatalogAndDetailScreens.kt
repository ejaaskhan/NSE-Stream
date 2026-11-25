package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.NseSafeImageLoader
import com.example.R
import com.example.data.local.WatchProgressEntity
import com.example.model.CategorySectionState
import com.example.model.MovieItem
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.GraphiteElevated
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.NseCrimson
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SlateCardBorder

/**
 * Dedicated Horizontal Category Row (e.g., Bangla Movies, Hollywood Movies, Bollywood Movies)
 * with "See All" navigation and automatic horizontal infinite scroll.
 */
@Composable
fun CategorySectionRow(
    section: CategorySectionState,
    onSeeAllClick: () -> Unit,
    onMovieClick: (MovieItem) -> Unit,
    onQuickPlayClick: (MovieItem) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore, section.items.size) {
        if (shouldLoadMore && !section.isLoadingInitial && !section.isLoadingMore) {
            onLoadMore()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("category_section_${section.category.id}"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section Header with Crimson Accent & "See All" Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(NseCrimson)
                )
                Text(
                    text = section.category.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = CrispWhite,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = GraphiteElevated,
                modifier = Modifier
                    .border(1.dp, SlateCardBorder, RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .clickable { onSeeAllClick() }
                    .testTag("see_all_${section.category.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "See All",
                        style = MaterialTheme.typography.labelMedium,
                        color = NseCrimson,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "See All ${section.category.title}",
                        tint = NseCrimson,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (section.isLoadingInitial && section.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = NseCrimson,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(32.dp)
                )
            }
        } else {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(
                    items = section.items,
                    key = { _, item -> "${section.category.id}_${item.id}" }
                ) { _, movie ->
                    MoviePosterCard(
                        movie = movie,
                        onMovieClick = onMovieClick,
                        onQuickPlayClick = onQuickPlayClick,
                        modifier = Modifier.width(154.dp)
                    )
                }

                if (section.isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .height(210.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = NseCrimson,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeroSpotlightBanner(
    movie: MovieItem,
    isInWatchlist: Boolean,
    onPlayClick: (MovieItem) -> Unit,
    onDetailsClick: (MovieItem) -> Unit,
    onToggleWatchlist: (MovieItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(310.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, SlateCardBorder, RoundedCornerShape(16.dp))
            .clickable { onDetailsClick(movie) }
            .testTag("hero_spotlight_banner")
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_cinema_hero_banner_1794136873794),
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        AsyncImage(
            model = movie.backdropUrl,
            imageLoader = NseSafeImageLoader.get(LocalContext.current),
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ObsidianBlack.copy(alpha = 0.20f),
                            ObsidianBlack.copy(alpha = 0.65f),
                            ObsidianBlack.copy(alpha = 0.96f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = NseCrimson,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = movie.primaryCategory.title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = CrispWhite,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    color = GraphiteElevated.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.border(1.dp, SignalEmerald.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                ) {
                    Text(
                        text = "★ ${movie.rating} • ${movie.releaseYear}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SignalEmerald,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = movie.title.uppercase(),
                style = MaterialTheme.typography.displayMedium,
                color = CrispWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = movie.tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedSilver,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Button(
                    onClick = { onPlayClick(movie) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NseCrimson,
                        contentColor = CrispWhite
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("hero_play_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Movie"
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Play Now",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = { onToggleWatchlist(movie) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CharcoalSurface.copy(alpha = 0.75f),
                        contentColor = CrispWhite
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("hero_watchlist_button")
                ) {
                    Icon(
                        imageVector = if (isInWatchlist) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = "Toggle My List"
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isInWatchlist) "In My List" else "My List",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                IconButton(
                    onClick = { onDetailsClick(movie) },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GraphiteElevated.copy(alpha = 0.8f))
                        .testTag("hero_info_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Movie Details",
                        tint = CrispWhite
                    )
                }
            }
        }
    }
}

@Composable
fun ContinueWatchingStrip(
    progressItems: List<WatchProgressEntity>,
    onResumeClick: (WatchProgressEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (progressItems.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Continue Watching",
            style = MaterialTheme.typography.titleMedium,
            color = CrispWhite
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            progressItems.take(6).forEach { item ->
                val progressFraction = if (item.durationMs > 0L) {
                    (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0.05f, 1f)
                } else {
                    0.1f
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CharcoalSurface,
                    modifier = Modifier
                        .width(210.dp)
                        .border(1.dp, SlateCardBorder, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onResumeClick(item) }
                        .testTag("continue_watching_${item.movieId}")
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(105.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_cinema_hero_banner_1794136873794),
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            AsyncImage(
                                model = item.backdropUrl,
                                imageLoader = NseSafeImageLoader.get(LocalContext.current),
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(ObsidianBlack.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(NseCrimson),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Resume",
                                        tint = CrispWhite
                                    )
                                }
                            }
                        }
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            color = NseCrimson,
                            trackColor = GraphiteElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                        )
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = CrispWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatDurationMs(item.positionMs)} / ${formatDurationMs(item.durationMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedSilver
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Minimalist Netflix-style Movie Poster Card displaying only the poster artwork,
 * star rating badge, quick-play trigger, movie title, and release year.
 */
@Composable
fun MoviePosterCard(
    movie: MovieItem,
    onMovieClick: (MovieItem) -> Unit,
    onQuickPlayClick: (MovieItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CharcoalSurface,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable { onMovieClick(movie) }
            .testTag("movie_card_${movie.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.70f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_cinema_hero_banner_1794136873794),
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                AsyncImage(
                    model = movie.posterUrl,
                    imageLoader = NseSafeImageLoader.get(LocalContext.current),
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ObsidianBlack.copy(alpha = 0.50f),
                                    Color.Transparent,
                                    ObsidianBlack.copy(alpha = 0.85f)
                                )
                            )
                        )
                )

                // Minimalist Top-Left Rating Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ObsidianBlack.copy(alpha = 0.82f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = AmberGold,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = movie.rating.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = CrispWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Quick Play Button
                IconButton(
                    onClick = { onQuickPlayClick(movie) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NseCrimson)
                        .testTag("quick_play_${movie.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play ${movie.title}",
                        tint = CrispWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Minimalist Footer: Title + Release Year Only
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = CrispWhite,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = movie.releaseYear.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedSilver
                )
            }
        }
    }
}

@Composable
fun MovieDetailScreen(
    movie: MovieItem,
    isInWatchlist: Boolean,
    savedProgressMs: Long,
    relatedMovies: List<MovieItem>,
    onPlayMovie: (MovieItem) -> Unit,
    onToggleWatchlist: (MovieItem) -> Unit,
    onSelectRelatedMovie: (MovieItem) -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("movie_detail_screen")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_cinema_hero_banner_1794136873794),
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            AsyncImage(
                model = movie.backdropUrl,
                imageLoader = NseSafeImageLoader.get(LocalContext.current),
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ObsidianBlack.copy(alpha = 0.45f),
                                Color.Transparent,
                                ObsidianBlack
                            )
                        )
                    )
            )

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .padding(16.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(CharcoalSurface.copy(alpha = 0.85f))
                    .align(Alignment.TopStart)
                    .testTag("detail_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Catalog",
                    tint = CrispWhite
                )
            }

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(NseCrimson.copy(alpha = 0.92f))
                    .clickable { onPlayMovie(movie) }
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Movie",
                    tint = CrispWhite,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = movie.title.uppercase(),
                style = MaterialTheme.typography.displayMedium,
                color = CrispWhite
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Rating",
                        tint = AmberGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${movie.rating} Rating",
                        style = MaterialTheme.typography.labelLarge,
                        color = SignalEmerald,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(text = "${movie.releaseYear}", style = MaterialTheme.typography.bodyMedium, color = CrispWhite)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = GraphiteElevated
                ) {
                    Text(
                        text = movie.maturityRating,
                        style = MaterialTheme.typography.labelSmall,
                        color = CrispWhite,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = "${movie.durationMinutes} min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSilver
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = GraphiteElevated,
                    modifier = Modifier.border(1.dp, SlateCardBorder, RoundedCornerShape(4.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "Native Aspect Ratio",
                            tint = NseCrimson,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = movie.nativeAspectRatio,
                            style = MaterialTheme.typography.labelSmall,
                            color = CrispWhite
                        )
                    }
                }
            }

            Button(
                onClick = { onPlayMovie(movie) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NseCrimson,
                    contentColor = CrispWhite
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("detail_play_button")
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                val playLabel = if (savedProgressMs > 5_000L) {
                    "Resume Movie (${formatDurationMs(savedProgressMs)})"
                } else {
                    "Stream Now (Fast-Start HD)"
                }
                Text(
                    text = playLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = { onToggleWatchlist(movie) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CharcoalSurface,
                    contentColor = CrispWhite
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("detail_watchlist_button")
            ) {
                Icon(
                    imageVector = if (isInWatchlist) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isInWatchlist) "Saved in My List" else "Add to My List",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Text(
                text = movie.tagline,
                style = MaterialTheme.typography.titleMedium,
                color = CrispWhite
            )
            Text(
                text = movie.overview,
                style = MaterialTheme.typography.bodyLarge,
                color = MutedSilver
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CharcoalSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateCardBorder, RoundedCornerShape(10.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hd,
                            contentDescription = "Stream Specs",
                            tint = SignalEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${movie.primaryCategory.title} • ${movie.audioLanguage}",
                            style = MaterialTheme.typography.titleSmall,
                            color = CrispWhite
                        )
                    }
                    Text(
                        text = "Cast: ${movie.cast.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSilver
                    )
                    val qualitiesSummary = if (movie.qualityOptions.isNotEmpty()) {
                        movie.qualityOptions.joinToString(" • ") { it.badgeDetail }
                    } else {
                        "Resolving direct HD streams (${movie.videoResolution})..."
                    }
                    Text(
                        text = "Direct Streams: $qualitiesSummary",
                        style = MaterialTheme.typography.labelSmall,
                        color = SignalEmerald
                    )
                }
            }

            if (relatedMovies.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "More Like This",
                    style = MaterialTheme.typography.titleLarge,
                    color = CrispWhite
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    content = {
                        relatedMovies.forEach { related ->
                            MoviePosterCard(
                                movie = related,
                                onMovieClick = onSelectRelatedMovie,
                                onQuickPlayClick = onPlayMovie,
                                modifier = Modifier.width(150.dp)
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
