package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.ApiConfig
import com.example.data.remote.NseMovieApiClient
import com.example.model.ApiResult
import com.example.model.CategorySectionState
import com.example.model.MovieCategory
import com.example.model.MovieItem
import com.example.model.StreamQualityOption
import com.example.ui.components.CategorySectionRow
import com.example.ui.components.MovieDetailScreen
import com.example.ui.components.NetworkErrorCard
import com.example.ui.components.NseBrandLogo
import com.example.ui.components.NseSplashScreen
import com.example.ui.components.NseTopNavigationBar
import com.example.ui.components.ServerSettingsDialog
import com.example.ui.components.YouTubeExoPlayerScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NseStreamRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @After
    fun tearDown() {
        ApiConfig.setBaseUrlForTesting(null)
    }

    private fun sampleMovie(
        id: String = "night_of_the_living_dead",
        title: String = "Night of the Living Dead",
        withQualities: Boolean = true
    ): MovieItem = MovieItem(
        id = id,
        title = title,
        tagline = "English • Classic Cinema • 1968",
        overview = "A group of people hide from bloodthirsty ghouls in a rural Pennsylvania farmhouse.",
        posterUrl = "https://archive.org/services/img/$id",
        backdropUrl = "https://archive.org/services/img/$id",
        releaseYear = 1968,
        rating = 8.4,
        maturityRating = "HD",
        durationMinutes = 96,
        primaryCategory = MovieCategory.HOLLYWOOD,
        audioLanguage = "English",
        genres = listOf("Horror", "Classic"),
        director = "George A. Romero",
        cast = listOf("Duane Jones", "Judith O'Dea"),
        nativeAspectRatio = "16:9",
        videoResolution = "720p",
        movieLinkSlug = id,
        streamUrl = if (withQualities) "https://archive.org/download/$id/movie_720p.mp4" else "",
        backupStreamUrl = if (withQualities) "https://archive.org/download/$id/movie_480p.mp4" else "",
        streamFormat = "MP4",
        qualityOptions = if (withQualities) {
            listOf(
                StreamQualityOption(
                    qualityLabel = "HLS Auto",
                    badgeDetail = "HLS Auto • HLS",
                    streamUrl = "https://archive.org/download/$id/master.m3u8",
                    streamFormat = "HLS"
                ),
                StreamQualityOption(
                    qualityLabel = "720p",
                    badgeDetail = "720p • 620 MB • MP4",
                    streamUrl = "https://archive.org/download/$id/movie_720p.mp4",
                    streamFormat = "MP4"
                )
            )
        } else {
            emptyList()
        }
    )

    @Test
    fun verifySplashScreenDisplaysSubtitlePillWithoutCineShorts() {
        var finished = false
        composeTestRule.setContent {
            MyApplicationTheme {
                NseSplashScreen(
                    onSplashFinished = { finished = true },
                    durationMs = 2_200L
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("nse_splash_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_logo_emblem").assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_brand_title").assertIsDisplayed()
        composeTestRule.onNodeWithText("NSE").assertIsDisplayed()
        composeTestRule.onNodeWithText("STREAM").assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_subtitle_pill").assertIsDisplayed()
        composeTestRule.onNodeWithText("A solution provided by Dev. E").assertIsDisplayed()
        composeTestRule.onNodeWithText("CineShorts", substring = true, ignoreCase = true)
            .assertDoesNotExist()
        composeTestRule.onNodeWithText("MOVIELINKBD", substring = true, ignoreCase = true)
            .assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(2_500L)
        composeTestRule.waitForIdle()
        assertTrue("Expected splash screen to complete after duration", finished)
    }

    @Test
    fun verifyBrandLogoRemovesMovielinkbdSubtitle() {
        composeTestRule.setContent {
            MyApplicationTheme {
                NseBrandLogo()
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("NSE").assertIsDisplayed()
        composeTestRule.onNodeWithText("STREAM").assertIsDisplayed()
        composeTestRule.onNodeWithText("MOVIELINKBD CINEMA", substring = true, ignoreCase = true)
            .assertDoesNotExist()
        composeTestRule.onNodeWithText("MOVIELINKBD", substring = true, ignoreCase = true)
            .assertDoesNotExist()
    }

    @Test
    fun verifyTopNavigationBarCategoriesSearchAndServerSettings() {
        var searchExpanded = false
        var openedServerSettings = false
        var selectedCat: MovieCategory? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                NseTopNavigationBar(
                    activeCategory = MovieCategory.HOME,
                    searchQuery = "",
                    isSearchExpanded = false,
                    watchlistCount = 2,
                    onToggleSearch = { searchExpanded = true },
                    onOpenServerSettings = { openedServerSettings = true },
                    onSearchQueryChange = {},
                    onSearchSubmit = {},
                    onClearSearch = {},
                    onSelectCategory = { selectedCat = it }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("nse_brand_logo").assertIsDisplayed()
        composeTestRule.onNodeWithText("MOVIELINKBD", substring = true, ignoreCase = true)
            .assertDoesNotExist()

        composeTestRule.onNodeWithTag("search_toggle_button").performClick()
        composeTestRule.onNodeWithTag("server_settings_button").performClick()
        composeTestRule.onNodeWithTag("category_chip_${MovieCategory.BANGLA.id}").performClick()
        composeTestRule.waitForIdle()

        assertTrue(searchExpanded)
        assertTrue(openedServerSettings)
        assertEquals(MovieCategory.BANGLA, selectedCat)
    }

    @Test
    fun verifyCategorySectionDisplaysErrorAndRetryAction() {
        var retried = false
        val failedSection = CategorySectionState(
            category = MovieCategory.BANGLA,
            items = emptyList(),
            isLoadingInitial = false,
            errorMessage = "Cannot reach server. Check your internet connection."
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                CategorySectionRow(
                    section = failedSection,
                    onSeeAllClick = {},
                    onMovieClick = {},
                    onQuickPlayClick = {},
                    onLoadMore = {},
                    onRetrySection = { retried = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Bangla Movies").assertIsDisplayed()
        composeTestRule.onNodeWithTag("network_error_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("retry_section_${MovieCategory.BANGLA.id}").performClick()
        composeTestRule.waitForIdle()

        assertTrue(retried)
    }

    @Test
    fun verifyMovieDetailScreenPlayAndStreamErrorRetry() {
        val movie = sampleMovie()
        var playedMovieId: String? = null
        var retriedStreams = false

        composeTestRule.setContent {
            MyApplicationTheme {
                MovieDetailScreen(
                    movie = movie,
                    isInWatchlist = false,
                    savedProgressMs = 0L,
                    isLoadingStreams = false,
                    streamErrorMessage = "Streaming server is temporarily unavailable.",
                    relatedMovies = emptyList(),
                    onPlayMovie = { playedMovieId = it.id },
                    onRetryStreams = { retriedStreams = true },
                    onToggleWatchlist = {},
                    onSelectRelatedMovie = {},
                    onClose = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("detail_play_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("detail_retry_streams_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(movie.id, playedMovieId)
        assertTrue(retriedStreams)
    }

    @Test
    fun verifyServerSettingsDialogValidationAndPersistence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ApiConfig.resetToDefault(context)
        assertEquals("https://archive.org", ApiConfig.getActiveBaseUrl(context))

        val saveValid = ApiConfig.setCustomBaseUrl(context, "https://cinema.example.org/")
        assertTrue(saveValid.isSuccess)
        assertEquals("https://cinema.example.org", ApiConfig.getActiveBaseUrl(context))

        val saveInvalid = ApiConfig.setCustomBaseUrl(context, "bad-url")
        assertTrue(saveInvalid.isFailure)

        val reset = ApiConfig.resetToDefault(context)
        assertEquals("https://archive.org", reset)

        var resetClicked = false
        composeTestRule.setContent {
            MyApplicationTheme {
                ServerSettingsDialog(
                    currentBaseUrl = "https://archive.org",
                    onDismiss = {},
                    onSaveBaseUrl = { null },
                    onResetDefault = { resetClicked = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Server Configuration").assertIsDisplayed()
        composeTestRule.onNodeWithText("NSE STREAM v1.0.1 (Build 2)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("server_reset_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(resetClicked)
    }

    @Test
    fun verifyPlayerErrorOverlayShowsRetrySwitchQualityAndBack() {
        val movie = sampleMovie()
        var retried = false
        var closed = false

        composeTestRule.setContent {
            MyApplicationTheme {
                YouTubeExoPlayerScreen(
                    movie = movie,
                    streamUrl = "",
                    initialPositionMs = 0L,
                    isResolvingStream = false,
                    streamErrorMessage = "No playable HLS (.m3u8) or MP4 stream was found.",
                    playbackAttemptId = 1,
                    onRetryStream = { retried = true },
                    onSaveProgress = { _, _ -> },
                    onClosePlayer = { _, _ -> closed = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("player_error_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithText("Stream Unavailable").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_error_quality_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_error_quality_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_rotate_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("player_retry_button").performClick()
        composeTestRule.onNodeWithTag("player_error_back_button").performClick()
        composeTestRule.waitForIdle()

        assertTrue(retried)
        assertTrue(closed)
    }

    @Test
    fun verifyApiClientJsonParsingAndErrorHandling() = runBlocking {
        val client = NseMovieApiClient(baseUrlProvider = { "https://archive.org" })

        // 1. Archive search JSON with mixed String & Array metadata fields
        val archiveJson = """
            {
              "response": {
                "numFound": 2,
                "docs": [
                  {
                    "identifier": "demo_film_1",
                    "title": ["Classic Film One 720p"],
                    "description": ["<p>First line &amp; second line</p>"],
                    "year": "1968",
                    "language": ["eng"],
                    "creator": "Classic Studio",
                    "subject": ["Drama", "Feature Film"],
                    "downloads": 25000,
                    "runtime": "1:35:00"
                  }
                ]
              }
            }
        """.trimIndent()
        val parsedArchive = client.parseArchiveSearchJson(
            jsonString = archiveJson,
            baseUrl = "https://archive.org",
            category = MovieCategory.HOLLYWOOD,
            page = 1,
            pageSize = 24,
            label = "Hollywood Movies"
        )
        assertTrue(parsedArchive is ApiResult.Success)
        val archivePage = (parsedArchive as ApiResult.Success).data
        assertEquals(1, archivePage.items.size)
        assertEquals("Classic Film One", archivePage.items.first().title)
        assertEquals(95, archivePage.items.first().durationMinutes)

        // 2. Archive metadata JSON with HLS (.m3u8) and MP4 streams
        val metadataJson = """
            {
              "metadata": {
                "description": "Full restored public-domain print.",
                "creator": "Archive Films"
              },
              "files": [
                {"name": "film_master.m3u8", "format": "HLS"},
                {"name": "film_720p.mp4", "format": "h.264", "height": "720", "size": "524288000"},
                {"name": "film_480p.mp4", "format": "MPEG4", "height": "480", "size": "262144000"}
              ]
            }
        """.trimIndent()
        val parsedMeta = client.parseArchiveMetadataJson(
            jsonString = metadataJson,
            baseUrl = "https://archive.org",
            movie = archivePage.items.first(),
            identifier = "demo_film_1"
        )
        assertTrue(parsedMeta is ApiResult.Success)
        val resolved = (parsedMeta as ApiResult.Success).data
        assertEquals("HLS", resolved.streamFormat)
        assertTrue(resolved.streamUrl.endsWith(".m3u8"))
        assertEquals(3, resolved.qualityOptions.size)

        // 3. Custom REST JSON catalog with HLS + MP4 qualities
        val customRestJson = """
            {
              "items": [
                {
                  "id": "custom_101",
                  "title": "Authorized Test Cinema",
                  "releaseYear": 2025,
                  "rating": 8.7,
                  "posterUrl": "/posters/custom_101.jpg",
                  "streams": [
                    {"quality": "1080p", "format": "HLS", "url": "/hls/101/index.m3u8"},
                    {"quality": "720p", "format": "MP4", "url": "/mp4/101/720p.mp4"}
                  ]
                }
              ],
              "totalPages": 3,
              "totalResults": 60
            }
        """.trimIndent()
        val parsedCustom = client.parseCustomRestCatalogJson(
            jsonString = customRestJson,
            baseUrl = "https://api.example.com",
            category = MovieCategory.BANGLA,
            page = 1,
            pageSize = 24,
            label = "Bangla Movies"
        )
        assertTrue(parsedCustom is ApiResult.Success)
        val customMovie = (parsedCustom as ApiResult.Success).data.items.first()
        assertEquals("https://api.example.com/posters/custom_101.jpg", customMovie.posterUrl)
        assertEquals("https://api.example.com/hls/101/index.m3u8", customMovie.streamUrl)
        assertEquals(2, customMovie.qualityOptions.size)

        // 4. Malformed JSON must fail cleanly without crashing
        val malformed = client.parseCustomRestCatalogJson(
            jsonString = "<html>502 Bad Gateway</html>",
            baseUrl = "https://api.example.com",
            category = MovieCategory.BANGLA,
            page = 1,
            pageSize = 24,
            label = "Bangla Movies"
        )
        assertTrue(malformed is ApiResult.Failure)

        // 5. Invalid base URL must return ApiResult.Failure cleanly
        val invalidClient = NseMovieApiClient(baseUrlProvider = { "not-valid-url" })
        val invalidResult = invalidClient.fetchCatalogPage(MovieCategory.HOLLYWOOD, page = 1)
        assertTrue(invalidResult is ApiResult.Failure)
    }

    @Test
    fun verifyLiveAuthorizedCategoriesSearchAndPlayableStream() = runBlocking {
        val client = NseMovieApiClient(baseUrlProvider = { "https://archive.org" })

        val hollywood = client.fetchCatalogPage(MovieCategory.HOLLYWOOD, page = 1, pageSize = 6)
        assertTrue("Expected Hollywood catalog success: $hollywood", hollywood is ApiResult.Success)
        val hollywoodItems = (hollywood as ApiResult.Success).data.items
        assertTrue("Expected non-empty Hollywood movies", hollywoodItems.isNotEmpty())

        val bangla = client.fetchCatalogPage(MovieCategory.BANGLA, page = 1, pageSize = 6)
        assertTrue("Expected Bangla catalog success: $bangla", bangla is ApiResult.Success)
        assertTrue("Expected non-empty Bangla movies", (bangla as ApiResult.Success).data.items.isNotEmpty())

        val bollywood = client.fetchCatalogPage(MovieCategory.BOLLYWOOD, page = 1, pageSize = 6)
        assertTrue("Expected Bollywood catalog success: $bollywood", bollywood is ApiResult.Success)
        assertTrue("Expected non-empty Bollywood movies", (bollywood as ApiResult.Success).data.items.isNotEmpty())

        val search = client.searchMoviesPaginated("Night of the Living Dead", page = 1, pageSize = 6)
        assertTrue("Expected Search success: $search", search is ApiResult.Success)
        assertTrue("Expected Search results", (search as ApiResult.Success).data.items.isNotEmpty())

        val playableResult = client.resolvePlayableMovie(hollywoodItems.first(), forceRefresh = true)
        assertTrue("Expected playable stream resolution: $playableResult", playableResult is ApiResult.Success)
        val playable = (playableResult as ApiResult.Success).data
        assertTrue(
            "Expected valid https stream URL, got: ${playable.streamUrl}",
            playable.streamUrl.startsWith("https://") &&
                (playable.streamUrl.contains(".mp4", ignoreCase = true) ||
                    playable.streamUrl.contains(".m3u8", ignoreCase = true))
        )
        assertTrue("Expected at least one quality option", playable.qualityOptions.isNotEmpty())
    }
}
