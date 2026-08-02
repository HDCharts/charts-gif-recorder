package io.github.hdcodedev.composegif.android

import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hdcodedev.composegif.core.GifFractionPoint
import io.github.hdcodedev.composegif.core.GifGestureStep
import io.github.hdcodedev.composegif.core.GifGestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GifGestureRunnerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun executesTapAndDragGestures() {
        val tapCount = mutableIntStateOf(0)
        val dragEventCount = mutableIntStateOf(0)
        val surfaceTag = "GestureSurface"

        composeRule.setContent {
            Box(
                modifier =
                    Modifier
                        .size(220.dp)
                        .testTag(surfaceTag)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { tapCount.intValue += 1 },
                            )
                        }.pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { _, _ -> dragEventCount.intValue += 1 },
                            )
                        },
            )
        }
        composeRule.waitForIdle()

        val node = composeRule.onNodeWithTag(surfaceTag, useUnmergedTree = true)
        node.fetchSemanticsNode()

        val nextFrameIndex =
            runGifGestureSteps(
                interactionNode = node,
                steps =
                    listOf(
                        GifGestureStep(type = GifGestureType.PAUSE, frames = 3),
                        GifGestureStep(type = GifGestureType.TAP, xFraction = 0.5f, yFraction = 0.5f, framesAfter = 2),
                        GifGestureStep(
                            type = GifGestureType.DRAG_PATH,
                            points =
                                listOf(
                                    GifFractionPoint(x = 0.20f, y = 0.60f),
                                    GifFractionPoint(x = 0.50f, y = 0.60f),
                                    GifFractionPoint(x = 0.80f, y = 0.60f),
                                ),
                            holdStartFrames = 1,
                            framesPerWaypoint = 2,
                            releaseFrames = 1,
                        ),
                    ),
                totalFrames = 20,
                startIndex = 1,
                waitForIdle = { composeRule.waitForIdle() },
                captureFrames = { frameCount, startIndex -> startIndex + frameCount },
            )

        assertEquals(12, nextFrameIndex)
        composeRule.runOnIdle {
            assertTrue(tapCount.intValue >= 1)
            assertTrue(dragEventCount.intValue >= 1)
        }
    }

    @Test
    fun respectsTotalFrameBudget() {
        val surfaceTag = "BudgetSurface"

        composeRule.setContent {
            Box(
                modifier =
                    Modifier
                        .size(220.dp)
                        .testTag(surfaceTag)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {})
                        },
            )
        }
        composeRule.waitForIdle()

        val node = composeRule.onNodeWithTag(surfaceTag, useUnmergedTree = true)
        node.fetchSemanticsNode()

        val nextFrameIndex =
            runGifGestureSteps(
                interactionNode = node,
                steps =
                    listOf(
                        GifGestureStep(type = GifGestureType.PAUSE, frames = 10),
                        GifGestureStep(type = GifGestureType.TAP, xFraction = 0.5f, yFraction = 0.5f, framesAfter = 10),
                    ),
                totalFrames = 4,
                startIndex = 1,
                waitForIdle = { composeRule.waitForIdle() },
                captureFrames = { frameCount, startIndex -> startIndex + frameCount },
            )

        assertEquals(5, nextFrameIndex)
    }
}
