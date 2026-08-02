package io.github.hdcodedev.composegif.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
import kotlin.math.max

internal abstract class RecordGifTask : DefaultTask() {
    init {
        outputs.upToDateWhen { false }
    }

    @get:Input
    public abstract val applicationId: Property<String>

    @get:OutputDirectory
    public abstract val outputDir: DirectoryProperty

    @get:Input
    public abstract val adbSerial: Property<String>

    @get:Input
    public abstract val adbBin: Property<String>

    @get:Input
    public abstract val ffmpegBin: Property<String>

    @get:Input
    public abstract val ffprobeBin: Property<String>

    @get:Input
    public abstract val gifsicleBin: Property<String>

    @get:Input
    public abstract val scenario: Property<String>

    @get:Input
    public abstract val registryClass: Property<String>

    @get:Input
    public abstract val testClass: Property<String>

    @get:Input
    public abstract val gifWidth: Property<Int>

    @get:Input
    public abstract val gifHeight: Property<Int>

    @get:Input
    public abstract val allScenarios: Property<Boolean>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val scenarioMetadataFile: RegularFileProperty

    @TaskAction
    public fun record() {
        val metadataFile = scenarioMetadataFile.get().asFile
        if (!metadataFile.exists()) {
            throw IllegalStateException(
                "Generated scenario metadata not found at ${metadataFile.path}. Run kspDebugKotlin first.",
            )
        }

        val scenarios = parseScenarioMetadata(metadataFile)
        if (scenarios.isEmpty()) {
            throw IllegalStateException("No scenarios found to record.")
        }

        val resolvedAdbBin = resolveAdbBinary()
        ensureBinaryExists(resolvedAdbBin)
        ensureBinaryExists(ffmpegBin.get())
        ensureBinaryExists(ffprobeBin.get())
        ensureBinaryExists(gifsicleBin.get())

        val selected = selectedScenarios(scenarios)
        val serial = resolveDeviceSerial(resolvedAdbBin, adbSerial.get())
        val adbPrefix = listOf(resolvedAdbBin, "-s", serial)

        val outputRoot = outputDir.get().asFile
        outputRoot.mkdirs()

        selected.forEach { scenarioName ->
            val metadata = scenarios.firstOrNull { it.name == scenarioName }
            if (metadata == null) {
                throw IllegalStateException(
                    "Scenario '$scenarioName' not found. Available: ${scenarios.map { it.name }}",
                )
            }
            recordScenario(
                scenarioName = scenarioName,
                fps = metadata.fps,
                adbPrefix = adbPrefix,
                outputRoot = outputRoot,
            )
        }
    }

    private fun selectedScenarios(scenarios: List<GifScenarioMetadata>): List<String> =
        if (allScenarios.get()) {
            scenarios.map { it.name }
        } else {
            val requested = scenario.get().takeIf { it.isNotBlank() && it != "all" }
            listOf(requested ?: scenarios.first().name)
        }

    private fun recordScenario(
        scenarioName: String,
        fps: Int,
        adbPrefix: List<String>,
        outputRoot: File,
    ) {
        logger.lifecycle("Recording scenario '$scenarioName' on device '${adbPrefix.last()}'")
        runChecked(
            adbPrefix +
                listOf(
                    "shell",
                    "am",
                    "instrument",
                    "-w",
                    "-e",
                    "class",
                    "${testClass.get()}#captureScenario",
                    "-e",
                    "registry_class",
                    registryClass.get(),
                    "-e",
                    "scenario_name",
                    scenarioName,
                    "-e",
                    "output_subdir",
                    DEFAULT_REMOTE_SUBDIR,
                    "${applicationId.get()}.test/androidx.test.runner.AndroidJUnitRunner",
                ),
        )

        val localScenarioDir =
            File(temporaryDir, "frames/$scenarioName").apply {
                deleteRecursively()
                mkdirs()
            }
        val remoteScenarioDir =
            "/sdcard/Android/data/${applicationId.get()}/files/$DEFAULT_REMOTE_SUBDIR/$scenarioName"
        runChecked(adbPrefix + listOf("pull", "$remoteScenarioDir/.", localScenarioDir.absolutePath))

        val frames =
            localScenarioDir
                .listFiles { file ->
                    file.name.matches(Regex("frame-\\d{4}\\.png"))
                }?.sortedBy { it.name }
                ?: emptyList()
        if (frames.isEmpty()) {
            throw IllegalStateException("No frames found for scenario '$scenarioName'.")
        }

        val effectiveHeight = resolveCanvasHeight(frames)
        val normalizedDir =
            File(temporaryDir, "normalized/$scenarioName").apply {
                deleteRecursively()
                mkdirs()
            }
        normalizeFrames(localScenarioDir, normalizedDir, effectiveHeight, fps)

        val palette = File(temporaryDir, "$scenarioName.palette.png")
        val baseGif = File(temporaryDir, "$scenarioName.base.gif")
        val finalGif = File(outputRoot, "$scenarioName.gif")
        createPalette(normalizedDir, palette, fps)
        createBaseGif(normalizedDir, palette, baseGif, fps)
        optimizeGif(baseGif, finalGif)
        logger.lifecycle("Generated GIF: ${finalGif.absolutePath}")
    }

    private fun normalizeFrames(
        sourceDir: File,
        normalizedDir: File,
        height: Int,
        fps: Int,
    ) {
        val scaleFilter =
            "scale=${gifWidth.get()}:$height:flags=lanczos:force_original_aspect_ratio=decrease," +
                "pad=${gifWidth.get()}:$height:(ow-iw)/2:(oh-ih)/2:color=black,format=rgb24"
        runChecked(
            listOf(
                ffmpegBin.get(),
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                "-framerate",
                fps.toString(),
                "-i",
                "${sourceDir.absolutePath}/frame-%04d.png",
                "-vf",
                scaleFilter,
                "${normalizedDir.absolutePath}/frame-%04d.png",
            ),
        )
    }

    private fun createPalette(
        normalizedDir: File,
        palette: File,
        fps: Int,
    ) {
        runChecked(
            listOf(
                ffmpegBin.get(),
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                "-framerate",
                fps.toString(),
                "-i",
                "${normalizedDir.absolutePath}/frame-%04d.png",
                "-vf",
                "palettegen=stats_mode=full",
                "-frames:v",
                "1",
                palette.absolutePath,
            ),
        )
    }

    private fun createBaseGif(
        normalizedDir: File,
        palette: File,
        baseGif: File,
        fps: Int,
    ) {
        runChecked(
            listOf(
                ffmpegBin.get(),
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                "-framerate",
                fps.toString(),
                "-i",
                "${normalizedDir.absolutePath}/frame-%04d.png",
                "-i",
                palette.absolutePath,
                "-lavfi",
                "paletteuse=dither=bayer:bayer_scale=3:diff_mode=rectangle",
                baseGif.absolutePath,
            ),
        )
    }

    private fun optimizeGif(
        baseGif: File,
        finalGif: File,
    ) {
        runChecked(
            listOf(
                gifsicleBin.get(),
                "--no-warnings",
                "--optimize=3",
                "--lossy=0",
                "--colors",
                "256",
                baseGif.absolutePath,
                "-o",
                finalGif.absolutePath,
            ),
        )
    }

    private fun resolveDeviceSerial(
        adb: String,
        configuredSerial: String,
    ): String {
        if (configuredSerial != "auto") return configuredSerial
        val output = runChecked(listOf(adb, "devices"))
        val devices =
            output
                .lineSequence()
                .drop(1)
                .map { it.trim() }
                .filter { it.endsWith("\tdevice") }
                .map { it.substringBefore("\t") }
                .toList()
        if (devices.isEmpty()) {
            throw IllegalStateException("No connected Android device/emulator found.")
        }
        if (devices.size > 1) {
            throw IllegalStateException("Multiple devices found: $devices. Configure gifRecorder.adbSerial.")
        }
        return devices.first()
    }

    private fun resolveCanvasHeight(frames: List<File>): Int {
        val configuredHeight = gifHeight.get()
        if (configuredHeight > 0) return configuredHeight

        var maxScaledHeight = 0
        frames.forEach { frame ->
            val dims =
                runChecked(
                    listOf(
                        ffprobeBin.get(),
                        "-v",
                        "error",
                        "-select_streams",
                        "v:0",
                        "-show_entries",
                        "stream=width,height",
                        "-of",
                        "csv=p=0:s=x",
                        frame.absolutePath,
                    ),
                ).trim()
            val parts = dims.split("x")
            check(parts.size == 2) { "Could not resolve frame dimensions from $dims" }
            val width = parts[0].toInt()
            val height = parts[1].toInt()
            val scaled = (height * gifWidth.get() + width - 1) / width
            if (scaled > maxScaledHeight) maxScaledHeight = scaled
        }

        return max(1, maxScaledHeight)
    }

    private fun resolveAdbBinary(): String {
        val configured = adbBin.get().trim()
        if (configured.isBlank()) {
            throw IllegalStateException("gifRecorder.adbBin cannot be blank.")
        }

        val configuredFile = File(configured)
        if (configuredFile.isAbsolute || configured.contains(File.separatorChar)) {
            return configured
        }

        val executableName = if (isWindows()) "adb.exe" else "adb"
        if (configured != executableName && configured != "adb") {
            return configured
        }

        findExecutableOnPath(executableName)?.let { return it }

        val userHome = System.getProperty("user.home").orEmpty()
        val candidates =
            buildList {
                addSdkCandidate(System.getenv("ANDROID_SDK_ROOT"), executableName)
                addSdkCandidate(System.getenv("ANDROID_HOME"), executableName)
                addSdkCandidate("$userHome/Library/Android/sdk", executableName)
                addSdkCandidate("$userHome/Android/Sdk", executableName)
            }.distinct()

        val matched = candidates.firstOrNull { File(it).exists() }
        if (matched != null) return matched

        throw IllegalStateException(
            buildString {
                append("Could not locate adb automatically. ")
                append("Install Android SDK platform-tools or set gifRecorder.adbBin to an absolute adb path. ")
                append("Checked ANDROID_SDK_ROOT, ANDROID_HOME, and common SDK paths: ")
                append(candidates.joinToString())
            },
        )
    }

    private fun findExecutableOnPath(binaryName: String): String? {
        val pathValue = System.getenv("PATH") ?: return null
        val separator = File.pathSeparatorChar
        return pathValue
            .split(separator)
            .asSequence()
            .map { File(it, binaryName) }
            .firstOrNull { it.exists() && it.isFile }
            ?.absolutePath
    }

    private fun MutableList<String>.addSdkCandidate(
        sdkRoot: String?,
        executableName: String,
    ) {
        if (sdkRoot.isNullOrBlank()) return
        add(File(sdkRoot, "platform-tools/$executableName").absolutePath)
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").contains("win", ignoreCase = true)

    private fun ensureBinaryExists(binary: String) {
        val resolved =
            if (binary.contains(File.separatorChar)) {
                File(binary).takeIf { it.exists() && it.isFile }?.absolutePath
            } else {
                findExecutableOnPath(binary)
            }
        if (resolved == null) {
            throw IllegalStateException("Binary not found or not executable: $binary")
        }
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
}

internal fun RecordGifTask.configureFromExtension(
    project: Project,
    extension: GifRecorderExtension,
) {
    applicationId.convention(extension.applicationId)
    outputDir.convention(extension.outputDir)
    adbSerial.convention(extension.adbSerial)
    adbBin.convention(extension.adbBin)
    ffmpegBin.convention(extension.ffmpegBin)
    ffprobeBin.convention(extension.ffprobeBin)
    gifsicleBin.convention(extension.gifsicleBin)
    scenario.convention(project.providers.gradleProperty("gifScenario").orElse(extension.scenario))
    registryClass.convention(extension.registryClass)
    testClass.convention(extension.testClass)
    gifWidth.convention(extension.gifWidth)
    gifHeight.convention(extension.gifHeight)
    scenarioMetadataFile.convention(project.layout.buildDirectory.file(GENERATED_SCENARIO_METADATA_FILE))
}
