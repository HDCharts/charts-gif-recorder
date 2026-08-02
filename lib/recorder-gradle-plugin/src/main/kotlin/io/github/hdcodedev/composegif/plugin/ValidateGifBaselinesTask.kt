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

    @get:OutputDirectory
    public abstract val reportDir: DirectoryProperty

    @TaskAction
    public fun validate() {
        val actualFiles = gifFiles(actualGifDir.get().asFile).associateBy { it.name }
        val baselineFiles = gifFiles(baselineDir.get().asFile).associateBy { it.name }
        if (baselineFiles.isEmpty()) {
            throw IllegalStateException("No GIF baselines found in ${baselineDir.get().asFile.absolutePath}.")
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
                    compareGifs(baseline, actual)?.let { reason ->
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
                appendLine("Mismatches: ${mismatches.size}")
                if (mismatches.isNotEmpty()) {
                    appendLine()
                    mismatches.forEach { appendLine("- $it") }
                }
            },
        )

        if (mismatches.isNotEmpty()) {
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
        if (expected.pixels.size != received.pixels.size) {
            return "decoded pixel buffer size differs: expected ${expected.pixels.size}, " +
                "received ${received.pixels.size}"
        }
        if (expected.pixels.contentEquals(received.pixels)) return null

        val firstDifference =
            expected.pixels.indices.first { index ->
                expected.pixels[index] != received.pixels[index]
            }
        val pixelIndex = firstDifference / 4
        val frameSize = expected.width * expected.height
        val frame = pixelIndex / frameSize
        val pixelInFrame = pixelIndex % frameSize
        val x = pixelInFrame % expected.width
        val y = pixelInFrame / expected.width
        return "pixel content differs at frame $frame, x=$x, y=$y"
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
            pixels =
                runBinaryChecked(
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
                    ),
                ),
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

    private fun runBinaryChecked(command: List<String>): ByteArray {
        val process =
            try {
                ProcessBuilder(command).directory(project.projectDir).start()
            } catch (error: IOException) {
                throw IllegalStateException(
                    "Failed to start command (${command.joinToString(" ")}): ${error.message}",
                    error,
                )
            }
        val output = process.inputStream.readBytes()
        val errorOutput = process.errorStream.bufferedReader().readText()
        val exit = process.waitFor()
        if (exit != 0) {
            throw IllegalStateException("Command failed (${command.joinToString(" ")}):\n$errorOutput")
        }
        return output
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
        val pixels: ByteArray,
    )
}
