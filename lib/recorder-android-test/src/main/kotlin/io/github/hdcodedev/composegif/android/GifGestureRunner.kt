package io.github.hdcodedev.composegif.android

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import io.github.hdcodedev.composegif.core.GifGestureStep
import io.github.hdcodedev.composegif.core.GifGestureType
import kotlin.math.min

internal fun runGifGestureSteps(
    interactionNode: SemanticsNodeInteraction,
    steps: List<GifGestureStep>,
    totalFrames: Int,
    startIndex: Int,
    waitForIdle: () -> Unit,
    captureFrames: (frameCount: Int, startIndex: Int) -> Int,
): Int {
    var nextFrameIndex = startIndex
    var pointerActive = false
    try {
        for (step in steps) {
            if (gifFramesRemaining(totalFrames, nextFrameIndex) <= 0) break
            when (step.type) {
                GifGestureType.PAUSE -> {
                    val framesToCapture =
                        min(step.frames.coerceAtLeast(0), gifFramesRemaining(totalFrames, nextFrameIndex))
                    nextFrameIndex = captureFrames(framesToCapture, nextFrameIndex)
                }
                GifGestureType.TAP -> {
                    val target = fractionToOffset(interactionNode, step.xFraction, step.yFraction)
                    interactionNode.performTouchInput {
                        click(target)
                    }
                    waitForIdle()
                    val framesToCapture =
                        min(step.framesAfter.coerceAtLeast(0), gifFramesRemaining(totalFrames, nextFrameIndex))
                    nextFrameIndex = captureFrames(framesToCapture, nextFrameIndex)
                }
                GifGestureType.DRAG_PATH -> {
                    val points = step.points
                    if (points.size < 2) continue

                    val startPoint = points.first()
                    val startOffset = fractionToOffset(interactionNode, startPoint.x, startPoint.y)
                    interactionNode.performTouchInput {
                        down(startOffset)
                    }
                    pointerActive = true
                    waitForIdle()

                    val holdFrames =
                        min(step.holdStartFrames.coerceAtLeast(0), gifFramesRemaining(totalFrames, nextFrameIndex))
                    nextFrameIndex = captureFrames(holdFrames, nextFrameIndex)

                    var previousOffset = startOffset
                    val waypointFrames = step.framesPerWaypoint.coerceAtLeast(0)
                    for (point in points.drop(1)) {
                        if (gifFramesRemaining(totalFrames, nextFrameIndex) <= 0) break
                        val targetOffset = fractionToOffset(interactionNode, point.x, point.y)
                        if (waypointFrames == 0) {
                            interactionNode.performTouchInput {
                                moveTo(targetOffset)
                            }
                            waitForIdle()
                        } else {
                            repeat(waypointFrames) { frame ->
                                if (gifFramesRemaining(totalFrames, nextFrameIndex) <= 0) return@repeat
                                val progress = (frame + 1).toFloat() / waypointFrames.toFloat()
                                val interpolated = interpolateOffset(previousOffset, targetOffset, progress)
                                interactionNode.performTouchInput {
                                    moveTo(interpolated)
                                }
                                waitForIdle()
                                nextFrameIndex = captureFrames(1, nextFrameIndex)
                            }
                        }
                        previousOffset = targetOffset
                    }

                    interactionNode.performTouchInput { up() }
                    pointerActive = false
                    waitForIdle()

                    val releaseFrames =
                        min(step.releaseFrames.coerceAtLeast(0), gifFramesRemaining(totalFrames, nextFrameIndex))
                    nextFrameIndex = captureFrames(releaseFrames, nextFrameIndex)
                }
            }
        }
    } finally {
        if (pointerActive) {
            runCatching {
                interactionNode.performTouchInput { cancel() }
            }
            waitForIdle()
        }
    }
    return nextFrameIndex
}

internal fun gifFramesRemaining(
    totalFrames: Int,
    nextFrameIndex: Int,
): Int = totalFrames - (nextFrameIndex - 1)

internal fun fractionToOffset(
    interactionNode: SemanticsNodeInteraction,
    xFraction: Float,
    yFraction: Float,
): Offset {
    val bounds = interactionNode.fetchSemanticsNode().boundsInRoot
    return Offset(
        x = bounds.width * xFraction.coerceIn(0f, 1f),
        y = bounds.height * yFraction.coerceIn(0f, 1f),
    )
}

internal fun interpolateOffset(
    start: Offset,
    end: Offset,
    progress: Float,
): Offset {
    val clamped = progress.coerceIn(0f, 1f)
    return Offset(
        x = start.x + ((end.x - start.x) * clamped),
        y = start.y + ((end.y - start.y) * clamped),
    )
}
