package io.github.hdcodedev.composegif.android

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hdcodedev.composegif.core.GifCaptureValidator
import io.github.hdcodedev.composegif.core.GifScenarioRegistry
import io.github.hdcodedev.composegif.core.GifScenarioSpec
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.max

private const val DEFAULT_REGISTRY_CLASS = "io.github.hdcodedev.composegif.generated.GeneratedGifScenarioRegistry"
private const val DEFAULT_OUTPUT_SUBDIR = "gif-recorder"

/**
 * Instrumentation entry point that renders one generated scenario and writes its PNG frames to device storage.
 *
 * This test is invoked by the Gradle plugin via `am instrument`.
 */
@RunWith(AndroidJUnit4::class)
public class GifFrameCaptureTest {
    /** Compose test rule used to render and capture scenario frames. */
    @get:Rule
    public val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Captures the scenario selected via instrumentation arguments. */
    @Test
    public fun captureScenario() {
        val args = InstrumentationRegistry.getArguments()
        val registryClass = args.getString("registry_class") ?: DEFAULT_REGISTRY_CLASS
        val outputSubdir = args.getString("output_subdir") ?: DEFAULT_OUTPUT_SUBDIR
        val requestedScenario = args.getString("scenario_name")

        val registry = resolveRegistry(registryClass)
        val scenario = resolveScenario(registry, requestedScenario)
        GifCaptureValidator.validate(scenario)

        val frameStepMs = max(1L, 1000L / scenario.capture.fps.toLong())
        val frameCount =
            max(1, ((scenario.capture.durationMs.toLong() * scenario.capture.fps.toLong()) / 1000L).toInt())

        val outputDir = prepareOutputDirectory(outputSubdir, scenario.name)

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            registry.Render(scenario.name)
        }
        composeRule.waitForIdle()

        val captureNode = composeRule.onRoot(useUnmergedTree = true)
        var nextFrameIndex = 1
        if (scenario.capture.gestures.isNotEmpty()) {
            val interactionNodeTag = scenario.capture.interactionNodeTag
            check(interactionNodeTag.isNotBlank()) {
                "interactionNodeTag must be provided when gestures are configured."
            }
            val interactionNode = requireNodeWithTag(interactionNodeTag)
            nextFrameIndex =
                runGifGestureSteps(
                    interactionNode = interactionNode,
                    steps = scenario.capture.gestures,
                    totalFrames = frameCount,
                    startIndex = nextFrameIndex,
                    waitForIdle = { composeRule.waitForIdle() },
                    captureFrames = { frameCountToCapture, currentIndex ->
                        captureFrames(
                            captureNode = captureNode,
                            outputDir = outputDir,
                            startIndex = currentIndex,
                            frameCount = frameCountToCapture,
                            frameStepMs = frameStepMs,
                        )
                    },
                )
        }

        val remainingFrames = gifFramesRemaining(totalFrames = frameCount, nextFrameIndex = nextFrameIndex)
        if (remainingFrames > 0) {
            captureFrames(
                captureNode = captureNode,
                outputDir = outputDir,
                startIndex = nextFrameIndex,
                frameCount = remainingFrames,
                frameStepMs = frameStepMs,
            )
        }

        writeMetadata(
            outputDir = outputDir,
            scenario = scenario,
            frameCount = frameCount,
            frameStepMs = frameStepMs,
        )
    }

    private fun resolveRegistry(registryClass: String): GifScenarioRegistry {
        val clazz = Class.forName(registryClass)
        val instance = clazz.getField("INSTANCE").get(null)
        check(instance is GifScenarioRegistry) {
            "Registry class $registryClass does not implement GifScenarioRegistry."
        }
        return instance
    }

    private fun resolveScenario(
        registry: GifScenarioRegistry,
        requestedName: String?,
    ): GifScenarioSpec {
        val scenarios = registry.scenarios()
        check(scenarios.isNotEmpty()) {
            "No scenarios registered. Add @RecordGif to at least one @Composable function."
        }

        val name = requestedName ?: scenarios.first().name
        return scenarios.firstOrNull { it.name == name }
            ?: error("Scenario '$name' not found. Available: ${scenarios.map { it.name }.sorted()}")
    }

    private fun prepareOutputDirectory(
        outputSubdir: String,
        scenarioName: String,
    ): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val outputDir = File(baseDir, "$outputSubdir/$scenarioName")
        if (outputDir.exists()) {
            outputDir.deleteRecursively()
        }
        check(outputDir.mkdirs()) {
            "Failed to create output directory: $outputDir"
        }
        return outputDir
    }

    private fun requireNodeWithTag(tag: String): SemanticsNodeInteraction {
        val node = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
        node.fetchSemanticsNode()
        return node
    }

    private fun captureFrames(
        captureNode: SemanticsNodeInteraction,
        outputDir: File,
        startIndex: Int,
        frameCount: Int,
        frameStepMs: Long,
    ): Int {
        var frameIndex = startIndex
        if (frameCount <= 0) return frameIndex
        repeat(frameCount) {
            composeRule.mainClock.advanceTimeBy(frameStepMs)
            composeRule.waitForIdle()
            saveFrame(
                captureNode = captureNode,
                index = frameIndex,
                outputDir = outputDir,
            )
            frameIndex += 1
        }
        return frameIndex
    }

    private fun saveFrame(
        captureNode: SemanticsNodeInteraction,
        index: Int,
        outputDir: File,
    ) {
        val image = captureNode.captureToImage()
        val bitmap = image.asAndroidBitmap()
        val frameFile = File(outputDir, String.format(Locale.US, "frame-%04d.png", index))
        FileOutputStream(frameFile).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Failed to write frame: $frameFile"
            }
        }
    }

    private fun writeMetadata(
        outputDir: File,
        scenario: GifScenarioSpec,
        frameCount: Int,
        frameStepMs: Long,
    ) {
        val metadata =
            """
            scenario=${scenario.name}
            duration_ms=${scenario.capture.durationMs}
            fps=${scenario.capture.fps}
            width_px=${scenario.capture.widthPx}
            height_px=${scenario.capture.heightPx}
            theme=${scenario.capture.theme}
            interaction_node_tag=${scenario.capture.interactionNodeTag}
            gesture_count=${scenario.capture.gestures.size}
            frame_step_ms=$frameStepMs
            total_frames=$frameCount
            """.trimIndent()
        File(outputDir, "metadata.txt").writeText(metadata)
    }
}
