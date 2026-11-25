package com.example

import com.example.data.remote.MovieLinkBdClient
import com.example.model.MovieCategory
import com.example.ui.components.formatDurationMs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun verifyMovieLinkBdCipherDecoder() {
        val decoded = MovieLinkBdClient.decodeMovieLinkDl(
            b64 = "0vG0kRD0oOqwGgE1wmWrdGVWWg2/VsARtlMF+EL5h3BOyrqTzzrfItbZahJcUbFZXhMo+YBcw90iK37eyMwI3TX1acEQiNfWKy/iqEo4W9SdX7fG29u5glXcsOMyU0C570S7o5gioXVVpO45wA==",
            seedHex = "c012e013",
            roundsStr = "300"
        )
        assertTrue("Expected /file/ path, got: $decoded", decoded.startsWith("/file/"))
    }

    @Test
    fun verifyUnpaddedBase64CipherDecoder() {
        // Test unpadded Base64 string (length not divisible by 4)
        val unpadded = "0vG0kRD0oOqwGgE1wmWrdGVWWg2/VsARtlMF+EL5h3BOyrqTzzrfItbZahJcUbFZXhMo+YBcw90iK37eyMwI3TX1acEQiNfWKy/iqEo4W9SdX7fG29u5glXcsOMyU0C570S7o5gioXVVpO45wA"
        val decoded = MovieLinkBdClient.decodeMovieLinkDl(
            b64 = unpadded,
            seedHex = "c012e013",
            roundsStr = "300"
        )
        assertTrue("Expected /file/ path from unpadded base64, got: $decoded", decoded.startsWith("/file/"))
    }

    @Test
    fun verifyDedicatedCategoriesAndFastStartStream() = runBlocking {
        val client = MovieLinkBdClient()
        val banglaPage = client.fetchCatalogPage(MovieCategory.BANGLA, page = 1)
        val hollywoodPage = client.fetchCatalogPage(MovieCategory.HOLLYWOOD, page = 1)
        val bollywoodPage = client.fetchCatalogPage(MovieCategory.BOLLYWOOD, page = 1)

        assertTrue("Expected live Bangla movies from pqbm3h.movielinkbd.at", banglaPage.items.isNotEmpty())
        assertTrue("Expected live Hollywood movies from pqbm3h.movielinkbd.at", hollywoodPage.items.isNotEmpty())
        assertTrue("Expected live Bollywood movies from pqbm3h.movielinkbd.at", bollywoodPage.items.isNotEmpty())

        val firstMovie = hollywoodPage.items.first()
        val fastStartMovie = client.resolveFastStartMovie(firstMovie)
        assertTrue(
            "Expected direct playable fast-start stream URL from pqbm3h.movielinkbd.at, got: ${fastStartMovie.streamUrl}",
            fastStartMovie.streamUrl.startsWith("https://")
        )
        assertTrue(
            "Expected fast-start quality option",
            fastStartMovie.qualityOptions.isNotEmpty()
        )
    }

    @Test
    fun verifyHeifAndAvifProtection() {
        val heicHeader = byteArrayOf(
            0x00, 0x00, 0x00, 0x18,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'h'.code.toByte(), 'e'.code.toByte(), 'i'.code.toByte(), 'c'.code.toByte()
        )
        assertTrue(NseSafeImageLoader.isHeifOrAvifBytes(heicHeader, "image/jpeg"))
        assertTrue(!NseSafeImageLoader.isHeifOrAvifBytes(NseSafeImageLoader.TRANSPARENT_1X1_PNG_BYTES, "image/png"))

        val client = MovieLinkBdClient()
        val normalized = client.normalizeSafeImageUrl("https://pqbm3h.movielinkbd.at/uploads/poster.avif")
        assertTrue(normalized.contains("output=jpg"))
    }

    @Test
    fun verifyPlayerTimecodeFormatting() {
        assertEquals("00:00", formatDurationMs(0L))
        assertEquals("01:05", formatDurationMs(65_000L))
        assertEquals("1:02:03", formatDurationMs(3_723_000L))
    }
}
