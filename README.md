<h1 align="center">compose-gif-recorder</h1>

<p align="center">
  <a href="https://central.sonatype.com/artifact/io.github.hdcodedev/compose-gif-recorder-gradle-plugin">
    <img src="https://img.shields.io/maven-central/v/io.github.hdcodedev/compose-gif-recorder-gradle-plugin?label=Release&color=2E7D32" />
  </a>
  <a href="https://central.sonatype.com/repository/maven-snapshots/io/github/hdcodedev/compose-gif-recorder-gradle-plugin/maven-metadata.xml">
    <img src="https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fio%2Fgithub%2Fhdcodedev%2Fcompose-gif-recorder-gradle-plugin%2Fmaven-metadata.xml&label=Snapshots&color=4285F4" />
  </a>
  <img src="https://img.shields.io/badge/Jetpack_Compose_BOM-2026.06.01-4285F4?logo=jetpackcompose" />
  <img src="https://img.shields.io/badge/Kotlin-2.4.10-0095D5?logo=kotlin" />
  <img src="https://img.shields.io/badge/AGP-9.3.1-2E7D32?logo=android" />
  <img src="https://img.shields.io/badge/KSP-2.3.10-FF6F00" />
</p>

<p align="center">
  Deterministic GIF recording for Jetpack Compose.
</p>

## Motivation

This plugin was originally created to automate GIF generation
for the [HDCharts wiki documentation](https://charts.hdcode.dev/).

Whenever chart styles, animations, or APIs change,
all documentation GIFs can be easily regenerated in an automated way.

## Documentation

- [API documentation](https://hdcharts.github.io/charts-gif-recorder/)
- [Snapshot API documentation](https://hdcharts.github.io/charts-gif-recorder/snapshot/)

To use a snapshot version, add the Maven Central snapshots repository to
`settings.gradle.kts` and set `compose-gif-recorder` in your version catalog to
the version shown by the snapshot badge above:

```kotlin
pluginManagement {
    repositories {
        google()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
        mavenCentral()
        gradlePluginPortal()
    }
}
```

## Requirements

- Android app module using Jetpack Compose
- Gradle plugins: Android application, Kotlin Compose, KSP
- Installed tools on `PATH`: `adb`, `ffmpeg`, `ffprobe`, `gifsicle`
- Running emulator or connected Android device

## Generate baselines in CI (recommended)

> **Recommended:** generate GIFs in CI when creating or updating committed
> baselines. The [pull request workflow](.github/workflows/pull_request.yml)
> runs the [GIF baseline validation workflow](.github/workflows/validate-gifs.yml)
> on a controlled Android emulator with pinned recording tools, keeping output
> deterministic across machines.

If validation fails, download the `gif-validation-*` artifact to review the
newly generated GIFs and the comparison report. If the visual change is
intentional, replace the committed baseline files with the reviewed CI GIFs and
commit them to the pull request.

## Use In Your App

### 1. Apply plugins in your app module

Use a Version Catalog (`gradle/libs.versions.toml`):

```toml
[versions]
compose-gif-recorder = "<version>"
composeUi = "<Compose UI version>"

[plugins]
composeGifRecorder = { id = "io.github.hdcodedev.compose-gif-recorder", version.ref = "compose-gif-recorder" }
```

Then in your app module `build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    alias(libs.plugins.composeGifRecorder)
}
```

### 2. Configure the recorder

Add this in your **app module** `build.gradle.kts` file (the same file where you applied the plugin):

```kotlin
gifRecorder {
    applicationId.set("com.example.app")
    // Optional. Defaults to "artifacts/gifs" in the app module.
    outputDir.set(
        layout.projectDirectory.dir("artifacts/gifs"),
    )
    // Optional. Defaults to white. Used to fill unused areas when frames are
    // normalized to a common canvas.
    canvasBackgroundColor.set("0xFCFCFD")
    // Optional. Defaults to 1.0%, allowing 1 changed pixel in a 100-pixel frame.
    maxChangedPixelPercentage.set(1.0)
}
```

### 3. How to use

Each recording scenario must be a top-level, parameterless `@Composable` function.

#### 3.1 Simple

Pie chart with default recorder settings:

```kotlin
@RecordGif
@Composable
fun PieChartDemo() {
    // UI content
}
```

<p align="center">
  <img src="gif-baselines/PieChartDemo.gif" alt="Pie chart defaults" width="260" />
</p>

#### 3.2 Advanced

Multi line chart with swipe interactions:

```kotlin
@RecordGif(
    name = "multi_line_custom_gesture",
    durationMs = 3200,
    interactionNodeTag = "LineChartPlot",
    interactions = [
        GifInteraction(
            type = GifInteractionType.SWIPE,
            target = GifInteractionTarget.CENTER,
            direction = GifSwipeDirection.LEFT_TO_RIGHT,
            distance = GifSwipeDistance.LONG,
            speed = GifSwipeSpeed.NORMAL,
            framesAfter = 12,
        ),
    ],
)
@Composable
fun MultiLineChartDemo() {
    // UI content
}
```

<p align="center">
  <img src="gif-baselines/multi_line_custom_gesture.gif" alt="Multi line swipe interactions" width="260" />
</p>

### 4. Run tasks

List available scenarios:

```bash
./gradlew :sample:listGifScenarios
```

Record one scenario:

```bash
./gradlew :sample:recordGifDebug -PgifScenario=PieChartDemo
```

Record all scenarios:

```bash
./gradlew :sample:recordGifsDebug
```

Generated GIFs are written to `sample/artifacts/gifs` (or your configured `outputDir`).

Validate generated GIFs against `gif-baselines` locally when debugging:

```bash
./gradlew :sample:validateGifBaselines
```

The `gif-baselines` directory is the source-controlled reference set. Local
recording and validation are diagnostic tools only; do not use GIFs generated
on a local device or emulator to update the committed baselines.

## Common Configuration

You can override binaries/device selection when needed:

```kotlin
gifRecorder {
    adbSerial.set("emulator-5554") // default: auto
    adbBin.set("adb")
    ffmpegBin.set("ffmpeg")
    ffprobeBin.set("ffprobe")
    gifsicleBin.set("gifsicle")
}
```
