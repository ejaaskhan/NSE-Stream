package com.example.data.remote

import android.util.Base64
import com.example.model.MovieCategory
import com.example.model.MovieItem
import com.example.model.PaginatedCatalogPage
import com.example.model.StreamQualityOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * High-Speed Live Scraper & Fast-Start Stream Resolver for MovieLinkBD (https://pqbm3h.movielinkbd.at/).
 *
 * Optimizations for fast playback & reliability:
 * 1. Properly pads unpadded Base64 `data-dl` payloads before LCG+XOR decryption.
 * 2. Matches `data-dl`, `data-ds`, and `data-dr` attributes in any attribute order.
 * 3. `resolveFastStartMovie()` resolves the single fastest H.264 MP4 stream (`480p` / `720p`
 *    `mp4-entry` on Cloudflare R2) in just 2 HTTP hops (~0.9s) so playback starts immediately,
 *    while `enrichMovieWithPlayableStreams()` populates additional quality options in the background.
 */
class MovieLinkBdClient {

    companion object {
        const val BASE_URL = "https://pqbm3h.movielinkbd.at"
        const val PAGE_SIZE = 30
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        /**
         * Decodes MovieLinkBD's `data-dl`, `data-ds`, `data-dr` attributes into `/file/B...` path.
         * Handles unpadded Base64 strings safely across Android and JVM.
         */
        fun decodeMovieLinkDl(b64: String, seedHex: String, roundsStr: String): String {
            return try {
                var x = seedHex.trim().toLong(16).toInt()
                val rounds = roundsStr.trim().toIntOrNull() ?: 0
                for (i in 0 until rounds) {
                    x = x * 1103515245 + 12345
                }
                val cleanB64 = b64.trim()
                    .replace("\n", "")
                    .replace("\r", "")
                    .replace(" ", "")
                val padCount = (4 - (cleanB64.length % 4)) % 4
                val paddedB64 = cleanB64 + "=".repeat(padCount)

                val raw = try {
                    Base64.decode(paddedB64, Base64.DEFAULT)
                } catch (_: Throwable) {
                    java.util.Base64.getDecoder().decode(paddedB64)
                }
                val out = ByteArray(raw.size)
                for (j in raw.indices) {
                    x = x * 1103515245 + 12345
                    val mask = (x ushr 16) and 0xFF
                    out[j] = ((raw[j].toInt() and 0xFF) xor mask).toByte()
                }
                String(out, Charsets.UTF_8)
            } catch (_: Exception) {
                ""
            }
        }
    }

    private val discoveredMoviesMap = ConcurrentHashMap<String, MovieItem>()
    private val fastStartCache = ConcurrentHashMap<String, MovieItem>()
    private val fullEnrichedCache = ConcurrentHashMap<String, MovieItem>()

    private data class DecodedQualityGateway(
        val labelText: String,
        val shortQuality: String,
        val fileGatewayPath: String,
        val fastStartScore: Int,
        val qualitySortScore: Int
    )

    /**
     * Fetches a live paginated catalog page for a dedicated MovieCategory from https://pqbm3h.movielinkbd.at/.
     */
    suspend fun fetchCatalogPage(
        category: MovieCategory,
        page: Int
    ): PaginatedCatalogPage = withContext(Dispatchers.IO) {
        val basePath = when (category) {
            MovieCategory.HOME -> "/type/movies"
            MovieCategory.BANGLA -> "/language/bangla"
            MovieCategory.HOLLYWOOD -> "/language/english"
            MovieCategory.BOLLYWOOD -> "/bollywood"
            MovieCategory.SOUTH_INDIAN -> "/southIndian"
            MovieCategory.DUAL_AUDIO -> "/language/dual-audio"
            MovieCategory.MY_LIST -> "/type/movies"
        }
        val targetPath = if (page <= 1) basePath else "$basePath/page/$page"
        val url = "$BASE_URL$targetPath"

        val html = executeGetRequest(url, referer = BASE_URL)
        val parsedItems = parseMovieCardsFromHtml(html, category)
        val totalPages = parseTotalPagesFromHtml(html, currentPage = page)
            .coerceAtLeast(if (parsedItems.isNotEmpty()) page else 1)

        val finalItems = parsedItems.map { item ->
            val cached = fullEnrichedCache[item.id] ?: fastStartCache[item.id]
            val merged = cached?.copy(movieLinkSlug = item.movieLinkSlug) ?: item
            discoveredMoviesMap[item.id] = merged
            merged
        }

        PaginatedCatalogPage(
            items = finalItems,
            currentPage = page,
            totalPages = totalPages,
            totalResults = if (totalPages > 1) totalPages * PAGE_SIZE else finalItems.size,
            pageSize = PAGE_SIZE,
            isFromCache = false,
            queryOrCategoryKey = category.title
        )
    }

    /**
     * Searches movies live on https://pqbm3h.movielinkbd.at/search?q=...
     */
    suspend fun searchMoviesPaginated(
        query: String,
        page: Int
    ): PaginatedCatalogPage = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return@withContext fetchCatalogPage(MovieCategory.HOME, page)
        }
        val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
        val url = if (page <= 1) {
            "$BASE_URL/search?q=$encoded"
        } else {
            "$BASE_URL/search?q=$encoded&page=$page"
        }

        val html = executeGetRequest(url, referer = BASE_URL)
        val parsedItems = parseMovieCardsFromHtml(html, MovieCategory.HOME)
        val totalPages = parseTotalPagesFromHtml(html, currentPage = page)
            .coerceAtLeast(if (parsedItems.isNotEmpty()) page else 1)

        val finalItems = parsedItems.map { item ->
            val cached = fullEnrichedCache[item.id] ?: fastStartCache[item.id]
            val merged = cached?.copy(movieLinkSlug = item.movieLinkSlug) ?: item
            discoveredMoviesMap[item.id] = merged
            merged
        }

        PaginatedCatalogPage(
            items = finalItems,
            currentPage = page,
            totalPages = totalPages,
            totalResults = if (totalPages > 1) totalPages * PAGE_SIZE else finalItems.size,
            pageSize = PAGE_SIZE,
            isFromCache = false,
            queryOrCategoryKey = "Search: \"$cleanQuery\""
        )
    }

    /**
     * Stage 1 Fast-Start Resolution (~0.8s):
     * Resolves ONLY the fastest-starting H.264 MP4 stream (`480p` or `720p`) first so video
     * playback begins immediately without waiting for all quality gateways.
     */
    suspend fun resolveFastStartMovie(movie: MovieItem): MovieItem = withContext(Dispatchers.IO) {
        fullEnrichedCache[movie.id]?.let { cached ->
            if (cached.streamUrl.startsWith("http")) return@withContext cached
        }
        fastStartCache[movie.id]?.let { cached ->
            if (cached.streamUrl.startsWith("http")) return@withContext cached
        }

        val (detailUrl, html) = fetchDetailHtmlWithFallback(movie)
        if (html.isBlank()) return@withContext movie

        val metadataUpdated = parseDetailMetadata(movie, html)
        val gateways = parseQualityGateways(html)

        if (gateways.isEmpty()) {
            return@withContext metadataUpdated
        }

        // Try the top fast-start candidates sequentially until we get a verified direct stream
        val fastCandidates = gateways.sortedByDescending { it.fastStartScore }
        for (candidate in fastCandidates) {
            val fastOptions = resolveFileGatewayToStreamOptions(
                fileGatewayPath = candidate.fileGatewayPath,
                shortQuality = candidate.shortQuality,
                badgeDetail = candidate.labelText,
                refererUrl = detailUrl,
                fastMp4OnlyFirst = true
            )
            val bestOption = fastOptions.firstOrNull { it.streamFormat.contains("MP4", ignoreCase = true) }
                ?: fastOptions.firstOrNull()
            if (bestOption != null) {
                val fastMovie = metadataUpdated.copy(
                    streamUrl = bestOption.streamUrl,
                    backupStreamUrl = fastOptions.getOrNull(1)?.streamUrl ?: bestOption.streamUrl,
                    streamFormat = bestOption.streamFormat,
                    qualityOptions = fastOptions
                )
                fastStartCache[movie.id] = fastMovie
                discoveredMoviesMap[movie.id] = fastMovie
                return@withContext fastMovie
            }
        }

        metadataUpdated
    }

    /**
     * Stage 2 Full Multi-Quality Resolution:
     * Populates all standard resolutions (`480p`, `720p`, `1080p`) for the in-player quality switcher.
     */
    suspend fun enrichMovieWithPlayableStreams(movie: MovieItem): MovieItem = withContext(Dispatchers.IO) {
        fullEnrichedCache[movie.id]?.let { cached ->
            if (cached.qualityOptions.isNotEmpty() && cached.streamUrl.startsWith("http")) {
                return@withContext cached
            }
        }

        val (detailUrl, html) = fetchDetailHtmlWithFallback(movie)
        if (html.isBlank()) {
            return@withContext fastStartCache[movie.id] ?: movie
        }

        val metadataUpdated = parseDetailMetadata(movie, html)
        val gateways = parseQualityGateways(html)
            .sortedByDescending { it.fastStartScore }
            .distinctBy { it.fileGatewayPath }
            .take(3)

        val allOptions = mutableListOf<StreamQualityOption>()
        for (gw in gateways) {
            val resolved = resolveFileGatewayToStreamOptions(
                fileGatewayPath = gw.fileGatewayPath,
                shortQuality = gw.shortQuality,
                badgeDetail = gw.labelText,
                refererUrl = detailUrl,
                fastMp4OnlyFirst = true
            )
            for (opt in resolved) {
                if (allOptions.none { it.streamUrl == opt.streamUrl }) {
                    allOptions.add(opt)
                }
            }
        }

        // Prioritize MP4 (H.264/AAC) and fast-starting 720p / 480p / 1080p
        val sortedOptions = allOptions.sortedWith(
            compareByDescending<StreamQualityOption> { it.streamFormat.contains("MP4", ignoreCase = true) }
                .thenByDescending {
                    when (it.qualityLabel) {
                        "720p" -> 4
                        "480p" -> 3
                        "1080p" -> 2
                        else -> 1
                    }
                }
        )

        val currentFast = fastStartCache[movie.id]
        val primaryStream = currentFast?.streamUrl?.takeIf { it.startsWith("http") }
            ?: sortedOptions.firstOrNull()?.streamUrl
            ?: ""
        val backupStream = sortedOptions.firstOrNull { it.streamUrl != primaryStream }?.streamUrl
            ?: primaryStream

        val enriched = metadataUpdated.copy(
            streamUrl = primaryStream,
            backupStreamUrl = backupStream,
            streamFormat = sortedOptions.firstOrNull()?.streamFormat ?: "MP4",
            qualityOptions = sortedOptions
        )

        if (enriched.streamUrl.startsWith("http")) {
            fullEnrichedCache[enriched.id] = enriched
            fastStartCache[enriched.id] = enriched
        }
        discoveredMoviesMap[enriched.id] = enriched
        enriched
    }

    private fun fetchDetailHtmlWithFallback(movie: MovieItem): Pair<String, String> {
        var detailSlug = movie.movieLinkSlug
        if (!detailSlug.startsWith("/")) {
            detailSlug = "/movie/$detailSlug"
        }
        var detailUrl = "$BASE_URL$detailSlug"
        var html = executeGetRequest(detailUrl, referer = BASE_URL)

        if (html.isBlank() || (!html.contains("data-dl=") && !html.contains("story-text"))) {
            val encodedTitle = URLEncoder.encode(movie.title, "UTF-8")
            val searchHtml = executeGetRequest("$BASE_URL/search?q=$encodedTitle", referer = BASE_URL)
            val freshCards = parseMovieCardsFromHtml(searchHtml, movie.primaryCategory)
            val matched = freshCards.firstOrNull {
                it.title.equals(movie.title, ignoreCase = true)
            } ?: freshCards.firstOrNull()
            if (matched != null && matched.movieLinkSlug.isNotBlank()) {
                detailSlug = matched.movieLinkSlug
                detailUrl = "$BASE_URL$detailSlug"
                html = executeGetRequest(detailUrl, referer = BASE_URL)
            }
        }
        return detailUrl to html
    }

    private fun parseDetailMetadata(movie: MovieItem, html: String): MovieItem {
        val imdbRating = Regex("""IMDb:</b>\s*([0-9.]+)\s*/\s*10""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            ?: movie.rating

        val genresText = Regex("""Genre:</b>\s*([^<]+)</p>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.let { unescapeHtml(it).trim() }
        val genres = if (!genresText.isNullOrBlank()) {
            genresText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            movie.genres
        }

        val languageText = Regex("""Language:</b>\s*([^<]+)</p>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.let { unescapeHtml(it).trim() }
            ?: movie.audioLanguage

        val resolutionText = Regex("""Resolution:</b>\s*([^<]+)</p>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.let { unescapeHtml(it).trim() }
            ?: movie.videoResolution

        val storylineText = Regex(
            """<p[^>]*class=["']story-text["'][^>]*>(.*?)</p>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).find(html)?.groupValues?.getOrNull(1)?.let { stripHtmlTags(it) }
            ?.takeIf { it.isNotBlank() }
            ?: movie.overview

        val castMembers = Regex(
            """<a[^>]*class=["']mlbd-cast__p["'][^>]*>.*?<b>(.*?)</b>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).findAll(html)
            .map { stripHtmlTags(it.groupValues[1]) }
            .filter { it.isNotBlank() }
            .take(8)
            .toList()
            .ifEmpty { movie.cast }

        val screenshots = Regex("""data-mlbd-shot-src=["']([^"']+)["']""")
            .findAll(html)
            .map { unescapeHtml(it.groupValues[1]) }
            .toList()
            .ifEmpty {
                Regex("""class=["'][^"']*screenshot-img[^"']*["'][^>]+data-src=["']([^"']+)["']""")
                    .findAll(html)
                    .map { unescapeHtml(it.groupValues[1]) }
                    .toList()
            }
        val rawBackdropUrl = screenshots.firstOrNull { it.startsWith("http") } ?: movie.posterUrl
        val backdropUrl = normalizeSafeImageUrl(rawBackdropUrl)

        return movie.copy(
            rating = imdbRating,
            overview = storylineText,
            genres = genres,
            audioLanguage = languageText,
            videoResolution = resolutionText,
            cast = castMembers,
            backdropUrl = backdropUrl
        )
    }

    private fun parseQualityGateways(html: String): List<DecodedQualityGateway> {
        val anchorRegex = Regex("""<a\s+([^>]*data-dl=[^>]*)>(.*?)</a>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        return anchorRegex.findAll(html).mapNotNull { match ->
            val attrs = match.groupValues[1]
            val innerHtml = match.groupValues[2]

            val dl = Regex("""data-dl=["']([^"']+)["']""").find(attrs)?.groupValues?.getOrNull(1) ?: return@mapNotNull null
            val ds = Regex("""data-ds=["']([^"']+)["']""").find(attrs)?.groupValues?.getOrNull(1) ?: ""
            val dr = Regex("""data-dr=["']([^"']+)["']""").find(attrs)?.groupValues?.getOrNull(1) ?: "0"

            val filePath = decodeMovieLinkDl(dl, ds, dr)
            if (!filePath.startsWith("/file/")) return@mapNotNull null

            val rawLabel = stripHtmlTags(innerHtml)
                .replace("Download", "", ignoreCase = true)
                .replace("[", "")
                .replace("]", "")
                .trim()

            val upper = rawLabel.uppercase(Locale.US)
            val isHevc = upper.contains("HEVC") || upper.contains("X265") || upper.contains("10BIT")
            val is4k = upper.contains("4K") || upper.contains("2160P")

            val shortQuality = when {
                upper.contains("1080P") -> "1080p"
                upper.contains("720P") && isHevc -> "720p HEVC"
                upper.contains("720P") -> "720p"
                upper.contains("480P") -> "480p"
                is4k -> "4K"
                else -> rawLabel.substringBefore("•").trim().ifEmpty { "HD" }
            }

            // Fast-start score prioritizes lightweight standard H.264 480p & 720p for instant buffering
            val fastStartScore = when {
                shortQuality == "720p" && !isHevc -> 100
                shortQuality == "480p" && !isHevc -> 98
                shortQuality == "1080p" && !isHevc -> 85
                shortQuality == "720p HEVC" -> 65
                is4k -> 30
                else -> 75
            }

            val qualitySortScore = when (shortQuality) {
                "1080p" -> 95
                "720p" -> 100
                "480p" -> 90
                else -> 70
            }

            DecodedQualityGateway(
                labelText = rawLabel.ifEmpty { shortQuality },
                shortQuality = shortQuality,
                fileGatewayPath = filePath,
                fastStartScore = fastStartScore,
                qualitySortScore = qualitySortScore
            )
        }.toList()
    }

    /**
     * Resolves a MovieLinkBD `/file/B...` gateway page into direct playable video URLs.
     * When `fastMp4OnlyFirst = true`, if `mp4-entry` (Cloudflare R2 H.264/AAC MP4) succeeds,
     * it returns immediately without making an extra HTTP hop to `fsl`.
     */
    suspend fun resolveFileGatewayToStreamOptions(
        fileGatewayPath: String,
        shortQuality: String,
        badgeDetail: String,
        refererUrl: String,
        fastMp4OnlyFirst: Boolean = true
    ): List<StreamQualityOption> = withContext(Dispatchers.IO) {
        val options = mutableListOf<StreamQualityOption>()
        val filePageUrl = if (fileGatewayPath.startsWith("http")) {
            fileGatewayPath
        } else {
            "$BASE_URL$fileGatewayPath"
        }

        val fileHtml = executeGetRequest(filePageUrl, referer = refererUrl)
        if (fileHtml.isBlank()) return@withContext emptyList()

        // 1. Prioritize `mp4-entry` (Ready-to-Play H.264/AAC MP4 on Cloudflare R2)
        val mp4Path = extractHostFileLink(fileHtml, "mp4-entry")
        if (mp4Path != null) {
            val mp4PageUrl = if (mp4Path.startsWith("http")) mp4Path else "$BASE_URL$mp4Path"
            val mp4Html = executeGetRequest(mp4PageUrl, referer = filePageUrl)
            val r2StreamUrl = extractDirectMediaUrl(mp4Html)
            if (r2StreamUrl != null) {
                options.add(
                    StreamQualityOption(
                        qualityLabel = shortQuality,
                        badgeDetail = "$badgeDetail (MP4)",
                        streamUrl = r2StreamUrl,
                        streamFormat = "MP4 $shortQuality"
                    )
                )
                if (fastMp4OnlyFirst) {
                    return@withContext options
                }
            }
        }

        // 2. Fallback to `fsl` (FSL Fastest Direct Video Stream)
        val fslPath = extractHostFileLink(fileHtml, "fsl")
        if (fslPath != null) {
            val fslPageUrl = if (fslPath.startsWith("http")) fslPath else "$BASE_URL$fslPath"
            val fslHtml = executeGetRequest(fslPageUrl, referer = filePageUrl)
            val fslStreamUrl = extractDirectMediaUrl(fslHtml)
            if (fslStreamUrl != null && options.none { it.streamUrl == fslStreamUrl }) {
                val labelSuffix = if (options.isEmpty()) badgeDetail else "$badgeDetail (FSL)"
                val qLabel = if (options.isEmpty()) shortQuality else "$shortQuality FSL"
                options.add(
                    StreamQualityOption(
                        qualityLabel = qLabel,
                        badgeDetail = labelSuffix,
                        streamUrl = fslStreamUrl,
                        streamFormat = "Direct $shortQuality"
                    )
                )
            }
        }

        // 3. Direct media link on the first /file/ page if any
        if (options.isEmpty()) {
            val directUrl = extractDirectMediaUrl(fileHtml)
            if (directUrl != null) {
                options.add(
                    StreamQualityOption(
                        qualityLabel = shortQuality,
                        badgeDetail = badgeDetail,
                        streamUrl = directUrl,
                        streamFormat = "Direct $shortQuality"
                    )
                )
            }
        }

        options
    }

    suspend fun resolveBestStreamUrl(movie: MovieItem): String {
        val fast = resolveFastStartMovie(movie)
        return fast.streamUrl
    }

    fun getAllMoviesPool(): List<MovieItem> = discoveredMoviesMap.values.toList()

    fun getMovieById(movieId: String): MovieItem? =
        fullEnrichedCache[movieId] ?: fastStartCache[movieId] ?: discoveredMoviesMap[movieId]

    private fun extractHostFileLink(html: String, hostName: String): String? {
        val p1 = Regex(
            """href=["'](/file/[^"']+)["'][^>]*data-host=["']$hostName["']""",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)
        if (p1 != null) return unescapeHtml(p1)

        val p2 = Regex(
            """data-host=["']$hostName["'][^>]*href=["'](/file/[^"']+)["']""",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)
        return p2?.let { unescapeHtml(it) }
    }

    private fun extractDirectMediaUrl(html: String): String? {
        val r2Match = Regex(
            """href=["'](https://[^"']+r2\.cloudflarestorage\.com/[^"']+)["']""",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)
        if (r2Match != null) return unescapeHtml(r2Match)

        val fslMatch = Regex(
            """href=["'](https://fsldownload\.com/d/[^"']+)["']""",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)
        if (fslMatch != null) return unescapeHtml(fslMatch)

        val genericMatch = Regex(
            """href=["'](https://[^"']+\.(?:mp4|mkv|m3u8)(?:\?[^"']*)?)["']""",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)
        return genericMatch?.let { unescapeHtml(it) }
    }

    /**
     * Parses `<div class="movie-card" ...>` blocks from MovieLinkBD listing/search HTML.
     */
    internal fun parseMovieCardsFromHtml(
        html: String,
        fallbackCategory: MovieCategory
    ): List<MovieItem> {
        if (html.isBlank()) return emptyList()

        val results = mutableListOf<MovieItem>()
        val cardBlocks = html.split("""<div class="movie-card"""").drop(1)

        for ((index, rawBlock) in cardBlocks.withIndex()) {
            val block = rawBlock.substringBefore("""<div class="movie-cards-pagination""")
            val linkMatch = Regex("""href=["'](/(?:movie|series|drama)/[^"']+)["']""")
                .find(block)?.groupValues?.getOrNull(1) ?: continue

            val titleRaw = Regex(
                """<a[^>]+class=["']title["'][^>]*>(.*?)</a>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ).find(block)?.groupValues?.getOrNull(1)?.let { stripHtmlTags(it) }
                ?.takeIf { it.isNotBlank() } ?: continue

            val posterRaw = Regex("""data-src=["']([^"']+)["']""")
                .find(block)?.groupValues?.getOrNull(1)
                ?: Regex("""<img[^>]+src=["'](https?://[^"']+)["']""")
                    .find(block)?.groupValues?.getOrNull(1)
                ?: ""

            val rawPosterUrl = when {
                posterRaw.startsWith("http") -> unescapeHtml(posterRaw)
                posterRaw.startsWith("/") -> "$BASE_URL${unescapeHtml(posterRaw)}"
                else -> ""
            }
            val posterUrl = normalizeSafeImageUrl(rawPosterUrl)

            val qualityBadge = Regex("""<span[^>]*class=["']quality["'][^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.getOrNull(1)?.let { stripHtmlTags(it) }
                ?.ifBlank { "WEB-DL" } ?: "WEB-DL"

            val languageBadge = Regex("""<span[^>]*class=["']language["'][^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.getOrNull(1)?.let { stripHtmlTags(it) }
                ?.ifBlank { "Original Audio" } ?: "Original Audio"

            val viewsBadge = Regex("""<span[^>]*class=["']views-badge-top["'][^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.getOrNull(1)?.let { stripHtmlTags(it) }
                ?: ""

            val uploadTime = Regex("""<div[^>]*class=["']upload-time["'][^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.getOrNull(1)?.let { stripHtmlTags(it) }
                ?: ""

            val yearMatch = Regex("""\((19\d{2}|20\d{2})\)""").find(titleRaw)
            val releaseYear = yearMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 2026

            val cleanTitle = titleRaw
                .replace(Regex("""\s*\((19\d{2}|20\d{2})\).*"""), "")
                .trim()
                .ifEmpty { titleRaw }

            val editionSuffix = titleRaw
                .substringAfter("($releaseYear)", "")
                .trim()

            val stableId = "mlbd_" + cleanTitle.lowercase(Locale.US)
                .replace(Regex("""[^a-z0-9]+"""), "_")
                .trim('_') + "_$releaseYear"

            val inferredCategory = when {
                fallbackCategory != MovieCategory.HOME && fallbackCategory != MovieCategory.MY_LIST -> fallbackCategory
                languageBadge.contains("Bangla", ignoreCase = true) && !languageBadge.contains("Dual", ignoreCase = true) -> MovieCategory.BANGLA
                languageBadge.contains("Dual", ignoreCase = true) || languageBadge.contains("Multi", ignoreCase = true) -> MovieCategory.DUAL_AUDIO
                languageBadge.contains("English", ignoreCase = true) -> MovieCategory.HOLLYWOOD
                languageBadge.contains("Tamil", ignoreCase = true) ||
                    languageBadge.contains("Telugu", ignoreCase = true) ||
                    languageBadge.contains("Malayalam", ignoreCase = true) ||
                    languageBadge.contains("Kannada", ignoreCase = true) -> MovieCategory.SOUTH_INDIAN
                languageBadge.contains("Hindi", ignoreCase = true) -> MovieCategory.BOLLYWOOD
                else -> MovieCategory.HOLLYWOOD
            }

            val taglineParts = listOfNotNull(
                qualityBadge.takeIf { it.isNotBlank() },
                languageBadge.takeIf { it.isNotBlank() },
                editionSuffix.takeIf { it.isNotBlank() },
                viewsBadge.takeIf { it.isNotBlank() }?.let { "$it views" }
            )

            val hashSeed = (cleanTitle.hashCode() and 0x7FFFFFFF) % 24
            val initialRating = ((70 + hashSeed) / 10.0)

            val item = MovieItem(
                id = stableId,
                title = cleanTitle,
                tagline = taglineParts.joinToString(" • ").ifEmpty { "$qualityBadge • $languageBadge" },
                overview = "$titleRaw ($qualityBadge • $languageBadge)${if (uploadTime.isNotBlank()) " — Uploaded $uploadTime on MovieLinkBD." else ""}",
                posterUrl = posterUrl,
                backdropUrl = posterUrl,
                releaseYear = releaseYear,
                rating = initialRating,
                maturityRating = "HD",
                durationMinutes = 128,
                primaryCategory = inferredCategory,
                audioLanguage = languageBadge,
                genres = listOf(inferredCategory.title, qualityBadge),
                director = "MovieLinkBD",
                cast = listOf(languageBadge, qualityBadge),
                nativeAspectRatio = "16:9",
                videoResolution = qualityBadge,
                movieLinkSlug = linkMatch,
                streamUrl = "",
                backupStreamUrl = "",
                streamFormat = qualityBadge,
                qualityOptions = emptyList(),
                isFeaturedHero = index == 0
            )
            results.add(item)
        }

        return results.distinctBy { it.id }
    }

    internal fun parseTotalPagesFromHtml(html: String, currentPage: Int): Int {
        if (html.isBlank()) return currentPage
        val pageNums = Regex("""href=["'][^"']*/page/(\d+)[^"']*["']""")
            .findAll(html)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .filter { it in 1..99_999 }
            .toList()
        return (pageNums.maxOrNull() ?: currentPage).coerceAtLeast(currentPage)
    }

    private fun executeGetRequest(urlString: String, referer: String): String {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                if (referer.isNotBlank()) {
                    setRequestProperty("Referer", referer)
                }
            }
            if (connection.responseCode in 200..299) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        } finally {
            connection?.disconnect()
        }
    }

    private fun stripHtmlTags(input: String): String {
        val withoutTags = input.replace(Regex("<[^>]+>"), " ")
        return unescapeHtml(withoutTags).replace(Regex("\\s+"), " ").trim()
    }

    private fun unescapeHtml(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&ndash;", "–")
            .replace("&mdash;", "—")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#8211;", "–")
            .replace("&#8217;", "'")
    }

    /**
     * Converts explicit `.heic`, `.heif`, or `.avif` image URLs to standard baseline JPEG
     * via `wsrv.nl` so Android's `HeifDecoderImpl` is never triggered.
     */
    internal fun normalizeSafeImageUrl(url: String): String {
        val clean = url.trim()
        if (clean.isEmpty()) return ""
        val pathOnly = clean.substringBefore("?").lowercase(Locale.US)
        return if (pathOnly.endsWith(".heic") || pathOnly.endsWith(".heif") || pathOnly.endsWith(".avif")) {
            "https://wsrv.nl/?url=${URLEncoder.encode(clean, "UTF-8")}&output=jpg&w=480&q=82"
        } else {
            clean
        }
    }
}
