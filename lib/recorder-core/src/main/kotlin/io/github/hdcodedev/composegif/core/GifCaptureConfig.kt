package io.github.hdcodedev.composegif.core

/** Available visual themes for GIF capture scenarios. */
enum class GifTheme {
    LIGHT,
    DARK,
}

/** Gesture primitives used during deterministic replay. */
enum class GifGestureType {
    PAUSE,
    TAP,
    DRAG_PATH,
}

/**
 * A normalized point in the interaction node coordinate space.
 *
 * @property x Horizontal fraction in range `[0.0, 1.0]`.
 * @property y Vertical fraction in range `[0.0, 1.0]`.
 */
data class GifFractionPoint(
    val x: Float,
    val y: Float,
)

/**
 * A low-level gesture replay step.
 *
 * @property type Gesture operation to perform.
 * @property frames Pause frame count for `PAUSE`.
 * @property xFraction Horizontal fraction for `TAP`.
 * @property yFraction Vertical fraction for `TAP`.
 * @property framesAfter Pause frames after a `TAP`.
 * @property points Drag path points for `DRAG_PATH`.
 * @property holdStartFrames Pause frames after pointer down for `DRAG_PATH`.
 * @property framesPerWaypoint Interpolation frames between drag waypoints.
 * @property releaseFrames Pause frames after pointer up for `DRAG_PATH`.
 */
data class GifGestureStep(
    val type: GifGestureType = GifGestureType.PAUSE,
    val frames: Int = 0,
    val xFraction: Float = 0.5f,
    val yFraction: Float = 0.5f,
    val framesAfter: Int = 0,
    val points: List<GifFractionPoint> = emptyList(),
    val holdStartFrames: Int = 0,
    val framesPerWaypoint: Int = 0,
    val releaseFrames: Int = 0,
)

/**
 * Capture configuration for a single GIF scenario.
 *
 * @property durationMs Target recording duration in milliseconds.
 * @property fps Capture frame rate used for frame extraction.
 * @property widthPx Output width in pixels before GIF encoding.
 * @property heightPx Output height in pixels. Use `0` to auto-resolve height.
 * @property theme Scenario theme metadata.
 * @property interactionNodeTag Node tag used as the gesture target root.
 * @property gestures Low-level gesture steps replayed during capture.
 */
data class GifCaptureConfig(
    val durationMs: Int = 3000,
    val fps: Int = 50,
    val widthPx: Int = 540,
    val heightPx: Int = 0,
    val theme: GifTheme = GifTheme.DARK,
    val interactionNodeTag: String = "",
    val gestures: List<GifGestureStep> = emptyList(),
)

/**
 * A named scenario and its capture configuration.
 *
 * @property name Scenario identifier used by generated registries and tooling.
 * @property capture Capture configuration for this scenario.
 */
data class GifScenarioSpec(
    val name: String,
    val capture: GifCaptureConfig,
)

/** Thrown when a scenario name or capture configuration fails validation. */
class GifValidationException(
    message: String,
) : IllegalArgumentException(message)

/** Validation helpers for scenario specs and capture configs. */
object GifCaptureValidator {
    /**
     * Validates a complete scenario specification.
     *
     * @throws GifValidationException if the spec is invalid.
     */
    fun validate(spec: GifScenarioSpec) {
        requireName(spec.name)
        validate(spec.capture)
    }

    /**
     * Validates capture configuration constraints.
     *
     * @throws GifValidationException if the config is invalid.
     */
    fun validate(config: GifCaptureConfig) {
        if (config.durationMs <= 0) {
            throw GifValidationException("durationMs must be greater than 0.")
        }
        if (config.fps !in 1..120) {
            throw GifValidationException("fps must be in range [1, 120].")
        }
        if (config.widthPx <= 0) {
            throw GifValidationException("widthPx must be greater than 0.")
        }
        if (config.heightPx < 0) {
            throw GifValidationException("heightPx must be 0 (auto) or greater.")
        }
        if (config.interactionNodeTag.isBlank() && config.gestures.isNotEmpty()) {
            throw GifValidationException("interactionNodeTag must be provided when gestures are configured.")
        }
        config.gestures.forEachIndexed { index, gesture ->
            validateGesture(index, gesture)
        }
    }

    /**
     * Validates a scenario name.
     *
     * @throws GifValidationException if the name is blank or has unsupported characters.
     */
    fun requireName(name: String) {
        if (name.isBlank()) {
            throw GifValidationException("Scenario name cannot be blank.")
        }
        if (!name.matches(Regex("[a-zA-Z0-9_\\-]+"))) {
            throw GifValidationException("Scenario name must match [a-zA-Z0-9_-]+.")
        }
    }

    private fun validateGesture(
        index: Int,
        gesture: GifGestureStep,
    ) {
        when (gesture.type) {
            GifGestureType.PAUSE -> {
                if (gesture.frames < 0) {
                    throw GifValidationException("Gesture[$index] pause frames must be >= 0.")
                }
            }
            GifGestureType.TAP -> {
                requireFraction(index, "xFraction", gesture.xFraction)
                requireFraction(index, "yFraction", gesture.yFraction)
                if (gesture.framesAfter < 0) {
                    throw GifValidationException("Gesture[$index] tap framesAfter must be >= 0.")
                }
            }
            GifGestureType.DRAG_PATH -> {
                if (gesture.points.size < 2) {
                    throw GifValidationException("Gesture[$index] drag path must contain at least 2 points.")
                }
                gesture.points.forEachIndexed { pointIndex, point ->
                    requireFraction(index, "points[$pointIndex].x", point.x)
                    requireFraction(index, "points[$pointIndex].y", point.y)
                }
                if (gesture.holdStartFrames < 0) {
                    throw GifValidationException("Gesture[$index] drag holdStartFrames must be >= 0.")
                }
                if (gesture.framesPerWaypoint < 0) {
                    throw GifValidationException("Gesture[$index] drag framesPerWaypoint must be >= 0.")
                }
                if (gesture.releaseFrames < 0) {
                    throw GifValidationException("Gesture[$index] drag releaseFrames must be >= 0.")
                }
            }
        }
    }

    private fun requireFraction(
        gestureIndex: Int,
        field: String,
        value: Float,
    ) {
        if (!value.isFinite() || value < 0f || value > 1f) {
            throw GifValidationException("Gesture[$gestureIndex] $field must be in range [0.0, 1.0].")
        }
    }
}
