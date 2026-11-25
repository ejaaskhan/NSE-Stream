package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.BebasNeueFontFamily
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.GraphiteElevated
import com.example.ui.theme.MontserratFontFamily
import com.example.ui.theme.NseCrimson
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SplashGoldAmber
import com.example.ui.theme.SplashGoldLight
import com.example.ui.theme.SplashSunsetOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Premium dark-cinema Splash Screen for NSE Stream.
 *
 * - Preserves the existing NSE STREAM emblem, brand name, and Obsidian-Crimson palette.
 * - Displays "A solution provided by Dev. E" centered below the app name in a Warm Gold
 *   to Sunset Orange gradient inside a subtle rounded pill border.
 * - Smooth fade-in and scale animation with soft atmospheric glow, responsive across
 *   all Android screen sizes and orientations.
 */
@Composable
fun NseSplashScreen(
    onSplashFinished: () -> Unit,
    durationMs: Long = 2_200L,
    modifier: Modifier = Modifier
) {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.92f) }
    val glowAlpha = remember { Animatable(0f) }
    val pillAlpha = remember { Animatable(0f) }
    val pillOffsetY = remember { Animatable(12f) }
    val progressFraction = remember { Animatable(0f) }

    LaunchedEffect(durationMs) {
        launch {
            logoAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
            )
        }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
            )
        }
        launch {
            glowAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(220L)
            launch {
                pillAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing)
                )
            }
            launch {
                pillOffsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing)
                )
            }
        }
        launch {
            progressFraction.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = (durationMs - 150L).coerceAtLeast(300L).toInt(),
                    easing = LinearEasing
                )
            )
        }
        delay(durationMs)
        onSplashFinished()
    }

    val goldOrangeTextBrush = remember {
        Brush.horizontalGradient(
            colors = listOf(
                SplashGoldLight,
                SplashGoldAmber,
                SplashSunsetOrange
            )
        )
    }

    val goldOrangeBorderBrush = remember {
        Brush.horizontalGradient(
            colors = listOf(
                SplashGoldLight.copy(alpha = 0.55f),
                SplashGoldAmber.copy(alpha = 0.45f),
                SplashSunsetOrange.copy(alpha = 0.60f)
            )
        )
    }

    val subtitleText = stringResource(id = R.string.splash_subtitle)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .drawBehind {
                val center = Offset(size.width * 0.5f, size.height * 0.44f)
                val maxRadius = size.minDimension * 0.72f
                val currentGlow = glowAlpha.value

                // Subtle deep crimson theater bloom behind NSE logo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NseCrimson.copy(alpha = 0.22f * currentGlow),
                            NseCrimson.copy(alpha = 0.06f * currentGlow),
                            Color.Transparent
                        ),
                        center = center,
                        radius = maxRadius
                    ),
                    radius = maxRadius,
                    center = center
                )

                // Restrained warm golden ambient glow near the subtitle pill
                val pillCenter = Offset(size.width * 0.5f, size.height * 0.57f)
                val pillRadius = size.minDimension * 0.48f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SplashGoldAmber.copy(alpha = 0.11f * currentGlow),
                            SplashSunsetOrange.copy(alpha = 0.04f * currentGlow),
                            Color.Transparent
                        ),
                        center = pillCenter,
                        radius = pillRadius
                    ),
                    radius = pillRadius,
                    center = pillCenter
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("nse_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        val isCompactHeight = maxHeight < 520.dp
        val isWideScreen = maxWidth >= 600.dp

        val logoSize = when {
            isCompactHeight -> 78.dp
            isWideScreen -> 112.dp
            else -> 96.dp
        }
        val titleFontSize = when {
            isCompactHeight -> 36.sp
            isWideScreen -> 52.sp
            else -> 44.sp
        }
        val subtitleFontSize = when {
            isCompactHeight -> 12.sp
            isWideScreen -> 15.sp
            else -> 13.5.sp
        }
        val sectionSpacing = if (isCompactHeight) 14.dp else 20.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Existing NSE Stream emblem with smooth fade-in & scale
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        alpha = logoAlpha.value
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                    }
                    .size(logoSize)
                    .clip(RoundedCornerShape(22.dp))
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                NseCrimson.copy(alpha = 0.9f),
                                NseCrimson.copy(alpha = 0.35f),
                                SplashGoldAmber.copy(alpha = 0.45f)
                            )
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .background(CharcoalSurface)
                    .testTag("splash_logo_emblem"),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_nse_logo_icon_1794136871384),
                    contentDescription = "NSE Stream Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(sectionSpacing))

            // Existing NSE STREAM brand wordmark
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = logoAlpha.value
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                    }
                    .testTag("splash_brand_title")
            ) {
                Text(
                    text = "NSE",
                    fontFamily = BebasNeueFontFamily,
                    fontSize = titleFontSize,
                    color = NseCrimson,
                    letterSpacing = 2.8.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "STREAM",
                    fontFamily = BebasNeueFontFamily,
                    fontSize = titleFontSize,
                    color = CrispWhite,
                    letterSpacing = 2.8.sp
                )
            }

            Spacer(modifier = Modifier.height(if (isCompactHeight) 12.dp else 18.dp))

            // Elegant centered Warm Gold -> Sunset Orange gradient subtitle inside rounded pill
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        alpha = pillAlpha.value
                        translationY = pillOffsetY.value.dp.toPx()
                    }
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                CharcoalSurface.copy(alpha = 0.88f),
                                GraphiteElevated.copy(alpha = 0.78f),
                                CharcoalSurface.copy(alpha = 0.88f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = goldOrangeBorderBrush,
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 20.dp, vertical = 9.dp)
                    .testTag("splash_subtitle_pill"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = subtitleText,
                    style = TextStyle(
                        brush = goldOrangeTextBrush,
                        fontFamily = MontserratFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = subtitleFontSize,
                        letterSpacing = 0.6.sp,
                        textAlign = TextAlign.Center
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("splash_subtitle_text")
                )
            }
        }

        // Minimalist bottom cinematic loading indicator synced with preload window
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isCompactHeight) 20.dp else 40.dp)
                .width(120.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .background(GraphiteElevated.copy(alpha = 0.7f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction.value.coerceIn(0f, 1f))
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(goldOrangeTextBrush)
            )
        }
    }
}
