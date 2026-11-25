package com.example.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WatchlistEntity(
    val movieId: String,
    val title: String,
    val tagline: String,
    val overview: String,
    val posterUrl: String,
    val backdropUrl: String,
    val releaseYear: Int,
    val rating: Double,
    val maturityRating: String,
    val durationMinutes: Int,
    val primaryCategoryId: String,
    val audioLanguage: String,
    val genresCsv: String,
    val director: String,
    val castCsv: String,
    val nativeAspectRatio: String,
    val videoResolution: String,
    val movieLinkSlug: String,
    val streamUrl: String,
    val backupStreamUrl: String,
    val streamFormat: String,
    val addedAt: Long = System.currentTimeMillis()
)

data class WatchProgressEntity(
    val movieId: String,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String,
    val streamUrl: String,
    val nativeAspectRatio: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

data class CachedPageEntity(
    val pageKey: String,
    val queryOrCategoryKey: String,
    val pageNumber: Int,
    val totalPages: Int,
    val totalResults: Int,
    val movieIdsCsv: String,
    val cachedAt: Long = System.currentTimeMillis()
)

interface NseDao {
    fun getAllWatchlist(): Flow<List<WatchlistEntity>>
    suspend fun insertWatchlist(item: WatchlistEntity)
    suspend fun deleteFromWatchlist(movieId: String)
    suspend fun isInWatchlist(movieId: String): Boolean
    fun getAllWatchProgress(): Flow<List<WatchProgressEntity>>
    suspend fun getWatchProgressForMovie(movieId: String): WatchProgressEntity?
    suspend fun upsertWatchProgress(progress: WatchProgressEntity)
    suspend fun deleteWatchProgress(movieId: String)
    suspend fun getCachedPage(pageKey: String): CachedPageEntity?
    suspend fun insertCachedPage(page: CachedPageEntity)
    suspend fun clearAllCachedPages()
}

class NseDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "nse_stream_local.db", null, 4) {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val watchlistState = MutableStateFlow<List<WatchlistEntity>>(emptyList())
    private val watchProgressState = MutableStateFlow<List<WatchProgressEntity>>(emptyList())

    private val daoImpl = object : NseDao {
        override fun getAllWatchlist(): Flow<List<WatchlistEntity>> = watchlistState.asStateFlow()

        override suspend fun insertWatchlist(item: WatchlistEntity) = withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("movieId", item.movieId)
                put("title", item.title)
                put("tagline", item.tagline)
                put("overview", item.overview)
                put("posterUrl", item.posterUrl)
                put("backdropUrl", item.backdropUrl)
                put("releaseYear", item.releaseYear)
                put("rating", item.rating)
                put("maturityRating", item.maturityRating)
                put("durationMinutes", item.durationMinutes)
                put("primaryCategoryId", item.primaryCategoryId)
                put("audioLanguage", item.audioLanguage)
                put("genresCsv", item.genresCsv)
                put("director", item.director)
                put("castCsv", item.castCsv)
                put("nativeAspectRatio", item.nativeAspectRatio)
                put("videoResolution", item.videoResolution)
                put("movieLinkSlug", item.movieLinkSlug)
                put("streamUrl", item.streamUrl)
                put("backupStreamUrl", item.backupStreamUrl)
                put("streamFormat", item.streamFormat)
                put("addedAt", item.addedAt)
            }
            writableDatabase.insertWithOnConflict(
                "watchlist_movies",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
            watchlistState.value = queryWatchlistSync()
        }

        override suspend fun deleteFromWatchlist(movieId: String) = withContext(Dispatchers.IO) {
            writableDatabase.delete("watchlist_movies", "movieId = ?", arrayOf(movieId))
            watchlistState.value = queryWatchlistSync()
        }

        override suspend fun isInWatchlist(movieId: String): Boolean = withContext(Dispatchers.IO) {
            readableDatabase.rawQuery(
                "SELECT 1 FROM watchlist_movies WHERE movieId = ? LIMIT 1",
                arrayOf(movieId)
            ).use { cursor ->
                cursor.moveToFirst()
            }
        }

        override fun getAllWatchProgress(): Flow<List<WatchProgressEntity>> =
            watchProgressState.asStateFlow()

        override suspend fun getWatchProgressForMovie(movieId: String): WatchProgressEntity? =
            withContext(Dispatchers.IO) {
                readableDatabase.rawQuery(
                    "SELECT movieId, title, posterUrl, backdropUrl, streamUrl, nativeAspectRatio, positionMs, durationMs, updatedAt FROM watch_progress WHERE movieId = ? LIMIT 1",
                    arrayOf(movieId)
                ).use { cursor ->
                    if (cursor.moveToFirst()) {
                        WatchProgressEntity(
                            movieId = cursor.getString(0),
                            title = cursor.getString(1),
                            posterUrl = cursor.getString(2),
                            backdropUrl = cursor.getString(3),
                            streamUrl = cursor.getString(4),
                            nativeAspectRatio = cursor.getString(5),
                            positionMs = cursor.getLong(6),
                            durationMs = cursor.getLong(7),
                            updatedAt = cursor.getLong(8)
                        )
                    } else {
                        null
                    }
                }
            }

        override suspend fun upsertWatchProgress(progress: WatchProgressEntity) =
            withContext(Dispatchers.IO) {
                val values = ContentValues().apply {
                    put("movieId", progress.movieId)
                    put("title", progress.title)
                    put("posterUrl", progress.posterUrl)
                    put("backdropUrl", progress.backdropUrl)
                    put("streamUrl", progress.streamUrl)
                    put("nativeAspectRatio", progress.nativeAspectRatio)
                    put("positionMs", progress.positionMs)
                    put("durationMs", progress.durationMs)
                    put("updatedAt", progress.updatedAt)
                }
                writableDatabase.insertWithOnConflict(
                    "watch_progress",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                watchProgressState.value = queryWatchProgressSync()
            }

        override suspend fun deleteWatchProgress(movieId: String) = withContext(Dispatchers.IO) {
            writableDatabase.delete("watch_progress", "movieId = ?", arrayOf(movieId))
            watchProgressState.value = queryWatchProgressSync()
        }

        override suspend fun getCachedPage(pageKey: String): CachedPageEntity? =
            withContext(Dispatchers.IO) {
                readableDatabase.rawQuery(
                    "SELECT pageKey, queryOrCategoryKey, pageNumber, totalPages, totalResults, movieIdsCsv, cachedAt FROM cached_catalog_pages WHERE pageKey = ? LIMIT 1",
                    arrayOf(pageKey)
                ).use { cursor ->
                    if (cursor.moveToFirst()) {
                        CachedPageEntity(
                            pageKey = cursor.getString(0),
                            queryOrCategoryKey = cursor.getString(1),
                            pageNumber = cursor.getInt(2),
                            totalPages = cursor.getInt(3),
                            totalResults = cursor.getInt(4),
                            movieIdsCsv = cursor.getString(5),
                            cachedAt = cursor.getLong(6)
                        )
                    } else {
                        null
                    }
                }
            }

        override suspend fun insertCachedPage(page: CachedPageEntity): Unit =
            withContext(Dispatchers.IO) {
                val values = ContentValues().apply {
                    put("pageKey", page.pageKey)
                    put("queryOrCategoryKey", page.queryOrCategoryKey)
                    put("pageNumber", page.pageNumber)
                    put("totalPages", page.totalPages)
                    put("totalResults", page.totalResults)
                    put("movieIdsCsv", page.movieIdsCsv)
                    put("cachedAt", page.cachedAt)
                }
                writableDatabase.insertWithOnConflict(
                    "cached_catalog_pages",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                Unit
            }

        override suspend fun clearAllCachedPages(): Unit = withContext(Dispatchers.IO) {
            writableDatabase.delete("cached_catalog_pages", null, null)
            Unit
        }
    }

    init {
        ioScope.launch {
            watchlistState.value = queryWatchlistSync()
            watchProgressState.value = queryWatchProgressSync()
        }
    }

    fun nseDao(): NseDao = daoImpl

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS watchlist_movies (
                movieId TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                tagline TEXT NOT NULL,
                overview TEXT NOT NULL,
                posterUrl TEXT NOT NULL,
                backdropUrl TEXT NOT NULL,
                releaseYear INTEGER NOT NULL,
                rating REAL NOT NULL,
                maturityRating TEXT NOT NULL,
                durationMinutes INTEGER NOT NULL,
                primaryCategoryId TEXT NOT NULL,
                audioLanguage TEXT NOT NULL,
                genresCsv TEXT NOT NULL,
                director TEXT NOT NULL,
                castCsv TEXT NOT NULL,
                nativeAspectRatio TEXT NOT NULL,
                videoResolution TEXT NOT NULL,
                movieLinkSlug TEXT NOT NULL,
                streamUrl TEXT NOT NULL,
                backupStreamUrl TEXT NOT NULL,
                streamFormat TEXT NOT NULL,
                addedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS watch_progress (
                movieId TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                posterUrl TEXT NOT NULL,
                backdropUrl TEXT NOT NULL,
                streamUrl TEXT NOT NULL,
                nativeAspectRatio TEXT NOT NULL,
                positionMs INTEGER NOT NULL,
                durationMs INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_catalog_pages (
                pageKey TEXT PRIMARY KEY NOT NULL,
                queryOrCategoryKey TEXT NOT NULL,
                pageNumber INTEGER NOT NULL,
                totalPages INTEGER NOT NULL,
                totalResults INTEGER NOT NULL,
                movieIdsCsv TEXT NOT NULL,
                cachedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS watchlist_movies")
        db.execSQL("DROP TABLE IF EXISTS watch_progress")
        db.execSQL("DROP TABLE IF EXISTS cached_catalog_pages")
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }

    private fun queryWatchlistSync(): List<WatchlistEntity> {
        val list = mutableListOf<WatchlistEntity>()
        readableDatabase.rawQuery(
            """
            SELECT movieId, title, tagline, overview, posterUrl, backdropUrl, releaseYear,
                   rating, maturityRating, durationMinutes, primaryCategoryId, audioLanguage,
                   genresCsv, director, castCsv, nativeAspectRatio, videoResolution,
                   movieLinkSlug, streamUrl, backupStreamUrl, streamFormat, addedAt
            FROM watchlist_movies ORDER BY addedAt DESC
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    WatchlistEntity(
                        movieId = cursor.getString(0),
                        title = cursor.getString(1),
                        tagline = cursor.getString(2),
                        overview = cursor.getString(3),
                        posterUrl = cursor.getString(4),
                        backdropUrl = cursor.getString(5),
                        releaseYear = cursor.getInt(6),
                        rating = cursor.getDouble(7),
                        maturityRating = cursor.getString(8),
                        durationMinutes = cursor.getInt(9),
                        primaryCategoryId = cursor.getString(10),
                        audioLanguage = cursor.getString(11),
                        genresCsv = cursor.getString(12),
                        director = cursor.getString(13),
                        castCsv = cursor.getString(14),
                        nativeAspectRatio = cursor.getString(15),
                        videoResolution = cursor.getString(16),
                        movieLinkSlug = cursor.getString(17),
                        streamUrl = cursor.getString(18),
                        backupStreamUrl = cursor.getString(19),
                        streamFormat = cursor.getString(20),
                        addedAt = cursor.getLong(21)
                    )
                )
            }
        }
        return list
    }

    private fun queryWatchProgressSync(): List<WatchProgressEntity> {
        val list = mutableListOf<WatchProgressEntity>()
        readableDatabase.rawQuery(
            """
            SELECT movieId, title, posterUrl, backdropUrl, streamUrl, nativeAspectRatio,
                   positionMs, durationMs, updatedAt
            FROM watch_progress ORDER BY updatedAt DESC
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    WatchProgressEntity(
                        movieId = cursor.getString(0),
                        title = cursor.getString(1),
                        posterUrl = cursor.getString(2),
                        backdropUrl = cursor.getString(3),
                        streamUrl = cursor.getString(4),
                        nativeAspectRatio = cursor.getString(5),
                        positionMs = cursor.getLong(6),
                        durationMs = cursor.getLong(7),
                        updatedAt = cursor.getLong(8)
                    )
                )
            }
        }
        return list
    }

    companion object {
        @Volatile
        private var INSTANCE: NseDatabase? = null

        fun getInstance(context: Context): NseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NseDatabase(context).also { INSTANCE = it }
            }
        }
    }
}
