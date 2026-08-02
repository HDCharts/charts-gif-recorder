package io.github.hdcodedev.composegif.annotations

/**
 * Marks a parameterless `@Composable` function as a GIF capture scenario.
 *
 * @property name Optional explicit scenario name. When blank, the KSP processor derives it from the function name.
 * @property durationMs Target recording duration in milliseconds.
 * @property fps Capture frame rate used for frame extraction.
 * @property widthPx Output width in pixels before GIF encoding.
 * @property heightPx Output height in pixels. Use `0` to keep aspect ratio and auto-resolve height.
 * @property theme Scenario theme metadata written into generated configuration.
 * @property interactionStartDelayMs Delay before replaying configured gestures or interactions.
 * @property interactionNodeTag Node tag used as the gesture target root.
 * @property interactions High-level interactions expanded into low-level gesture steps.
 * @property gestures Explicit low-level gesture steps to replay during capture.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class RecordGif(
    val name: String = "",
    val durationMs: Int = 3000,
    val fps: Int = 50,
    val widthPx: Int = 540,
    val heightPx: Int = 0,
    val theme: GifTheme = GifTheme.DARK,
    /**
     * Delay before replaying configured interactions/gestures.
     *
     * Useful for letting first-render animations settle before input starts.
     */
    val interactionStartDelayMs: Int = 1000,
    val interactionNodeTag: String = "",
    val interactions: Array<GifInteraction> = [],
    val gestures: Array<GifGestureStep> = [],
)

/** Available visual themes for generated GIF scenarios. */
enum class GifTheme {
    LIGHT,
    DARK,
}

/** Gesture primitives used by low-level replay steps. */
enum class GifGestureType {
    PAUSE,
    TAP,
    DRAG_PATH,
}

/** High-level interaction primitives that are expanded into gesture steps. */
public enum class GifInteractionType {
    PAUSE,
    TAP,
    SWIPE,
}

/** Target lane or point used to position interactions. */
public enum class GifInteractionTarget {
    CENTER,
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
}

/** Supported swipe directions for high-level swipe interactions. */
public enum class GifSwipeDirection {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
    TOP_TO_BOTTOM,
    BOTTOM_TO_TOP,
}

/** Supported swipe distances for high-level swipe interactions. */
public enum class GifSwipeDistance {
    SHORT,
    MEDIUM,
    LONG,
}

/** Preset timing speeds used by swipe interactions. */
public enum class GifSwipeSpeed {
    CUSTOM,
    FAST,
    NORMAL,
    SLOW,
}

/**
 * A high-level interaction step expanded by the KSP processor into one or more low-level gestures.
 *
 * @property type Interaction operation to perform.
 * @property frames Pause duration for `PAUSE`.
 * @property framesAfter Extra pause frames after the interaction.
 * @property target Target lane or point used by tap and swipe interactions.
 * @property direction Swipe direction when [type] is `SWIPE`.
 * @property distance Swipe travel distance when [type] is `SWIPE`.
 * @property speed Swipe timing preset.
 * @property travelFrames Explicit swipe travel frames when [speed] is `CUSTOM`.
 * @property holdStartFrames Explicit hold frames before travel when [speed] is `CUSTOM`.
 * @property releaseFrames Explicit hold frames after release when [speed] is `CUSTOM`.
 */
public annotation class GifInteraction(
    val type: GifInteractionType = GifInteractionType.PAUSE,
    val frames: Int = 0,
    val framesAfter: Int = 0,
    /**
     * Target lane for the interaction.
     *
     * For taps, this is the tap point.
     *
     * For swipes, this selects the lane perpendicular to the swipe direction:
     * - horizontal swipes (`LEFT_TO_RIGHT`, `RIGHT_TO_LEFT`) use `TOP`/`BOTTOM` for Y lane,
     * - vertical swipes (`TOP_TO_BOTTOM`, `BOTTOM_TO_TOP`) use `LEFT`/`RIGHT` for X lane,
     * - other values fall back to center lane.
     */
    val target: GifInteractionTarget = GifInteractionTarget.CENTER,
    val direction: GifSwipeDirection = GifSwipeDirection.LEFT_TO_RIGHT,
    val distance: GifSwipeDistance = GifSwipeDistance.MEDIUM,
    /**
     * High-level swipe timing preset.
     *
     * Use `CUSTOM` to control timing with `travelFrames`, `holdStartFrames`, and `releaseFrames`.
     */
    val speed: GifSwipeSpeed = GifSwipeSpeed.CUSTOM,
    val travelFrames: Int = 8,
    val holdStartFrames: Int = 0,
    val releaseFrames: Int = 0,
)

/**
 * A normalized point in the interaction node coordinate space.
 *
 * @property x Horizontal fraction in range `[0.0, 1.0]`.
 * @property y Vertical fraction in range `[0.0, 1.0]`.
 */
public annotation class GifFractionPoint(
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
public annotation class GifGestureStep(
    val type: GifGestureType = GifGestureType.PAUSE,
    val frames: Int = 0,
    val xFraction: Float = 0.5f,
    val yFraction: Float = 0.5f,
    val framesAfter: Int = 0,
    val points: Array<GifFractionPoint> = [],
    val holdStartFrames: Int = 0,
    val framesPerWaypoint: Int = 0,
    val releaseFrames: Int = 0,
)
