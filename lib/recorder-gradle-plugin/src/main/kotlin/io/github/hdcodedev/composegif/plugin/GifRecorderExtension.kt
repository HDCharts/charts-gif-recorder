package io.github.hdcodedev.composegif.plugin

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Configuration DSL for the Compose GIF Recorder Gradle plugin.
 *
 * @property applicationId Android application ID used for instrumentation and device pull paths.
 * @property outputDir Directory where generated GIFs are written.
 * @property adbSerial Device serial, or `auto` to pick a single connected device.
 * @property adbBin `adb` executable path or command name.
 * @property ffmpegBin `ffmpeg` executable path or command name.
 * @property ffprobeBin `ffprobe` executable path or command name.
 * @property gifsicleBin `gifsicle` executable path or command name.
 * @property scenario Scenario name to capture, or `all`.
 * @property registryClass Generated registry class name used by instrumentation.
 * @property testClass Instrumentation test class used for frame capture.
 * @property libraryVersion Recorder library version injected into app dependencies.
 * @property gifWidth Output GIF width in pixels.
 * @property gifHeight Output GIF height in pixels. Use `0` for auto height.
 * @property canvasBackgroundColor Background color used to fill unused areas when frames are normalized to a common canvas.
 * @property baselineDir Directory containing source-controlled GIF baselines.
 * @property validationReportDir Directory where baseline validation reports are written.
 * @property maxChangedPixelPercentage Maximum percentage of pixels allowed to differ
 * between a generated GIF and its baseline for each frame. For example, `1.0` allows
 * 1% of pixels to differ. `0.0` keeps exact pixel comparison.
 * @property gifDither Dither mode passed to ffmpeg's `paletteuse` filter (the value after
 * `dither=`), for example `none`, `bayer:bayer_scale=3`, or `floyd_steinberg`. Ordered dithers
 * such as `bayer` spread quantization error across a fixed pixel pattern to fake extra color
 * steps; that pattern compresses far worse than a flat run under GIF's LZW coding, so scenarios
 * with large moving or textured regions benefit from `none`.
 * @property gifsicleLossy Value passed to gifsicle's `--lossy` flag. `0` (the default) keeps
 * lossless optimization; higher values let gifsicle merge visually similar pixels for a smaller
 * file at the cost of exactness, and should be chosen by comparing output at a few values rather
 * than assumed.
 * @property gifsicleColors Value passed to gifsicle's `--colors` flag: the maximum size of the
 * output color table. `256` is the GIF format maximum and also gifsicle's default.
 */
abstract class GifRecorderExtension
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        val applicationId: Property<String> = objects.property(String::class.java)
        val outputDir: DirectoryProperty = objects.directoryProperty()
        val adbSerial: Property<String> = objects.property(String::class.java)
        val adbBin: Property<String> = objects.property(String::class.java)
        val ffmpegBin: Property<String> = objects.property(String::class.java)
        val ffprobeBin: Property<String> = objects.property(String::class.java)
        val gifsicleBin: Property<String> = objects.property(String::class.java)
        val scenario: Property<String> = objects.property(String::class.java)
        val registryClass: Property<String> = objects.property(String::class.java)
        val testClass: Property<String> = objects.property(String::class.java)
        val libraryVersion: Property<String> = objects.property(String::class.java)
        val gifWidth: Property<Int> = objects.property(Int::class.java)
        val gifHeight: Property<Int> = objects.property(Int::class.java)
        val canvasBackgroundColor: Property<String> = objects.property(String::class.java)
        val baselineDir: DirectoryProperty = objects.directoryProperty()
        val validationReportDir: DirectoryProperty = objects.directoryProperty()
        val maxChangedPixelPercentage: Property<Double> = objects.property(Double::class.java)
        val gifDither: Property<String> = objects.property(String::class.java)
        val gifsicleLossy: Property<Int> = objects.property(Int::class.java)
        val gifsicleColors: Property<Int> = objects.property(Int::class.java)
    }
