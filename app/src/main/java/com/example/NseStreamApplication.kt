package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.disk.DiskCache
import coil.fetch.SourceResult
import coil.request.Options
import com.example.data.remote.ApiConfig
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Application entry point for NSE STREAM v1.0.1.
 * Initializes centralized `ApiConfig` and configures a resilient Coil `ImageLoader`
 * that guards against unsupported HEIF/HEIC/AVIF streams and handles broken poster URLs cleanly.
 */
class NseStreamApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        ApiConfig.init(this)
        Coil.setImageLoader(this)
    }

    override fun newImageLoader(): ImageLoader {
        return NseSafeImageLoader.get(this)
    }
}

object NseSafeImageLoader {

    @Volatile
    private var instance: ImageLoader? = null

    internal val TRANSPARENT_1X1_PNG_BYTES: ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(),
        0x89.toByte(), 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
        0x54, 0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00,
        0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00,
        0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(),
        0x42, 0x60, 0x82.toByte()
    )

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: buildSafeImageLoader(context.applicationContext).also {
                instance = it
            }
        }
    }

    internal fun isHeifOrAvifBytes(header: ByteArray, contentType: String? = null): Boolean {
        val lowerMime = contentType?.lowercase(Locale.US).orEmpty()
        if (lowerMime.contains("heif") || lowerMime.contains("heic") || lowerMime.contains("avif")) {
            return true
        }
        if (header.size >= 8 &&
            header[4] == 'f'.code.toByte() &&
            header[5] == 't'.code.toByte() &&
            header[6] == 'y'.code.toByte() &&
            header[7] == 'p'.code.toByte()
        ) {
            return true
        }
        return false
    }

    private fun buildSafeImageLoader(context: Context): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(SafeImageFormatInterceptor())
            .build()

        return ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            .allowHardware(false)
            .components {
                add(HeifGuardDecoderFactory())
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("nse_safe_image_cache_v3"))
                    .maxSizePercent(0.04)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    internal class SafeImageFormatInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val originalUrl = originalRequest.url.toString()

            val safeRequest = originalRequest.newBuilder()
                .header("User-Agent", ApiConfig.USER_AGENT)
                .header("Accept", "image/jpeg,image/png,image/webp;q=0.9")
                .build()

            val response = try {
                chain.proceed(safeRequest)
            } catch (_: Exception) {
                return createFallbackPngResponse(safeRequest)
            }

            if (!response.isSuccessful) {
                response.close()
                return createFallbackPngResponse(safeRequest)
            }

            val peekedHeader = try {
                response.peekBody(16).bytes()
            } catch (_: Exception) {
                ByteArray(0)
            }

            if (!isHeifOrAvifBytes(peekedHeader, response.header("Content-Type"))) {
                return response
            }

            response.close()

            if (!originalUrl.contains("wsrv.nl", ignoreCase = true)) {
                try {
                    val encodedUrl = URLEncoder.encode(originalUrl, "UTF-8")
                    val proxyUrl = "https://wsrv.nl/?url=$encodedUrl&output=jpg&w=480&q=82"
                    val proxyRequest = originalRequest.newBuilder()
                        .url(proxyUrl)
                        .header("User-Agent", ApiConfig.USER_AGENT)
                        .header("Accept", "image/jpeg,image/png;q=0.9")
                        .build()
                    val proxyResponse = chain.proceed(proxyRequest)
                    if (proxyResponse.isSuccessful) {
                        val proxyHeader = proxyResponse.peekBody(16).bytes()
                        if (!isHeifOrAvifBytes(proxyHeader, proxyResponse.header("Content-Type"))) {
                            return proxyResponse
                        }
                    }
                    proxyResponse.close()
                } catch (_: Exception) {
                    // Fall through to clean PNG fallback
                }
            }

            return createFallbackPngResponse(safeRequest)
        }

        private fun createFallbackPngResponse(request: okhttp3.Request): Response {
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Content-Type", "image/png")
                .body(TRANSPARENT_1X1_PNG_BYTES.toResponseBody("image/png".toMediaType()))
                .build()
        }
    }

    internal class HeifGuardDecoderFactory : Decoder.Factory {
        override fun create(
            result: SourceResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
            val headerBytes = try {
                val peekSource = result.source.source().peek()
                val buf = ByteArray(16)
                val readCount = peekSource.read(buf, 0, buf.size)
                if (readCount > 0) buf.copyOf(readCount) else ByteArray(0)
            } catch (_: Exception) {
                ByteArray(0)
            }

            if (!isHeifOrAvifBytes(headerBytes, result.mimeType)) {
                return null
            }

            return Decoder {
                try {
                    result.source.close()
                } catch (_: Exception) {
                }
                val emptyBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                DecodeResult(
                    drawable = BitmapDrawable(options.context.resources, emptyBitmap),
                    isSampled = false
                )
            }
        }
    }
}
