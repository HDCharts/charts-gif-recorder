package io.github.hdcodedev.composegif.plugin

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GifPixelComparisonTest {
    @Test
    fun identicalPixels_pass() {
        val pixels = ByteArray(2 * 2 * 4)

        assertNull(
            compareDecodedPixels(
                expected = pixels,
                received = pixels.copyOf(),
                width = 2,
                height = 2,
                frameCount = 1,
                maxChangedPixelPercentage = 0.0,
            ),
        )
    }

    @Test
    fun fivePercentDifferenceWithinPercentage_passes() {
        val expected = ByteArray(10 * 10 * 4)
        val received =
            expected.copyOf().also {
                it[0] = 10
                it[4] = 10
                it[8] = 10
                it[12] = 10
                it[16] = 10
            }

        assertNull(
            compareDecodedPixels(
                expected = expected,
                received = received,
                width = 10,
                height = 10,
                frameCount = 1,
                maxChangedPixelPercentage = 5.0,
            ),
        )
    }

    @Test
    fun differenceAbovePercentage_failsWithStatistics() {
        val expected = ByteArray(2 * 2 * 4)
        val received =
            expected.copyOf().also {
                it[0] = 10
                it[4] = 10
            }

        val message =
            compareDecodedPixels(
                expected = expected,
                received = received,
                width = 2,
                height = 2,
                frameCount = 1,
                maxChangedPixelPercentage = 25.0,
            )

        assertNotNull(message)
        assertContains(message, "2/4 pixels")
        assertContains(message, "50.0000%")
    }

    @Test
    fun oneChannelDifference_isCountedAsChanged() {
        val expected = ByteArray(4)
        val received = byteArrayOf(1, 0, 0, 0)

        val message =
            compareDecodedPixels(
                expected = expected,
                received = received,
                width = 1,
                height = 1,
                frameCount = 1,
                maxChangedPixelPercentage = 0.01,
            )

        assertNotNull(message)
        assertContains(message, "1/1 pixels")
    }

    @Test
    fun zeroPercentage_keepsExactPixelComparison() {
        val expected = ByteArray(4)
        val received = byteArrayOf(1, 0, 0, 0)

        assertNotNull(
            compareDecodedPixels(
                expected = expected,
                received = received,
                width = 1,
                height = 1,
                frameCount = 1,
                maxChangedPixelPercentage = 0.0,
            ),
        )
    }
}
