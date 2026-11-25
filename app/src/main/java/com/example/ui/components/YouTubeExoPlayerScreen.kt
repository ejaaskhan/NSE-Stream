package com.example.ui.components

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.remote.MovieLinkBdClient
import com.example.model.MovieItem
import com.example.model.StreamQualityOption
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.NseCrimson
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SignalEmerald
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Full-screen Media3 ExoPlayer with:
 * - Fast-Start Low-Latency Buffering (`bufferForPlaybackMs = 800`) & Decoder Fallback
 * - Dedicated Screen Rotation Toggle (`Portrait ⇄ Landscape`) with guaranteed 90° rotation
 *   support even in fixed-orientation streaming emulators
 * - Zero-Crop Original Video Ratio (`AspectRatioFrameLayout.RESIZE_MODE_FIT`)
 * - In-Player Quality Switcher & Auto-Fallback
 */
@OptIn(UnstableApi::class)
@Composable
fun YouTubeExoPlayerScreen(
    movie: MovieItem,
    streamUrl: String,
    initialPositionMs: Long,
    onSaveProgress: (positionMs: Long, durationMs: Long) -> Unit,
    onClosePlayer: (finalPositionMs: Long, durationMs: Long) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val qualityOptions = remember(movie.id, movie.qualityOptions, streamUrl) {
        movie.qualityOptions.ifEmpty {
            if (streamUrl.isNotBlank()) {
                listOf(
                    StreamQualityOption(
                        qualityLabel = movie.videoResolution.ifBlank { "HD" },
                        badgeDetail = "${movie.videoResolution.ifBlank { "HD" }} • ${movie.audioLanguage}",
                        streamUrl = streamUrl,
                        streamFormat = movie.streamFormat
                    )
                )
            } else {
                emptyList()
            }
        }
    }

    var selectedQuality by remember(movie.id) {
        mutableStateOf(
            qualityOptions.firstOrNull() ?: StreamQualityOption(
                qualityLabel = "Auto HD",
                badgeDetail = "Fast-Start Stream",
                streamUrl = "",
                streamFormat = "MP4"
            )
        )
    }
    var showQualityMenu by remember { mutableStateOf(false) }
    var currentlyLoadedUrl by remember(movie.id) { mutableStateOf("") }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var isEnded by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(initialPositionMs) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedFraction by remember { mutableFloatStateOf(0f) }

    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubSliderPositionMs by remember { mutableFloatStateOf(0f) }

    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTick by remember { mutableIntStateOf(0) }
    var isRotatedLandscape by remember { mutableStateOf(false) }

    var seekFeedbackSide by remember { mutableStateOf<String?>(null) }
    var seekFeedbackSeconds by remember { mutableIntStateOf(10) }

    var detectedVideoWidth by remember { mutableIntStateOf(1920) }
    var detectedVideoHeight by remember { mutableIntStateOf(1080) }
    var fallbackAttemptIndex by remember(movie.id) { mutableIntStateOf(0) }
    var appliedInitialResume by remember(movie.id) { mutableStateOf(false) }

    val exoPlayer = remember(movie.id) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(MovieLinkBdClient.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(12_000)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

        // Fast-start buffer configuration: begins playback as soon as 800ms of video is buffered
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000, // minBufferMs
                50_000, // maxBufferMs
                800,    // bufferForPlaybackMs (fast start!)
                1_500   // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Enable software decoder fallback so all H.264/HEVC/AAC/AC3 profiles play smoothly
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    // Load stream URL immediately when resolved; do not restart if background quality enrichment adds more options
    LaunchedEffect(streamUrl, qualityOptions) {
        if (currentlyLoadedUrl.isBlank()) {
            val firstOption = qualityOptions.firstOrNull()
            if (firstOption != null) {
                selectedQuality = firstOption
            }
            val targetUrl = firstOption?.streamUrl?.ifBlank { streamUrl } ?: streamUrl
            if (targetUrl.isNotBlank()) {
                currentlyLoadedUrl = targetUrl
                isBuffering = true
                exoPlayer.setMediaItem(MediaItem.fromUri(targetUrl))
                if (!appliedInitialResume && initialPositionMs > 0L) {
                    exoPlayer.seekTo(initialPositionMs)
                    appliedInitialResume = true
                }
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
        } else if (qualityOptions.isNotEmpty()) {
            val matching = qualityOptions.firstOrNull { it.streamUrl == currentlyLoadedUrl }
            if (matching != null) {
                selectedQuality = matching
            }
        }
    }

    LaunchedEffect(initialPositionMs) {
        if (!appliedInitialResume && initialPositionMs > 2_000L && currentlyLoadedUrl.isNotBlank() && exoPlayer.currentPosition < 2_000L) {
            exoPlayer.seekTo(initialPositionMs)
            appliedInitialResume = true
        }
    }

    fun switchQuality(option: StreamQualityOption) {
        selectedQuality = option
        showQualityMenu = false
        if (option.streamUrl.isNotBlank() && option.streamUrl != currentlyLoadedUrl) {
            val savedPos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val wasPlaying = exoPlayer.playWhenReady
            currentlyLoadedUrl = option.streamUrl
            isBuffering = true
            exoPlayer.setMediaItem(MediaItem.fromUri(option.streamUrl))
            exoPlayer.seekTo(savedPos)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = wasPlaying
        }
        lastInteractionTick++
    }

    DisposableEffect(exoPlayer, qualityOptions) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                isEnded = playbackState == Player.STATE_ENDED
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    detectedVideoWidth = videoSize.width
                    detectedVideoHeight = videoSize.height
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val alternatives = qualityOptions.filter {
                    it.streamUrl.isNotBlank() && it.streamUrl != currentlyLoadedUrl
                }
                if (fallbackAttemptIndex < alternatives.size) {
                    val nextOption = alternatives[fallbackAttemptIndex]
                    fallbackAttemptIndex++
                    selectedQuality = nextOption
                    currentlyLoadedUrl = nextOption.streamUrl
                    val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    exoPlayer.setMediaItem(MediaItem.fromUri(nextOption.streamUrl))
                    exoPlayer.seekTo(pos)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exeDisposeCleanup(
                exoPlayer = exoPlayer,
                listener = listener,
                activity = activity,
                onSaveProgress = onSaveProgress
            )
        }
    }

    fun toggleScreenRotation() {
        isRotatedLandscape = !isRotatedLandscape
        activity?.let { act ->
            val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isRotatedLandscape) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        lastInteractionTick++
    }

    BackHandler {
        if (isRotatedLandscape) {
            toggleScreenRotation()
        } else {
            onClosePlayer(
                exoPlayer.currentPosition.coerceAtLeast(0L),
                exoPlayer.duration.coerceAtLeast(0L)
            )
        }
    }

    LaunchedEffect(exoPlayer) {
        var tickCount = 0
        while (true) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration
            if (dur > 0L) {
                durationMs = dur
                val bufPos = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                bufferedFraction = (bufPos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            }
            if (!isUserScrubbing) {
                scrubSliderPositionMs = currentPositionMs.toFloat()
            }
            tickCount++
            if (tickCount % 20 == 0 && durationMs > 0L) {
                onSaveProgress(currentPositionMs, durationMs)
            }
            delay(250L)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, isUserScrubbing, showQualityMenu, lastInteractionTick) {
        if (controlsVisible && isPlaying && !isUserScrubbing && !showQualityMenu && currentlyLoadedUrl.isNotBlank()) {
            delay(3500L)
            controlsVisible = false
        }
    }

    LaunchedEffect(seekFeedbackSide, lastInteractionTick) {
        if (seekFeedbackSide != null) {
            delay(650L)
            seekFeedbackSide = null
        }
    }

    fun seekByDeltaMs(deltaMs: Long) {
        val target = if (durationMs > 0L) {
            (exoPlayer.currentPosition + deltaMs).coerceIn(0L, durationMs)
        } else {
            (exoPlayer.currentPosition + deltaMs).coerceAtLeast(0L)
        }
        exoPlayer.seekTo(target)
        currentPositionMs = target
        scrubSliderPositionMs = target.toFloat()
        lastInteractionTick++
    }

    val isResolvingStream = currentlyLoadedUrl.isBlank()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("youtube_exoplayer_screen"),
        contentAlignment = Alignment.Center
    ) {
        // If user toggled Landscape rotation and the host window is still in Portrait bounds,
        // apply a 90° layout transform with swapped dimensions so rotation works in every environment.
        val needsCanvasRotation = isRotatedLandscape && maxWidth < maxHeight
        val stageModifier = if (needsCanvasRotation) {
            Modifier
                .requiredSize(width = maxHeight, height = maxWidth)
                .graphicsLayer { rotationZ = 90f }
        } else {
            Modifier.fillMaxSize()
        }

        Box(
            modifier = stageModifier.background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // 1. TextureView-backed PlayerView with RESIZE_MODE_FIT (preserves original ratio without cropping)
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(
                        R.layout.exo_player_texture_view,
                        null,
                        false
                    ) as PlayerView).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        keepScreenOn = true
                    }
                },
                update = { playerView ->
                    if (playerView.player !== exoPlayer) {
                        playerView.player = exoPlayer
                    }
                    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. YouTube Gesture Layer: Single-tap toggles HUD; Double-tap left/right seeks -10s / +10s
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    controlsVisible = !controlsVisible
                                    lastInteractionTick++
                                },
                                onDoubleTap = {
                                    seekByDeltaMs(-10_000L)
                                    seekFeedbackSide = "LEFT"
                                    seekFeedbackSeconds = 10
                                }
                            )
                        }
                        .testTag("player_left_gesture_zone"),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = seekFeedbackSide == "LEFT",
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(topEnd = 80.dp, bottomEnd = 80.dp, topStart = 16.dp, bottomStart = 16.dp),
                            color = CrispWhite.copy(alpha = 0.22f),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Rewind 10 seconds",
                                    tint = CrispWhite,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "-$seekFeedbackSeconds seconds",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CrispWhite
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(0.30f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    controlsVisible = !controlsVisible
                                    lastInteractionTick++
                                }
                            )
                        }
                )

                Box(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    controlsVisible = !controlsVisible
                                    lastInteractionTick++
                                },
                                onDoubleTap = {
                                    seekByDeltaMs(10_000L)
                                    seekFeedbackSide = "RIGHT"
                                    seekFeedbackSeconds = 10
                                }
                            )
                        }
                        .testTag("player_right_gesture_zone"),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = seekFeedbackSide == "RIGHT",
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 80.dp, bottomStart = 80.dp, topEnd = 16.dp, bottomEnd = 16.dp),
                            color = CrispWhite.copy(alpha = 0.22f),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Forward 10 seconds",
                                    tint = CrispWhite,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "+$seekFeedbackSeconds seconds",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CrispWhite
                                )
                            }
                        }
                    }
                }
            }

            // 3. YouTube-Style HUD Overlay
            AnimatedVisibility(
                visible = controlsVisible || !isPlaying || isBuffering || isResolvingStream,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ObsidianBlack.copy(alpha = 0.80f),
                                    Color.Transparent,
                                    ObsidianBlack.copy(alpha = 0.85f)
                                )
                            )
                        )
                        .let { mod ->
                            if (needsCanvasRotation) mod.padding(horizontal = 20.dp, vertical = 12.dp)
                            else mod.windowInsetsPadding(WindowInsets.safeDrawing)
                        }
                ) {
                    // Top Bar: Back Button, Title, Rotate Screen Button, Quality Switcher, Original Ratio Badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = {
                                    if (isRotatedLandscape) {
                                        toggleScreenRotation()
                                    } else {
                                        onClosePlayer(
                                            exoPlayer.currentPosition.coerceAtLeast(0L),
                                            exoPlayer.duration.coerceAtLeast(0L)
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(CharcoalSurface.copy(alpha = 0.7f))
                                    .testTag("player_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Exit Player",
                                    tint = CrispWhite
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = movie.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CrispWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${movie.releaseYear} • ${movie.audioLanguage}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedSilver,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Dedicated Rotate Screen Button (Portrait ⇄ Landscape)
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isRotatedLandscape) SignalEmerald else CharcoalSurface.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .border(
                                        1.dp,
                                        if (isRotatedLandscape) SignalEmerald else CrispWhite.copy(alpha = 0.35f),
                                        RoundedCornerShape(50)
                                    )
                                    .clip(RoundedCornerShape(50))
                                    .clickable { toggleScreenRotation() }
                                    .testTag("player_rotate_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ScreenRotation,
                                        contentDescription = "Rotate Screen",
                                        tint = if (isRotatedLandscape) ObsidianBlack else CrispWhite,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = if (isRotatedLandscape) "Portrait" else "Rotate",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isRotatedLandscape) ObsidianBlack else CrispWhite,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // In-Player Quality Switcher
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = NseCrimson,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable(enabled = qualityOptions.isNotEmpty()) {
                                            showQualityMenu = true
                                            lastInteractionTick++
                                        }
                                        .testTag("player_quality_switcher_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.HighQuality,
                                            contentDescription = "Select Stream Quality",
                                            tint = CrispWhite,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = selectedQuality.qualityLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = CrispWhite,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showQualityMenu,
                                    onDismissRequest = { showQualityMenu = false },
                                    modifier = Modifier.background(CharcoalSurface)
                                ) {
                                    qualityOptions.forEach { option ->
                                        val isCurrent = option.streamUrl == selectedQuality.streamUrl
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${option.qualityLabel} — ${option.badgeDetail}",
                                                    color = if (isCurrent) SignalEmerald else CrispWhite,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = { switchQuality(option) }
                                        )
                                    }
                                }
                            }

                            // Original Video Ratio Badge
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = CharcoalSurface.copy(alpha = 0.85f),
                                modifier = Modifier.border(
                                    1.dp,
                                    SignalEmerald.copy(alpha = 0.5f),
                                    RoundedCornerShape(50)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AspectRatio,
                                        contentDescription = "Original Ratio Fit",
                                        tint = SignalEmerald,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${detectedVideoWidth}×${detectedVideoHeight}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CrispWhite
                                    )
                                }
                            }
                        }
                    }

                    // Center Transport Controls
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(36.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { seekByDeltaMs(-10_000L) },
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(ObsidianBlack.copy(alpha = 0.55f))
                                    .testTag("player_rewind_10_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Rewind 10 seconds",
                                    tint = CrispWhite,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Box(
                                modifier = Modifier.size(72.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isBuffering || isResolvingStream) {
                                    CircularProgressIndicator(
                                        color = NseCrimson,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(68.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (isEnded) {
                                            exoPlayer.seekTo(0L)
                                            exoPlayer.playWhenReady = true
                                        } else if (exoPlayer.isPlaying) {
                                            exoPlayer.pause()
                                        } else {
                                            exoPlayer.play()
                                        }
                                        lastInteractionTick++
                                    },
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(NseCrimson.copy(alpha = 0.90f))
                                        .testTag("player_play_pause_button")
                                ) {
                                    Icon(
                                        imageVector = when {
                                            isEnded -> Icons.Default.Replay
                                            isPlaying -> Icons.Default.Pause
                                            else -> Icons.Default.PlayArrow
                                        },
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = CrispWhite,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { seekByDeltaMs(10_000L) },
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(ObsidianBlack.copy(alpha = 0.55f))
                                    .testTag("player_forward_10_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Forward 10 seconds",
                                    tint = CrispWhite,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        if (isResolvingStream) {
                            Text(
                                text = "Starting fast HD stream...",
                                style = MaterialTheme.typography.labelMedium,
                                color = CrispWhite
                            )
                        }
                    }

                    // Bottom Bar: YouTube Red Scrub Bar, Timecodes, Rotate & Fullscreen Buttons
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Slider(
                            value = if (durationMs > 0L) {
                                scrubSliderPositionMs.coerceIn(0f, durationMs.toFloat())
                            } else {
                                0f
                            },
                            onValueChange = { newVal ->
                                isUserScrubbing = true
                                scrubSliderPositionMs = newVal
                                lastInteractionTick++
                            },
                            onValueChangeFinished = {
                                val targetMs = scrubSliderPositionMs.toLong()
                                exoPlayer.seekTo(targetMs)
                                currentPositionMs = targetMs
                                isUserScrubbing = false
                                lastInteractionTick++
                            },
                            valueRange = 0f..(durationMs.coerceAtLeast(1L).toFloat()),
                            colors = SliderDefaults.colors(
                                thumbColor = NseCrimson,
                                activeTrackColor = NseCrimson,
                                inactiveTrackColor = CrispWhite.copy(alpha = 0.30f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .testTag("player_scrub_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val displayedPos = if (isUserScrubbing) {
                                    scrubSliderPositionMs.toLong()
                                } else {
                                    currentPositionMs
                                }
                                Text(
                                    text = "${formatDurationMs(displayedPos)} / ${formatDurationMs(durationMs)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CrispWhite,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "• ${selectedQuality.qualityLabel} (${(bufferedFraction * 100).toInt()}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedSilver
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { toggleScreenRotation() },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("player_bottom_rotate_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ScreenRotation,
                                        contentDescription = "Rotate Screen",
                                        tint = if (isRotatedLandscape) SignalEmerald else CrispWhite
                                    )
                                }
                                IconButton(
                                    onClick = { toggleScreenRotation() },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("player_fullscreen_button")
                                ) {
                                    Icon(
                                        imageVector = if (isRotatedLandscape) {
                                            Icons.Default.FullscreenExit
                                        } else {
                                            Icons.Default.Fullscreen
                                        },
                                        contentDescription = if (isRotatedLandscape) {
                                            "Exit Fullscreen Landscape"
                                        } else {
                                            "Fullscreen Landscape"
                                        },
                                        tint = CrispWhite
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun exeDisposeCleanup(
    exoPlayer: ExoPlayer,
    listener: Player.Listener,
    activity: Activity?,
    onSaveProgress: (positionMs: Long, durationMs: Long) -> Unit
) {
    val finalPos = exoPlayer.currentPosition.coerceAtLeast(0L)
    val totalDur = exoPlayer.duration.coerceAtLeast(0L)
    onSaveProgress(finalPos, totalDur)
    exoPlayer.removeListener(listener)
    exoPlayer.release()

    activity?.let { act ->
        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    }
}

fun formatDurationMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
