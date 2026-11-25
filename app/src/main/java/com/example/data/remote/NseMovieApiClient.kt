package com.example.data.remote

import com.example.model.ApiResult
import com.example.model.MovieCategory
import com.example.model.MovieItem
import com.example.model.PaginatedCatalogPage
import com.example.model.StreamQualityOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/**
 * Centralized, asynchronous JSON Movie & Streaming API Client for NSE STREAM v1.0.1.
 *
 * - Default catalog: Legitimate public-domain & open-archive feature films via Internet Archive's
 *   official JSON Advanced Search API (`/advancedsearch.php`) and Metadata API (`/metadata/{id}`),
 *   providing verified HLS (`.m3u8`) and MP4 (`.mp4`) streams.
 * - Custom server mode: Automatically switches to standard REST JSON endpoints
 *   (`/api/movies`, `/api/search`, `/api/movies/{id}`) whenever the user configures a custom
 *   API base URL in Server Settings.
 * - Strict error propagation via `ApiResult.Failure` — never fakes data when a server is unavailable.
 */
class NseMovieApiClient(
    private val baseUrlProvider: () -> String = { ApiConfig.getActiveBaseUrl() },
    private val httpClient: OkHttpClient = defaultOkHttpClient()
) {

    companion object {
        const val PAGE_SIZE = ApiConfig.PAGE_SIZE

        fun defaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(ApiConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(ApiConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(ApiConfig.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }

        internal fun encodePathSegments(path: String): String {
            return path.split("/").joinToString("/") { segment ->
                URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
            }
        }
    }

    private val discoveredMovies = ConcurrentHashMap<String, MovieItem>()
    private val resolvedStreamCache = ConcurrentHashMap<String, MovieItem>()

    fun clearCaches() {
        discoveredMovies.clear()
        resolvedStreamCache.clear()
    }

    fun getAllMoviesPool(): List<MovieItem> = discoveredMovies.values.toList()

    fun getMovieById(movieId: String): MovieItem? =
        resolvedStreamCache[movieId] ?: discoveredMovies[movieId]

    /**
     * Fetches a paginated category catalog from the active configured server.
     */
    suspend fun fetchCatalogPage(
        category: MovieCategory,
        page: Int,
        pageSize: Int = PAGE_SIZE
    ): ApiResult<PaginatedCatalogPage> = withContext(Dispatchers.IO) {
        val baseUrl = ApiConfig.normalizeBaseUrl(baseUrlProvider())
        if (!ApiConfig.isValidBaseUrl(baseUrl)) {
            return@withContext ApiResult.Failure(
                message = "Invalid API base URL configured. Open Server Settings to enter a valid URL.",
                isNetworkError = false
            )
        }

        if (ApiConfig.isUsingArchiveCatalog(baseUrl)) {
            fetchArchiveCatalogPage(baseUrl, category, page, pageSize)
        } else {
            fetchCustomRestCatalogPage(baseUrl, category, page, pageSize)
        }
    }

    /**
     * Searches movies live against the active configured server.
     */
    suspend fun searchMoviesPaginated(
        query: String,
        page: Int,
        pageSize: Int = PAGE_SIZE
    ): ApiResult<PaginatedCatalogPage> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return@withContext fetchCatalogPage(MovieCategory.HOLLYWOOD, page, pageSize)
        }

        val baseUrl = ApiConfig.normalizeBaseUrl(baseUrlProvider())
        if (!ApiConfig.isValidBaseUrl(baseUrl)) {
            return@withContext ApiResult.Failure(
                message = "Invalid API base URL configured. Open Server Settings to enter a valid URL.",
                isNetworkError = false
            )
        }

        if (ApiConfig.isUsingArchiveCatalog(baseUrl)) {
            fetchArchiveSearchPage(baseUrl, cleanQuery, page, pageSize)
        } else {
            fetchCustomRestSearchPage(baseUrl, cleanQuery, page, pageSize)
        }
    }

    /**
     * Resolves full movie metadata and valid playable HLS (`.m3u8`) / MP4 streams for the exact
     * selected movie ID.
     */
    suspend fun resolvePlayableMovie(
        movie: MovieItem,
        forceRefresh: Boolean = false
    ): ApiResult<MovieItem> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            resolvedStreamCache[movie.id]?.let { cached ->
                if (isValidStreamUrl(cached.streamUrl)) {
                    return@withContext ApiResult.Success(cached)
                }
            }
            if (isValidStreamUrl(movie.streamUrl) && movie.qualityOptions.isNotEmpty()) {
                resolvedStreamCache[movie.id] = movie
                discoveredMovies[movie.id] = movie
                return@withContext ApiResult.Success(movie)
            }
        }

        val baseUrl = ApiConfig.normalizeBaseUrl(baseUrlProvider())
        if (!ApiConfig.isValidBaseUrl(baseUrl)) {
            return@withContext ApiResult.Failure(
                message = "Invalid server URL configured. Check Server Settings and try again.",
                isNetworkError = false
            )
        }

        val result = if (ApiConfig.isUsingArchiveCatalog(baseUrl)) {
            resolveArchiveMovieStreams(baseUrl, movie)
        } else {
            resolveCustomRestMovieDetail(baseUrl, movie)
        }

        if (result is ApiResult.Success) {
            val resolved = result.data
            if (isValidStreamUrl(resolved.streamUrl)) {
                resolvedStreamCache[resolved.id] = resolved
                discoveredMovies[resolved.id] = resolved
            } else {
                return@withContext ApiResult.Failure(
                    message = "No playable MP4 or HLS stream is currently available for \"${movie.title}\".",
                    isNetworkError = false
                )
            }
        }
        result
    }

    // -------------------------------------------------------------------------
    // Internet Archive Official Public-Domain Film JSON API
    // -------------------------------------------------------------------------

    private fun buildArchiveQueryForCategory(category: MovieCategory): String {
        val exclusions = "AND NOT title:(Trailer OR Promo OR Music OR Song OR Sex OR CapCut OR Lecture) " +
            "AND NOT subject:(adult OR sex OR trailer OR lecture OR sermon OR news OR template)"
        return when (category) {
            MovieCategory.BANGLA ->
                "mediatype:(movies) AND format:(MPEG4) AND " +
                    "(subject:(\"Bengali film\" OR \"Bangla movie\" OR \"Bengali cinema\" OR \"Bangla film\") " +
                    "OR title:(\"Bangla Movie\" OR \"Bengali Movie\" OR \"Bangla Film\")) $exclusions"

            MovieCategory.HOLLYWOOD, MovieCategory.HOME, MovieCategory.MY_LIST ->
                "collection:(feature_films) AND mediatype:(movies) AND format:(MPEG4) " +
                    "AND (language:eng OR language:English) $exclusions"

            MovieCategory.BOLLYWOOD ->
                "mediatype:(movies) AND format:(MPEG4) AND " +
                    "(subject:(\"Hindi film\" OR \"Hindi movie\" OR \"Bollywood\") " +
                    "OR title:(\"Hindi Movie\" OR \"Bollywood Full Movie\")) $exclusions"

            MovieCategory.SOUTH_INDIAN ->
                "mediatype:(movies) AND format:(MPEG4) AND " +
                    "(subject:(\"Tamil film\" OR \"Telugu film\" OR \"Malayalam film\" OR \"Kannada film\" OR \"South Indian\") " +
                    "OR title:(\"Tamil Movie\" OR \"Telugu Movie\" OR \"Malayalam Movie\")) $exclusions"

            MovieCategory.DUAL_AUDIO ->
                "mediatype:(movies) AND format:(MPEG4) AND " +
                    "(collection:(SciFi_Horror) OR collection:(Film_Noir) OR collection:(Comedy_Films) OR title:(\"Dual Audio\")) $exclusions"
        }
    }

    private fun fetchArchiveCatalogPage(
        baseUrl: String,
        category: MovieCategory,
        page: Int,
        pageSize: Int
    ): ApiResult<PaginatedCatalogPage> {
        val query = buildArchiveQueryForCategory(category)
        return executeArchiveSearch(
            baseUrl = baseUrl,
            luceneQuery = query,
            page = page,
            pageSize = pageSize,
            category = category,
            label = category.title
        )
    }

    private fun fetchArchiveSearchPage(
        baseUrl: String,
        userQuery: String,
        page: Int,
        pageSize: Int
    ): ApiResult<PaginatedCatalogPage> {
        val sanitized = userQuery.replace("\"", " ").replace(Regex("[{}\\[\\]^~*?:\\\\]"), " ").trim()
        val lucene = "mediatype:(movies) AND format:(MPEG4) AND (title:($sanitized) OR subject:($sanitized) OR creator:($sanitized)) " +
            "AND NOT subject:(adult OR sex)"
        return executeArchiveSearch(
            baseUrl = baseUrl,
            luceneQuery = lucene,
            page = page,
            pageSize = pageSize,
            category = MovieCategory.HOME,
            label = "Search: \"$userQuery\""
        )
    }

    private fun executeArchiveSearch(
        baseUrl: String,
        luceneQuery: String,
        page: Int,
        pageSize: Int,
        category: MovieCategory,
        label: String
    ): ApiResult<PaginatedCatalogPage> {
        val httpUrl = "$baseUrl/advancedsearch.php".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("q", luceneQuery)
            ?.addQueryParameter("fl[]", "identifier")
            ?.addQueryParameter("fl[]", "title")
            ?.addQueryParameter("fl[]", "description")
            ?.addQueryParameter("fl[]", "year")
            ?.addQueryParameter("fl[]", "date")
            ?.addQueryParameter("fl[]", "language")
            ?.addQueryParameter("fl[]", "creator")
            ?.addQueryParameter("fl[]", "subject")
            ?.addQueryParameter("fl[]", "downloads")
            ?.addQueryParameter("fl[]", "avg_rating")
            ?.addQueryParameter("fl[]", "runtime")
            ?.addQueryParameter("sort[]", "downloads desc")
            ?.addQueryParameter("rows", pageSize.toString())
            ?.addQueryParameter("page", page.coerceAtLeast(1).toString())
            ?.addQueryParameter("output", "json")
            ?.build()
            ?: return ApiResult.Failure("Failed to construct search API endpoint URL.")

        return when (val bodyResult = executeJsonGet(httpUrl.toString())) {
            is ApiResult.Failure -> bodyResult
            is ApiResult.Success -> parseArchiveSearchJson(
                jsonString = bodyResult.data,
                baseUrl = baseUrl,
                category = category,
                page = page,
                pageSize = pageSize,
                label = label
            )
        }
    }

    internal fun parseArchiveSearchJson(
        jsonString: String,
        baseUrl: String,
        category: MovieCategory,
        page: Int,
        pageSize: Int,
        label: String
    ): ApiResult<PaginatedCatalogPage> {
        return try {
            val root = JSONObject(jsonString)
            val responseObj = root.optJSONObject("response")
                ?: return ApiResult.Failure("Unexpected server response format (missing response object).")

            val numFound = responseObj.optInt("numFound", 0).coerceAtLeast(0)
            val docs = responseObj.optJSONArray("docs") ?: JSONArray()
            val items = mutableListOf<MovieItem>()

            for (i in 0 until docs.length()) {
                val doc = docs.optJSONObject(i) ?: continue
                val identifier = doc.optString("identifier", "").trim()
                if (identifier.isEmpty()) continue

                val rawTitle = optFirstString(doc, "title").ifBlank { identifier }
                val cleanTitle = cleanMovieTitle(rawTitle)
                val year = parseReleaseYear(optFirstString(doc, "year"), optFirstString(doc, "date"), rawTitle)
                val description = stripHtmlTags(optFirstString(doc, "description"))
                    .ifBlank { "Public-domain & open-archive cinema feature film available for streaming." }
                val language = formatLanguageLabel(optFirstString(doc, "language"), category)
                val creator = optFirstString(doc, "creator").ifBlank { "Classic Cinema Archive" }
                val subjects = optStringList(doc, "subject")
                    .map { it.trim() }
                    .filter { it.length in 2..28 }
                    .take(3)
                    .ifEmpty { listOf(category.title, "Feature Film") }
                val downloads = doc.optLong("downloads", 0L)
                val avgRating = doc.optDouble("avg_rating", Double.NaN)
                val computedRating = when {
                    !avgRating.isNaN() && avgRating in 1.0..5.0 ->
                        String.format(Locale.US, "%.1f", (avgRating * 1.9).coerceIn(5.5, 9.6)).toDouble()
                    downloads > 50_000 -> 8.6
                    downloads > 15_000 -> 8.1
                    downloads > 3_000 -> 7.7
                    else -> 7.3
                }
                val runtimeMinutes = parseRuntimeMinutes(optFirstString(doc, "runtime"))
                val posterUrl = "$baseUrl/services/img/${encodePathSegments(identifier)}"

                val resolvedCategory = inferCategory(category, language)
                val cached = resolvedStreamCache[identifier]

                val movie = cached ?: MovieItem(
                    id = identifier,
                    title = cleanTitle,
                    tagline = "$language • ${subjects.firstOrNull() ?: "Feature Film"} • $year",
                    overview = description,
                    posterUrl = posterUrl,
                    backdropUrl = posterUrl,
                    releaseYear = year,
                    rating = computedRating,
                    maturityRating = "HD",
                    durationMinutes = runtimeMinutes,
                    primaryCategory = resolvedCategory,
                    audioLanguage = language,
                    genres = subjects,
                    director = creator,
                    cast = listOf(creator, language),
                    nativeAspectRatio = "16:9",
                    videoResolution = "HD",
                    movieLinkSlug = identifier,
                    streamUrl = "",
                    backupStreamUrl = "",
                    streamFormat = "MP4",
                    qualityOptions = emptyList(),
                    isFeaturedHero = i == 0
                )
                discoveredMovies[movie.id] = movie
                items.add(movie)
            }

            val totalPages = if (numFound > 0) {
                ceil(numFound.toDouble() / pageSize.toDouble()).toInt().coerceAtLeast(1)
            } else {
                1
            }

            ApiResult.Success(
                PaginatedCatalogPage(
                    items = items.distinctBy { it.id },
                    currentPage = page,
                    totalPages = totalPages,
                    totalResults = numFound,
                    pageSize = pageSize,
                    isFromCache = false,
                    queryOrCategoryKey = label
                )
            )
        } catch (e: JSONException) {
            ApiResult.Failure(
                message = "Failed to parse movie catalog response from server.",
                isNetworkError = false
            )
        }
    }

    private fun resolveArchiveMovieStreams(
        baseUrl: String,
        movie: MovieItem
    ): ApiResult<MovieItem> {
        val identifier = movie.movieLinkSlug.ifBlank { movie.id }.trim()
        if (identifier.isEmpty()) {
            return ApiResult.Failure("Missing movie identifier for \"${movie.title}\".")
        }
        val metadataUrl = "$baseUrl/metadata/${encodePathSegments(identifier)}"
        return when (val jsonResult = executeJsonGet(metadataUrl)) {
            is ApiResult.Failure -> jsonResult
            is ApiResult.Success -> parseArchiveMetadataJson(
                jsonString = jsonResult.data,
                baseUrl = baseUrl,
                movie = movie,
                identifier = identifier
            )
        }
    }

    internal fun parseArchiveMetadataJson(
        jsonString: String,
        baseUrl: String,
        movie: MovieItem,
        identifier: String
    ): ApiResult<MovieItem> {
        return try {
            val root = JSONObject(jsonString)
            val meta = root.optJSONObject("metadata") ?: JSONObject()
            val files = root.optJSONArray("files") ?: JSONArray()

            val description = stripHtmlTags(optFirstString(meta, "description"))
                .takeIf { it.isNotBlank() } ?: movie.overview
            val director = optFirstString(meta, "creator")
                .takeIf { it.isNotBlank() } ?: movie.director
            val runtimeMinutes = parseRuntimeMinutes(optFirstString(meta, "runtime"))
                .takeIf { it > 0 } ?: movie.durationMinutes

            val options = mutableListOf<StreamQualityOption>()
            var bestBackdrop = movie.backdropUrl

            for (i in 0 until files.length()) {
                val fileObj = files.optJSONObject(i) ?: continue
                val fileName = fileObj.optString("name", "").trim()
                if (fileName.isEmpty()) continue
                val lowerName = fileName.lowercase(Locale.US)
                val formatStr = fileObj.optString("format", "")

                // Capture a wide poster/thumbnail if available
                if ((lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png")) &&
                    !lowerName.contains("_thumb") &&
                    !lowerName.contains(".thumbs/") &&
                    bestBackdrop == movie.posterUrl
                ) {
                    bestBackdrop = "$baseUrl/download/${encodePathSegments(identifier)}/${encodePathSegments(fileName)}"
                }

                val isHls = lowerName.endsWith(".m3u8")
                val isMp4 = lowerName.endsWith(".mp4") && !lowerName.contains("ia_thumb")

                if (!isHls && !isMp4) continue

                val height = fileObj.optString("height", "").toIntOrNull() ?: 0
                val sizeBytes = fileObj.optString("size", "").toLongOrNull() ?: 0L
                val sizeLabel = formatBytesCompact(sizeBytes)
                val streamUrl = "$baseUrl/download/${encodePathSegments(identifier)}/${encodePathSegments(fileName)}"

                val qualityLabel = when {
                    isHls -> "HLS Auto"
                    height >= 1000 || lowerName.contains("1080") -> "1080p"
                    height >= 680 || lowerName.contains("720") -> "720p"
                    height >= 420 || lowerName.contains("480") -> "480p"
                    height >= 320 || lowerName.contains("360") -> "360p"
                    lowerName.contains("512kb") -> "360p"
                    else -> "HD"
                }

                val streamFormat = if (isHls) "HLS" else "MP4"
                val detailParts = listOfNotNull(
                    qualityLabel,
                    sizeLabel.takeIf { it.isNotBlank() },
                    if (formatStr.isNotBlank()) formatStr else streamFormat
                )

                options.add(
                    StreamQualityOption(
                        qualityLabel = qualityLabel,
                        badgeDetail = detailParts.joinToString(" • "),
                        streamUrl = streamUrl,
                        streamFormat = streamFormat
                    )
                )
            }

            // Sort: prefer HLS or standard H.264 720p / 480p / 1080p for fast smooth playback
            val sortedOptions = options
                .distinctBy { it.streamUrl }
                .sortedWith(
                    compareByDescending<StreamQualityOption> {
                        when (it.qualityLabel) {
                            "HLS Auto" -> 6
                            "720p" -> 5
                            "480p" -> 4
                            "1080p" -> 3
                            "HD" -> 2
                            else -> 1
                        }
                    }.thenBy { it.badgeDetail.contains("512Kb", ignoreCase = true) }
                )

            val primary = sortedOptions.firstOrNull()
            if (primary == null) {
                return ApiResult.Failure(
                    message = "No playable HLS (.m3u8) or MP4 stream was found for \"${movie.title}\".",
                    isNetworkError = false
                )
            }

            val backup = sortedOptions.getOrNull(1)?.streamUrl ?: primary.streamUrl

            ApiResult.Success(
                movie.copy(
                    overview = description,
                    director = director,
                    durationMinutes = runtimeMinutes,
                    backdropUrl = bestBackdrop,
                    videoResolution = primary.qualityLabel,
                    streamUrl = primary.streamUrl,
                    backupStreamUrl = backup,
                    streamFormat = primary.streamFormat,
                    qualityOptions = sortedOptions
                )
            )
        } catch (e: JSONException) {
            ApiResult.Failure("Failed to parse video metadata from server.")
        }
    }

    // -------------------------------------------------------------------------
    // Standard Custom JSON REST API Adapter (for user-configured Base URLs)
    // -------------------------------------------------------------------------

    private fun fetchCustomRestCatalogPage(
        baseUrl: String,
        category: MovieCategory,
        page: Int,
        pageSize: Int
    ): ApiResult<PaginatedCatalogPage> {
        val url = "$baseUrl/api/movies".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("category", category.slug)
            ?.addQueryParameter("page", page.coerceAtLeast(1).toString())
            ?.addQueryParameter("limit", pageSize.toString())
            ?.build()
            ?: return ApiResult.Failure("Invalid custom server URL.")

        return when (val body = executeJsonGet(url.toString())) {
            is ApiResult.Failure -> body
            is ApiResult.Success -> parseCustomRestCatalogJson(
                jsonString = body.data,
                baseUrl = baseUrl,
                category = category,
                page = page,
                pageSize = pageSize,
                label = category.title
            )
        }
    }

    private fun fetchCustomRestSearchPage(
        baseUrl: String,
        query: String,
        page: Int,
        pageSize: Int
    ): ApiResult<PaginatedCatalogPage> {
        val url = "$baseUrl/api/search".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("q", query)
            ?.addQueryParameter("page", page.coerceAtLeast(1).toString())
            ?.addQueryParameter("limit", pageSize.toString())
            ?.build()
            ?: return ApiResult.Failure("Invalid custom server search URL.")

        return when (val body = executeJsonGet(url.toString())) {
            is ApiResult.Failure -> body
            is ApiResult.Success -> parseCustomRestCatalogJson(
                jsonString = body.data,
                baseUrl = baseUrl,
                category = MovieCategory.HOME,
                page = page,
                pageSize = pageSize,
                label = "Search: \"$query\""
            )
        }
    }

    private fun resolveCustomRestMovieDetail(
        baseUrl: String,
        movie: MovieItem
    ): ApiResult<MovieItem> {
        if (isValidStreamUrl(movie.streamUrl)) {
            val opts = movie.qualityOptions.ifEmpty {
                listOf(
                    StreamQualityOption(
                        qualityLabel = movie.videoResolution.ifBlank { "HD" },
                        badgeDetail = "${movie.videoResolution.ifBlank { "HD" }} • ${movie.streamFormat}",
                        streamUrl = movie.streamUrl,
                        streamFormat = detectStreamFormat(movie.streamUrl)
                    )
                )
            }
            return ApiResult.Success(movie.copy(qualityOptions = opts))
        }

        val url = "$baseUrl/api/movies/${encodePathSegments(movie.id)}"
        return when (val body = executeJsonGet(url)) {
            is ApiResult.Failure -> body
            is ApiResult.Success -> {
                try {
                    val obj = JSONObject(body.data)
                    val movieJson = obj.optJSONObject("movie")
                        ?: obj.optJSONObject("data")
                        ?: obj
                    val parsed = parseCustomMovieJsonObject(movieJson, baseUrl, movie.primaryCategory, 0)
                        ?: return ApiResult.Failure("Server returned empty movie details for \"${movie.title}\".")
                    ApiResult.Success(
                        movie.copy(
                            overview = parsed.overview.ifBlank { movie.overview },
                            posterUrl = parsed.posterUrl.ifBlank { movie.posterUrl },
                            backdropUrl = parsed.backdropUrl.ifBlank { movie.backdropUrl },
                            streamUrl = parsed.streamUrl,
                            backupStreamUrl = parsed.backupStreamUrl,
                            streamFormat = parsed.streamFormat,
                            qualityOptions = parsed.qualityOptions
                        )
                    )
                } catch (_: JSONException) {
                    ApiResult.Failure("Server returned malformed JSON for movie details.")
                }
            }
        }
    }

    internal fun parseCustomRestCatalogJson(
        jsonString: String,
        baseUrl: String,
        category: MovieCategory,
        page: Int,
        pageSize: Int,
        label: String
    ): ApiResult<PaginatedCatalogPage> {
        return try {
            val trimmed = jsonString.trim()
            val array: JSONArray
            var totalPages = 1
            var totalResults = 0

            if (trimmed.startsWith("[")) {
                array = JSONArray(trimmed)
                totalResults = array.length()
            } else {
                val root = JSONObject(trimmed)
                array = root.optJSONArray("items")
                    ?: root.optJSONArray("movies")
                    ?: root.optJSONArray("results")
                    ?: root.optJSONArray("data")
                    ?: return ApiResult.Failure("Server JSON response does not contain a movie list.")
                totalPages = root.optInt("totalPages", root.optInt("total_pages", 1)).coerceAtLeast(1)
                totalResults = root.optInt("totalResults", root.optInt("total", array.length()))
            }

            val items = mutableListOf<MovieItem>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val parsed = parseCustomMovieJsonObject(obj, baseUrl, category, i) ?: continue
                discoveredMovies[parsed.id] = parsed
                items.add(parsed)
            }

            ApiResult.Success(
                PaginatedCatalogPage(
                    items = items.distinctBy { it.id },
                    currentPage = page,
                    totalPages = totalPages,
                    totalResults = totalResults.coerceAtLeast(items.size),
                    pageSize = pageSize,
                    isFromCache = false,
                    queryOrCategoryKey = label
                )
            )
        } catch (_: JSONException) {
            ApiResult.Failure("Server returned invalid JSON. Verify the API Base URL in Server Settings.")
        }
    }

    private fun parseCustomMovieJsonObject(
        obj: JSONObject,
        baseUrl: String,
        fallbackCategory: MovieCategory,
        index: Int
    ): MovieItem? {
        val id = obj.optString("id", obj.optString("_id", "")).trim()
        val title = obj.optString("title", obj.optString("name", "")).trim()
        if (id.isEmpty() || title.isEmpty()) return null

        val posterUrl = resolveAbsoluteUrl(
            baseUrl,
            obj.optString("posterUrl", obj.optString("poster", obj.optString("thumbnail", "")))
        )
        val backdropUrl = resolveAbsoluteUrl(
            baseUrl,
            obj.optString("backdropUrl", obj.optString("backdrop", posterUrl))
        ).ifBlank { posterUrl }

        val qualities = mutableListOf<StreamQualityOption>()
        val streamsArray = obj.optJSONArray("streams") ?: obj.optJSONArray("qualities")
        if (streamsArray != null) {
            for (j in 0 until streamsArray.length()) {
                val sObj = streamsArray.optJSONObject(j) ?: continue
                val sUrl = resolveAbsoluteUrl(baseUrl, sObj.optString("url", sObj.optString("streamUrl", "")))
                if (isValidStreamUrl(sUrl)) {
                    val qLabel = sObj.optString("quality", sObj.optString("label", "HD")).ifBlank { "HD" }
                    val fmt = sObj.optString("format", detectStreamFormat(sUrl))
                    qualities.add(
                        StreamQualityOption(
                            qualityLabel = qLabel,
                            badgeDetail = "$qLabel • $fmt",
                            streamUrl = sUrl,
                            streamFormat = fmt
                        )
                    )
                }
            }
        }

        val directStream = resolveAbsoluteUrl(
            baseUrl,
            obj.optString("streamUrl", obj.optString("hlsUrl", obj.optString("videoUrl", obj.optString("url", ""))))
        )
        if (isValidStreamUrl(directStream) && qualities.none { it.streamUrl == directStream }) {
            val fmt = detectStreamFormat(directStream)
            qualities.add(
                0,
                StreamQualityOption(
                    qualityLabel = if (fmt == "HLS") "HLS Auto" else "HD",
                    badgeDetail = "Direct • $fmt",
                    streamUrl = directStream,
                    streamFormat = fmt
                )
            )
        }

        val primaryStream = qualities.firstOrNull()?.streamUrl ?: ""
        val backupStream = qualities.getOrNull(1)?.streamUrl ?: primaryStream
        val year = obj.optInt("releaseYear", obj.optInt("year", 2025))
        val rating = obj.optDouble("rating", 7.8).takeIf { !it.isNaN() } ?: 7.8
        val language = obj.optString("language", obj.optString("audioLanguage", "Original Audio"))
        val overview = obj.optString("overview", obj.optString("description", title))
        val genres = optStringList(obj, "genres").ifEmpty { listOf(fallbackCategory.title) }

        return MovieItem(
            id = id,
            title = title,
            tagline = obj.optString("tagline", "$language • $year"),
            overview = overview,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            releaseYear = year,
            rating = rating,
            maturityRating = obj.optString("maturityRating", "HD"),
            durationMinutes = obj.optInt("durationMinutes", obj.optInt("duration", 110)),
            primaryCategory = fallbackCategory,
            audioLanguage = language,
            genres = genres,
            director = obj.optString("director", "Studio"),
            cast = optStringList(obj, "cast"),
            nativeAspectRatio = obj.optString("aspectRatio", "16:9"),
            videoResolution = qualities.firstOrNull()?.qualityLabel ?: "HD",
            movieLinkSlug = id,
            streamUrl = primaryStream,
            backupStreamUrl = backupStream,
            streamFormat = qualities.firstOrNull()?.streamFormat ?: "MP4",
            qualityOptions = qualities,
            isFeaturedHero = index == 0
        )
    }

    // -------------------------------------------------------------------------
    // HTTP Execution & Defensive JSON Helpers
    // -------------------------------------------------------------------------

    private fun executeJsonGet(url: String): ApiResult<String> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", ApiConfig.USER_AGENT)
            .header("Accept", "application/json")
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val code = response.code
                val bodyString = response.body?.string().orEmpty()
                when {
                    code in 200..299 && bodyString.isNotBlank() -> ApiResult.Success(bodyString)
                    code in 200..299 -> ApiResult.Failure(
                        message = "Server returned an empty response.",
                        isNetworkError = false,
                        statusCode = code
                    )
                    code == 404 -> ApiResult.Failure(
                        message = "Requested movie endpoint was not found on the server (HTTP 404).",
                        isNetworkError = false,
                        statusCode = code
                    )
                    code in 500..599 -> ApiResult.Failure(
                        message = "Streaming server is temporarily unavailable (HTTP $code). Please retry.",
                        isNetworkError = true,
                        statusCode = code
                    )
                    else -> ApiResult.Failure(
                        message = "Server rejected the request (HTTP $code).",
                        isNetworkError = false,
                        statusCode = code
                    )
                }
            }
        } catch (_: UnknownHostException) {
            ApiResult.Failure(
                message = "Cannot reach server. Check your internet connection or Server Settings.",
                isNetworkError = true
            )
        } catch (_: SocketTimeoutException) {
            ApiResult.Failure(
                message = "Server connection timed out. Tap Retry to reconnect.",
                isNetworkError = true
            )
        } catch (e: IOException) {
            ApiResult.Failure(
                message = "Network error while contacting server: ${e.localizedMessage ?: "Connection failed"}.",
                isNetworkError = true
            )
        } catch (e: Exception) {
            ApiResult.Failure(
                message = "Unexpected error communicating with server: ${e.localizedMessage ?: "Unknown error"}.",
                isNetworkError = false
            )
        }
    }

    internal fun isValidStreamUrl(url: String): Boolean {
        val clean = url.trim()
        return clean.startsWith("https://", ignoreCase = true) ||
            clean.startsWith("http://", ignoreCase = true)
    }

    private fun detectStreamFormat(url: String): String {
        val lower = url.lowercase(Locale.US)
        return when {
            lower.contains(".m3u8") -> "HLS"
            lower.contains(".mp4") -> "MP4"
            else -> "Stream"
        }
    }

    private fun resolveAbsoluteUrl(baseUrl: String, path: String): String {
        val clean = path.trim()
        if (clean.isEmpty()) return ""
        return when {
            clean.startsWith("https://", ignoreCase = true) ||
                clean.startsWith("http://", ignoreCase = true) -> clean
            clean.startsWith("/") -> "$baseUrl$clean"
            else -> "$baseUrl/$clean"
        }
    }

    private fun optFirstString(obj: JSONObject, key: String): String {
        val raw = obj.opt(key) ?: return ""
        return when (raw) {
            is String -> raw.trim()
            is Number -> raw.toString()
            is JSONArray -> {
                for (i in 0 until raw.length()) {
                    val value = raw.optString(i, "").trim()
                    if (value.isNotEmpty()) return value
                }
                ""
            }
            else -> ""
        }
    }

    private fun optStringList(obj: JSONObject, key: String): List<String> {
        val raw = obj.opt(key) ?: return emptyList()
        return when (raw) {
            is JSONArray -> (0 until raw.length())
                .mapNotNull { idx -> raw.optString(idx, "").trim().takeIf { it.isNotEmpty() } }
            is String -> raw.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }
            else -> emptyList()
        }
    }

    private fun cleanMovieTitle(raw: String): String {
        return raw
            .replace(Regex("""https?://\S+"""), "")
            .replace(Regex("""www\.\S+"""), "")
            .replace(Regex("""\b(720p|1080p|480p|WEB-?DL|WEBHD|x264|x265|AAC|AVC|CAMRip|HDTS|DVDRip|BluRay)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[\._]{2,}"""), " ")
            .replace(Regex("""\s*\(\s*\)\s*"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '|', '.')
            .ifBlank { raw.trim() }
    }

    private fun parseReleaseYear(yearRaw: String, dateRaw: String, titleRaw: String): Int {
        val candidate = listOf(yearRaw, dateRaw, titleRaw).firstNotNullOfOrNull { source ->
            Regex("""\b(19\d{2}|20\d{2})\b""").find(source)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
        return candidate ?: 2024
    }

    private fun parseRuntimeMinutes(raw: String): Int {
        if (raw.isBlank()) return 95
        val parts = raw.trim().split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            3 -> (parts[0] * 60 + parts[1]).coerceIn(10, 300)
            2 -> parts[0].coerceIn(10, 300)
            1 -> parts[0].coerceIn(10, 300)
            else -> Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE)
                .find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(10, 300) ?: 95
        }
    }

    private fun formatLanguageLabel(rawLang: String, category: MovieCategory): String {
        val lower = rawLang.lowercase(Locale.US)
        return when {
            lower.contains("ben") || lower.contains("bangla") -> "Bangla"
            lower.contains("hin") -> "Hindi"
            lower.contains("tam") -> "Tamil"
            lower.contains("tel") -> "Telugu"
            lower.contains("mal") -> "Malayalam"
            lower.contains("eng") -> "English"
            rawLang.isNotBlank() -> rawLang.replaceFirstChar { it.uppercase(Locale.US) }
            category == MovieCategory.BANGLA -> "Bangla"
            category == MovieCategory.BOLLYWOOD -> "Hindi"
            category == MovieCategory.SOUTH_INDIAN -> "South Indian"
            category == MovieCategory.HOLLYWOOD -> "English"
            else -> "Classic Cinema"
        }
    }

    private fun inferCategory(requested: MovieCategory, language: String): MovieCategory {
        if (requested != MovieCategory.HOME && requested != MovieCategory.MY_LIST) return requested
        val lower = language.lowercase(Locale.US)
        return when {
            lower.contains("bangla") || lower.contains("bengali") -> MovieCategory.BANGLA
            lower.contains("hindi") -> MovieCategory.BOLLYWOOD
            lower.contains("tamil") || lower.contains("telugu") || lower.contains("malayalam") -> MovieCategory.SOUTH_INDIAN
            else -> MovieCategory.HOLLYWOOD
        }
    }

    private fun formatBytesCompact(bytes: Long): String {
        if (bytes <= 0L) return ""
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.1f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.0f MB", mb)
        }
    }

    private fun stripHtmlTags(input: String): String {
        if (input.isBlank()) return ""
        return input
            .replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
