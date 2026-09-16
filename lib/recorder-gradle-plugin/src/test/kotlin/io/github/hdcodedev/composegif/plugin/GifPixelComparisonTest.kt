package io.github.hdcodedev.composegif.plugin

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GifPixelComparisonTest {
    @Test
    fun rawVideoCommand_usesGifFrameTimestampsWithoutDuplication() {
        assertEquals(
            listOf(
                "ffmpeg",
                "-hide_banner",
                "-loglevel",
                "error",
                "-i",
                "/tmp/input.gif",
                "-fps_mode",
                "passthrough",
                "-f",
                "rawvideo",
                "-pix_fmt",
                "rgba",
                "-",
            ),
            rawVideoCommand("ffmpeg", File("/tmp/input.gif")),
        )
    }

    @Test
    fun cleanup_finishesAllProcesses_whenOneProcessFails() {
        val failedProcess = RecordingProcess(exitCode = 1)
        val healthyProcess = RecordingProcess(exitCode = 0)
        val rawVideos =
            listOf(
                rawVideo(failedProcess),
                rawVideo(healthyProcess),
            )

        val error =
            assertFailsWith<IllegalStateException> {
                cleanupRawVideos(rawVideos)
            }

        assertContains(error.message.orEmpty(), "Command failed while decoding GIF")
        assertTrue(failedProcess.waited)
        assertTrue(healthyProcess.waited)
        assertTrue(failedProcess.inputClosed)
        assertTrue(healthyProcess.inputClosed)
    }

    @Test
    fun identicalPixelStreams_passWithoutRetainingWholeInput() {
        val pixels = ByteArray(2 * 2 * 4)

        assertNull(
            compareDecodedPixelStreams(
                expected = ByteArrayInputStream(pixels),
                received = ByteArrayInputStream(pixels.copyOf()),
                width = 2,
                height = 2,
                frameCount = 1,
                maxChangedPixelPercentage = 0.0,
            ),
        )
    }

    @Test
    fun pixelStreams_compareFrameByFrame() {
        val expected = ByteArray(2 * 2 * 4)
        val received = expected.copyOf().also { it[4] = 10 }

        val message =
            compareDecodedPixelStreams(
                expected = ByteArrayInputStream(expected),
                received = ByteArrayInputStream(received),
                width = 2,
                height = 2,
                frameCount = 1,
                maxChangedPixelPercentage = 0.0,
            )

        assertNotNull(message)
        assertContains(message, "1/4 pixels")
    }

    @Test
    fun truncatedPixelStream_isRejected() {
        val frame = ByteArray(2 * 2 * 4)

        val message =
            compareDecodedPixelStreams(
                expected = ByteArrayInputStream(frame),
                received = ByteArrayInputStream(frame.copyOf(frame.size - 1)),
                width = 2,
                height = 2,
                frameCount = 1,
                maxChangedPixelPercentage = 0.0,
            )

        assertNotNull(message)
        assertContains(message, "ended before frame 0")
    }

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

    private fun rawVideo(process: RecordingProcess): RawVideoProcess =
        RawVideoProcess(
            process = process,
            errorOutput = AtomicReference("ffmpeg failed"),
            errorThread = thread(isDaemon = true) {},
        )

    private class RecordingProcess(
        private val exitCode: Int,
    ) : Process() {
        var waited = false
        var inputClosed = false

        private val input =
            object : ByteArrayInputStream(ByteArray(0)) {
                override fun close() {
                    inputClosed = true
                    super.close()
                }
            }

        override fun getOutputStream() = ByteArrayOutputStream()

        override fun getInputStream(): InputStream = input

        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun waitFor(): Int {
            waited = true
            return exitCode
        }

        override fun exitValue(): Int = exitCode

        override fun destroy() = Unit
    }
}
