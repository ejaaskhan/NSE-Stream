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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
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
import com.example.data.remote.ApiConfig
import com.example.model.MovieItem
import com.example.model.StreamQualityOption
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.GraphiteElevated
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.NseCrimson
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SlateCardBorder
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Full-screen Media3 ExoPlayer for NSE STREAM v1.0.1:
 * - Supports valid HLS (`.m3u8`) and MP4 (`.mp4`) streams with automatic MIME-type detection
 * - Fast-Start Low-Latency Buffering & Software Decoder Fallback
 * - Dedicated Screen Rotation Toggle (`Portrait ⇄ Landscape`) with zero-crop `RESIZE_MODE_FIT`
 * - Clear Stream Unavailable / Playback Error Overlay with Retry, Switch Quality, and Back buttons
 *   (prevents blank screens and infinite loading spinners)
 */
@OptIn(UnstableApi::class)
@Composable
fun YouTubeExoPlayerScreen(
    movie: MovieItem,
    streamUrl: String,
    initialPositionMs: Long,
    isResolvingStream: Boolean,
    streamErrorMessage: String?,
    playbackAttemptId: Int,
    onRetryStream: () -> Unit,
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
                        streamFormat = if (streamUrl.contains(".m3u8", ignoreCase = true)) "HLS" else "MP4"
                    )
                )
            } else {
                emptyList()
            }
        }
    }

    var selectedQuality by remember(movie.id, playbackAttemptId) {
        mutableStateOf(
            qualityOptions.firstOrNull() ?: StreamQualityOption(
                qualityLabel = "Auto",
                badgeDetail = "Stream",
                streamUrl = "",
                streamFormat = "MP4"
            )
        )
    }
    var showQualityMenu by remember { mutableStateOf(false) }
    var currentlyLoadedUrl by remember(movie.id, playbackAttemptId) { mutableStateOf("") }
    var playbackErrorMessage by remember(movie.id, playbackAttemptId) { mutableStateOf<String?>(null) }

    var isPlaying by remember(movie.id, playbackAttemptId) { mutableStateOf(false) }
    var isBuffering by remember(movie.id, playbackAttemptId) { mutableStateOf(true) }
    var isEnded by remember(movie.id, playbackAttemptId) { mutableStateOf(false) }
    var currentPositionMs by remember(movie.id) { mutableLongStateOf(initialPositionMs) }
    var durationMs by remember(movie.id) { mutableLongStateOf(0L) }
    var bufferedFraction by remember(movie.id, playbackAttemptId) { mutableFloatStateOf(0f) }

    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubSliderPositionMs by remember { mutableFloatStateOf(0f) }

    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTick by remember { mutableIntStateOf(0) }
    var isRotatedLandscape by remember { mutableStateOf(false) }

    var seekFeedbackSide by remember { mutableStateOf<String?>(null) }
    var seekFeedbackSeconds by remember { mutableIntStateOf(10) }

    var detectedVideoWidth by remember(movie.id) { mutableIntStateOf(1920) }
    var detectedVideoHeight by remember(movie.id) { mutableIntStateOf(1080) }
    var fallbackAttemptIndex by remember(movie.id, playbackAttemptId) { mutableIntStateOf(0) }
    var appliedInitialResume by remember(movie.id, playbackAttemptId) { mutableStateOf(false) }

    val exoPlayer = remember(movie.id) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(ApiConfig.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(15_000)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,
                50_000,
                1_000,
                2_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

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

    fun buildMediaItem(url: String): MediaItem {
        val builder = MediaItem.Builder().setUri(url)
        val lower = url.lowercase(Locale.US)
        when {
            lower.contains(".m3u8") -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            lower.contains(".mp4") -> builder.setMimeType(MimeTypes.VIDEO_MP4)
        }
        return builder.build()
    }

    fun prepareStreamOption(option: StreamQualityOption, seekPositionMs: Long) {
        if (option.streamUrl.isBlank()) return
        selectedQuality = option
        currentlyLoadedUrl = option.streamUrl
        playbackErrorMessage = null
        isBuffering = true
        isEnded = false
        exoPlayer.stop()
        exoPlayer.setMediaItem(buildMediaItem(option.streamUrl))
        if (seekPositionMs > 0L) {
            exoPlayer.seekTo(seekPositionMs)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // Load resolved stream URL cleanly for the active movie & attempt
    LaunchedEffect(movie.id, playbackAttemptId, streamUrl, qualityOptions, isResolvingStream, streamErrorMessage) {
        if (streamErrorMessage != null) {
            isBuffering = false
            exoPlayer.stop()
            return@LaunchedEffect
        }
        if (isResolvingStream) {
            isBuffering = true
            return@LaunchedEffect
        }
        if (currentlyLoadedUrl.isBlank()) {
            val firstOption = qualityOptions.firstOrNull { it.streamUrl.isNotBlank() }
            val targetUrl = firstOption?.streamUrl?.ifBlank { streamUrl } ?: streamUrl
            if (targetUrl.isNotBlank()) {
                val optionToPlay = firstOption ?: StreamQualityOption(
                    qualityLabel = movie.videoResolution.ifBlank { "HD" },
                    badgeDetail = movie.audioLanguage,
                    streamUrl = targetUrl,
                    streamFormat = if (targetUrl.contains(".m3u8", ignoreCase = true)) "HLS" else "MP4"
                )
                val resumePos = if (!appliedInitialResume && initialPositionMs > 0L) {
                    appliedInitialResume = true
                    initialPositionMs
                } else {
                    0L
                }
                prepareStreamOption(optionToPlay, resumePos)
            } else {
                // Resolution finished with no stream URL -> prevent infinite spinner!
                isBuffering = false
                playbackErrorMessage = "No playable video stream was found for \"${movie.title}\"."
            }
        } else if (qualityOptions.isNotEmpty()) {
            val matching = qualityOptions.firstOrNull { it.streamUrl == currentlyLoadedUrl }
            if (matching != null) {
                selectedQuality = matching
            }
        }
    }

    // Safety timeout guard: prevent infinite loading/buffering if a stream silently stalls forever
    LaunchedEffect(currentlyLoadedUrl, isBuffering, isResolvingStream, playbackErrorMessage, streamErrorMessage) {
        if ((isBuffering || isResolvingStream) && playbackErrorMessage == null && streamErrorMessage == null) {
            delay(25_000L)
            if ((isBuffering || isResolvingStream) && !isPlaying && playbackErrorMessage == null && streamErrorMessage == null) {
                isBuffering = false
                exoPlayer.pause()
                playbackErrorMessage = "Video took too long to load. Tap Retry or try switching quality."
            }
        }
    }

    LaunchedEffect(initialPositionMs, currentlyLoadedUrl) {
        if (!appliedInitialResume && initialPositionMs > 2_000L && currentlyLoadedUrl.isNotBlank() && exoPlayer.currentPosition < 2_000L) {
            exoPlayer.seekTo(initialPositionMs)
            appliedInitialResume = true
        }
    }

    fun switchQuality(option: StreamQualityOption) {
        showQualityMenu = false
        if (option.streamUrl.isNotBlank()) {
            val savedPos = exoPlayer.currentPosition.coerceAtLeast(0L)
            prepareStreamOption(option, savedPos)
        }
        lastInteractionTick++
    }

    DisposableEffect(exoPlayer, qualityOptions, playbackAttemptId) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                        isEnded = false
                    }
                    Player.STATE_READY -> {
                        isBuffering = false
                        isEnded = false
                        playbackErrorMessage = null
                        durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    }
                    Player.STATE_ENDED -> {
                        isBuffering = false
                        isEnded = true
                    }
                    Player.STATE_IDLE -> {
                        isBuffering = false
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    isBuffering = false
                    playbackErrorMessage = null
                }
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
                    val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    prepareStreamOption(nextOption, pos)
                } else {
                    isBuffering = false
                    isPlaying = false
                    controlsVisible = true
                    val codeName = error.errorCodeName.replace("ERROR_CODE_", "").replace("_", " ").lowercase(Locale.US)
                    playbackErrorMessage = "Unable to play this stream ($codeName). Tap Retry or choose another quality."
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

    fun exitPlayerSafely() {
        if (isRotatedLandscape) {
            toggleScreenRotation()
        } else {
            onClosePlayer(
                exoPlayer.currentPosition.coerceAtLeast(0L),
                exoPlayer.duration.coerceAtLeast(0L)
            )
        }
    }

    BackHandler { exitPlayerSafely() }

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

    val activeError = streamErrorMessage ?: playbackErrorMessage

    LaunchedEffect(controlsVisible, isPlaying, isUserScrubbing, showQualityMenu, activeError, lastInteractionTick) {
        if (controlsVisible && isPlaying && !isUserScrubbing && !showQualityMenu && activeError == null && currentlyLoadedUrl.isNotBlank()) {
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
        if (activeError != null) return
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("youtube_exoplayer_screen"),
        contentAlignment = Alignment.Center
    ) {
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
            // 1. TextureView-backed PlayerView with RESIZE_MODE_FIT (zero crop, original ratio)
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

            // 2. Gesture Layer (when no blocking error overlay is showing)
            if (activeError == null) {
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
            }

            // 3. Player HUD & Error Recovery Overlay
            AnimatedVisibility(
                visible = controlsVisible || !isPlaying || isBuffering || isResolvingStream || activeError != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ObsidianBlack.copy(alpha = 0.85f),
                                    if (activeError != null) ObsidianBlack.copy(alpha = 0.78f) else Color.Transparent,
                                    ObsidianBlack.copy(alpha = 0.88f)
                                )
                            )
                        )
                        .let { mod ->
                            if (needsCanvasRotation) mod.padding(horizontal = 20.dp, vertical = 12.dp)
                            else mod.windowInsetsPadding(WindowInsets.safeDrawing)
                        }
                ) {
                    // Top Bar: Back, Title, Rotate, Quality Switcher, Original Ratio
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
                                onClick = { exitPlayerSafely() },
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

                    // Center: Error Recovery Panel OR Transport Controls
                    if (activeError != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = CharcoalSurface.copy(alpha = 0.96f),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 24.dp)
                                .border(1.dp, NseCrimson.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                .testTag("player_error_overlay")
                        ) {
                            Column(
                                modifier = Modifier.padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Stream Error",
                                    tint = NseCrimson,
                                    modifier = Modifier.size(42.dp)
                                )
                                Text(
                                    text = "Stream Unavailable",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = CrispWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = activeError,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSilver,
                                    textAlign = TextAlign.Center
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            playbackErrorMessage = null
                                            fallbackAttemptIndex = 0
                                            if (currentlyLoadedUrl.isNotBlank() && streamErrorMessage == null) {
                                                prepareStreamOption(selectedQuality, exoPlayer.currentPosition.coerceAtLeast(0L))
                                            } else {
                                                onRetryStream()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NseCrimson,
                                            contentColor = CrispWhite
                                        ),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("player_retry_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Retry", fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { exitPlayerSafely() },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = GraphiteElevated,
                                            contentColor = CrispWhite
                                        ),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("player_error_back_button")
                                    ) {
                                        Text("Go Back")
                                    }
                                }

                                if (qualityOptions.size > 1) {
                                    Text(
                                        text = "Switch Stream Quality:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MutedSilver,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        qualityOptions.forEachIndexed { idx, opt ->
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = if (opt.streamUrl == selectedQuality.streamUrl) NseCrimson.copy(alpha = 0.25f) else GraphiteElevated,
                                                modifier = Modifier
                                                    .border(
                                                        1.dp,
                                                        if (opt.streamUrl == selectedQuality.streamUrl) NseCrimson else SlateCardBorder,
                                                        RoundedCornerShape(50)
                                                    )
                                                    .clip(RoundedCornerShape(50))
                                                    .clickable { switchQuality(opt) }
                                                    .testTag("player_error_quality_$idx")
                                            ) {
                                                Text(
                                                    text = "${opt.qualityLabel} (${opt.streamFormat})",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = CrispWhite,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
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
                                            modifier = Modifier
                                                .size(68.dp)
                                                .testTag("player_buffering_indicator")
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
                                    text = "Connecting to stream...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CrispWhite
                                )
                            } else if (isBuffering) {
                                Text(
                                    text = "Buffering ${selectedQuality.qualityLabel}...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MutedSilver
                                )
                            }
                        }
                    }

                    // Bottom Bar: Scrubber, Timecode, Quality, Rotate & Fullscreen
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
                            enabled = activeError == null && durationMs > 0L,
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
