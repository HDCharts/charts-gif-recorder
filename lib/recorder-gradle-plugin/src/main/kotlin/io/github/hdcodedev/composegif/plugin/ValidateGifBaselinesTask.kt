package io.github.hdcodedev.composegif.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

internal abstract class ValidateGifBaselinesTask : DefaultTask() {
    init {
        outputs.upToDateWhen { false }
    }

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val actualGifDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val baselineDir: DirectoryProperty

    @get:Input
    public abstract val ffmpegBin: Property<String>

    @get:Input
    public abstract val ffprobeBin: Property<String>

    @get:Input
    public abstract val maxChangedPixelPercentage: Property<Double>

    @get:OutputDirectory
    public abstract val reportDir: DirectoryProperty

    @TaskAction
    public fun validate() {
        val actualFiles = gifFiles(actualGifDir.get().asFile).associateBy { it.name }
        val baselineFiles = gifFiles(baselineDir.get().asFile).associateBy { it.name }
        if (baselineFiles.isEmpty()) {
            throw IllegalStateException("No GIF baselines found in ${baselineDir.get().asFile.absolutePath}.")
        }
        val maxChangedPixelPercentage = maxChangedPixelPercentage.get()
        require(maxChangedPixelPercentage in 0.0..100.0) {
            "maxChangedPixelPercentage must be in range [0.0, 100.0], " +
                "where 1.0 means 1%, was $maxChangedPixelPercentage."
        }

        val reportRoot =
            reportDir.get().asFile.apply {
                deleteRecursively()
                mkdirs()
            }
        val mismatches = mutableListOf<String>()
        (actualFiles.keys + baselineFiles.keys).toSortedSet().forEach { name ->
            val actual = actualFiles[name]
            val baseline = baselineFiles[name]
            when {
                actual == null -> mismatches += "$name: generated GIF is missing"
                baseline == null -> mismatches += "$name: GIF baseline is missing"
                else ->
                    compareGifs(baseline, actual, maxChangedPixelPercentage)?.let { reason ->
                        mismatches += "$name: $reason"
                    }
            }
        }

        val report = reportRoot.resolve("summary.txt")
        report.writeText(
            buildString {
                appendLine("GIF baseline validation")
                appendLine("Baselines: ${baselineDir.get().asFile.absolutePath}")
                appendLine("Generated: ${actualGifDir.get().asFile.absolutePath}")
                appendLine("Compared: ${baselineFiles.keys.intersect(actualFiles.keys).size}")
                appendLine("Max changed pixels per frame: ${formatPercentage(maxChangedPixelPercentage)}%")
                appendLine("Mismatches: ${mismatches.size}")
                if (mismatches.isNotEmpty()) {
                    appendLine()
                    mismatches.forEach { appendLine("- $it") }
                }
            },
        )

        if (mismatches.isNotEmpty()) {
            mismatches.forEach { mismatch ->
                logger.error("GIF baseline mismatch: $mismatch")
            }
            throw GradleException(
                "GIF baseline validation failed for ${mismatches.size} file(s). " +
                    "See ${report.absolutePath} and the generated GIF artifacts.",
            )
        }
        logger.lifecycle("GIF baseline validation passed for ${baselineFiles.size} GIFs")
    }

    private fun compareGifs(
        baseline: File,
        actual: File,
        maxChangedPixelPercentage: Double,
    ): String? {
        val expected = decodeGif(baseline)
        val received = decodeGif(actual)
        if (expected.width != received.width || expected.height != received.height) {
            return "dimensions differ: expected ${expected.width}x${expected.height}, " +
                "received ${received.width}x${received.height}"
        }
        if (expected.frameCount != received.frameCount) {
            return "frame count differs: expected ${expected.frameCount}, received ${received.frameCount}"
        }
        if (expected.timestamps != received.timestamps) {
            return "frame timing differs"
        }
        return compareGifPixelStreams(
            expected = expected,
            received = received,
            width = expected.width,
            height = expected.height,
            frameCount = expected.frameCount,
            maxChangedPixelPercentage = maxChangedPixelPercentage,
        )
    }

    private fun compareGifPixelStreams(
        expected: DecodedGif,
        received: DecodedGif,
        width: Int,
        height: Int,
        frameCount: Int,
        maxChangedPixelPercentage: Double,
    ): String? {
        val processes = mutableListOf<RawVideoProcess>()
        var comparisonFailure: Throwable? = null
        return try {
            processes += startRawVideo(expected.file)
            processes += startRawVideo(received.file)
            val expectedProcess = processes[0]
            val receivedProcess = processes[1]
            compareDecodedPixelStreams(
                expected = expectedProcess.process.inputStream,
                received = receivedProcess.process.inputStream,
                width = width,
                height = height,
                frameCount = frameCount,
                maxChangedPixelPercentage = maxChangedPixelPercentage,
            )
        } catch (error: Throwable) {
            comparisonFailure = error
            throw error
        } finally {
            try {
                cleanupRawVideos(processes)
            } catch (cleanupFailure: Throwable) {
                if (comparisonFailure == null) {
                    throw cleanupFailure
                }
                comparisonFailure.addSuppressed(cleanupFailure)
            }
        }
    }

    private fun decodeGif(file: File): DecodedGif {
        val metadata =
            runFfprobe(
                file,
                "-count_frames",
                "-show_entries",
                "stream=width,height,nb_read_frames",
                "-of",
                "csv=p=0:s=,",
            ).trim().split(",")
        check(metadata.size == 3) { "Could not read GIF metadata from ${file.absolutePath}" }

        val timestamps =
            runFfprobe(
                file,
                "-show_entries",
                "frame=pts_time",
                "-of",
                "csv=p=0",
            ).lineSequence().filter { it.isNotBlank() }.toList()
        return DecodedGif(
            width = metadata[0].toInt(),
            height = metadata[1].toInt(),
            frameCount = metadata[2].toInt(),
            timestamps = timestamps,
            file = file,
        )
    }

    private fun runFfprobe(
        file: File,
        vararg arguments: String,
    ): String =
        runChecked(
            buildList {
                add(ffprobeBin.get())
                addAll(listOf("-v", "error", "-select_streams", "v:0"))
                addAll(arguments)
                add(file.absolutePath)
            },
        )

    private fun startRawVideo(file: File): RawVideoProcess {
        val command =
            listOf(
                ffmpegBin.get(),
                "-hide_banner",
                "-loglevel",
                "error",
                "-i",
                file.absolutePath,
                "-f",
                "rawvideo",
                "-pix_fmt",
                "rgba",
                "-",
            )
        val process =
            try {
                ProcessBuilder(command).directory(project.projectDir).start()
            } catch (error: IOException) {
                throw IllegalStateException(
                    "Failed to start command (${command.joinToString(" ")}): ${error.message}",
                    error,
                )
            }
        val errorOutput = AtomicReference("")
        val errorThread =
            thread(isDaemon = true, name = "compose-gif-ffmpeg-stderr") {
                errorOutput.set(process.errorStream.bufferedReader().readText())
            }
        return RawVideoProcess(process, errorOutput, errorThread)
    }

    private fun runChecked(command: List<String>): String {
        val process =
            try {
                ProcessBuilder(command).directory(project.projectDir).redirectErrorStream(true).start()
            } catch (error: IOException) {
                throw IllegalStateException(
                    "Failed to start command (${command.joinToString(" ")}): ${error.message}",
                    error,
                )
            }
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        if (exit != 0) {
            throw IllegalStateException("Command failed (${command.joinToString(" ")}):\n$output")
        }
        return output
    }

    private data class DecodedGif(
        val width: Int,
        val height: Int,
        val frameCount: Int,
        val timestamps: List<String>,
        val file: File,
    )
}

internal data class RawVideoProcess(
    val process: Process,
    val errorOutput: AtomicReference<String>,
    val errorThread: Thread,
)

internal fun cleanupRawVideos(rawVideos: List<RawVideoProcess>) {
    var cleanupFailure: Throwable? = null

    rawVideos.forEach { rawVideo ->
        try {
            rawVideo.process.inputStream.close()
        } catch (error: Throwable) {
            cleanupFailure = cleanupFailure.addCleanupFailure(error)
        }
    }

    rawVideos.forEach { rawVideo ->
        try {
            finishRawVideo(rawVideo)
        } catch (error: Throwable) {
            cleanupFailure = cleanupFailure.addCleanupFailure(error)
        }
    }

    cleanupFailure?.let { throw it }
}

private fun finishRawVideo(rawVideo: RawVideoProcess) {
    val exit = rawVideo.process.waitFor()
    rawVideo.errorThread.join()
    if (exit != 0) {
        throw IllegalStateException(
            "Command failed while decoding GIF: ${rawVideo.errorOutput.get()}".trimEnd(),
        )
    }
}

private fun Throwable?.addCleanupFailure(error: Throwable): Throwable {
    if (this == null) return error
    addSuppressed(error)
    return this
}

internal const val DEFAULT_MAX_CHANGED_PIXEL_PERCENTAGE = 1.0

/**
 * Compares two raw RGBA frame streams without retaining the complete GIFs in
 * memory. Only one frame from each stream is held at a time.
 */
internal fun compareDecodedPixelStreams(
    expected: InputStream,
    received: InputStream,
    width: Int,
    height: Int,
    frameCount: Int,
    maxChangedPixelPercentage: Double,
): String? {
    val pixelsPerFrame = width * height
    val bytesPerFrame = pixelsPerFrame * 4
    val expectedFrame = ByteArray(bytesPerFrame)
    val receivedFrame = ByteArray(bytesPerFrame)
    var totalChangedPixels = 0
    var maxChangedPixels = 0
    var maxChangedFrame = 0

    repeat(frameCount) { frame ->
        if (!readFully(expected, expectedFrame) || !readFully(received, receivedFrame)) {
            return "decoded pixel stream ended before frame $frame"
        }

        var changedPixels = 0
        for (pixel in 0 until pixelsPerFrame) {
            val pixelStart = pixel * 4
            var differs = false
            repeat(4) { channel ->
                if (expectedFrame[pixelStart + channel] != receivedFrame[pixelStart + channel]) {
                    differs = true
                }
            }
            if (differs) changedPixels++
        }

        totalChangedPixels += changedPixels
        if (changedPixels > maxChangedPixels) {
            maxChangedPixels = changedPixels
            maxChangedFrame = frame
        }
    }

    if (expected.read() != -1 || received.read() != -1) {
        return "decoded pixel stream contains more frames than metadata reports"
    }

    val changedPixelPercentage = maxChangedPixels * 100.0 / pixelsPerFrame
    if (changedPixelPercentage <= maxChangedPixelPercentage) return null

    val totalPixels = pixelsPerFrame * frameCount
    val totalChangedPercentage = totalChangedPixels * 100.0 / totalPixels
    return "pixel content differs: " +
        "$maxChangedPixels/$pixelsPerFrame pixels (${formatPercentage(changedPixelPercentage)}%) " +
        "in frame $maxChangedFrame; " +
        "$totalChangedPixels/$totalPixels pixels (${formatPercentage(totalChangedPercentage)}%) " +
        "across GIF (allowed ${formatPercentage(maxChangedPixelPercentage)}% per frame)"
}

private fun readFully(
    input: InputStream,
    buffer: ByteArray,
): Boolean {
    var offset = 0
    while (offset < buffer.size) {
        val read = input.read(buffer, offset, buffer.size - offset)
        if (read < 0) return false
        if (read == 0) continue
        offset += read
    }
    return true
}

internal fun compareDecodedPixels(
    expected: ByteArray,
    received: ByteArray,
    width: Int,
    height: Int,
    frameCount: Int,
    maxChangedPixelPercentage: Double,
): String? {
    if (expected.contentEquals(received)) return null

    val pixelsPerFrame = width * height
    val bytesPerFrame = pixelsPerFrame * 4
    var totalChangedPixels = 0
    var maxChangedPixels = 0
    var maxChangedFrame = 0

    repeat(frameCount) { frame ->
        var changedPixels = 0
        val frameStart = frame * bytesPerFrame
        for (pixel in 0 until pixelsPerFrame) {
            val pixelStart = frameStart + (pixel * 4)
            var differs = false
            repeat(4) { channel ->
                val expectedValue = expected[pixelStart + channel].toInt() and 0xFF
                val receivedValue = received[pixelStart + channel].toInt() and 0xFF
                if (expectedValue != receivedValue) {
                    differs = true
                }
            }
            if (differs) changedPixels++
        }
        totalChangedPixels += changedPixels
        if (changedPixels > maxChangedPixels) {
            maxChangedPixels = changedPixels
            maxChangedFrame = frame
        }
    }

    val changedPixelPercentage = maxChangedPixels * 100.0 / pixelsPerFrame
    if (changedPixelPercentage <= maxChangedPixelPercentage) return null

    val totalPixels = pixelsPerFrame * frameCount
    val totalChangedPercentage = totalChangedPixels * 100.0 / totalPixels
    return "pixel content differs: " +
        "$maxChangedPixels/$pixelsPerFrame pixels (${formatPercentage(changedPixelPercentage)}%) " +
        "in frame $maxChangedFrame; " +
        "$totalChangedPixels/$totalPixels pixels (${formatPercentage(totalChangedPercentage)}%) " +
        "across GIF (allowed ${formatPercentage(maxChangedPixelPercentage)}% per frame)"
}

private fun formatPercentage(value: Double): String = "%.4f".format(java.util.Locale.US, value)
