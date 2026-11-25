package com.example

import com.example.data.remote.ApiConfig
import com.example.data.remote.NseMovieApiClient
import com.example.ui.components.formatDurationMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun verifyVersionUpdateTo1_0_1() {
        assertEquals("1.0.1", BuildConfig.VERSION_NAME)
        assertEquals(2, BuildConfig.VERSION_CODE)
        assertEquals("https://archive.org", BuildConfig.DEFAULT_API_BASE_URL)
    }

    @Test
    fun verifyApiConfigValidationAndNormalization() {
        assertEquals("https://archive.org", ApiConfig.normalizeBaseUrl("  https://archive.org/  "))
        assertTrue(ApiConfig.isValidBaseUrl("https://archive.org"))
        assertTrue(ApiConfig.isValidBaseUrl("http://192.168.1.50:8080"))
        assertFalse(ApiConfig.isValidBaseUrl(""))
        assertFalse(ApiConfig.isValidBaseUrl("ftp://example.com"))
        assertFalse(ApiConfig.isValidBaseUrl("not-a-valid-url"))
        assertTrue(ApiConfig.isUsingArchiveCatalog("https://archive.org"))
        assertFalse(ApiConfig.isUsingArchiveCatalog("https://api.mycinema.example"))
    }

    @Test
    fun verifyHeifAndAvifProtection() {
        val heicHeader = byteArrayOf(
            0x00, 0x00, 0x00, 0x18,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'h'.code.toByte(), 'e'.code.toByte(), 'i'.code.toByte(), 'c'.code.toByte()
        )
        assertTrue(NseSafeImageLoader.isHeifOrAvifBytes(heicHeader, "image/jpeg"))
        assertFalse(NseSafeImageLoader.isHeifOrAvifBytes(NseSafeImageLoader.TRANSPARENT_1X1_PNG_BYTES, "image/png"))
    }

    @Test
    fun verifyUrlPathSegmentEncoding() {
        val encoded = NseMovieApiClient.encodePathSegments("folder/my movie (1968).mp4")
        assertEquals("folder/my%20movie%20%281968%29.mp4", encoded)
    }

    @Test
    fun verifyPlayerTimecodeFormatting() {
        assertEquals("00:00", formatDurationMs(0L))
        assertEquals("01:05", formatDurationMs(65_000L))
        assertEquals("1:02:03", formatDurationMs(3_723_000L))
    }
}
