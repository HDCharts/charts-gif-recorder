package io.github.hdcodedev.composegif.plugin

internal const val GENERATED_REGISTRY_FILE =
    "generated/ksp/debug/kotlin/" +
        "io/github/hdcodedev/composegif/generated/GeneratedGifScenarioRegistry.kt"
internal const val GENERATED_REGISTRY_CLASS = "io.github.hdcodedev.composegif.generated.GeneratedGifScenarioRegistry"
internal const val DEFAULT_TEST_CLASS = "io.github.hdcodedev.composegif.android.GifFrameCaptureTest"

internal val DEFAULT_LIBRARY_VERSION: String by lazy {
    ComposeGifRecorderPlugin::class.java
        .classLoader
        .getResourceAsStream("compose-gif-recorder.version")
        ?.use { it.bufferedReader().readText() }
        ?.trim()
        ?: error("compose-gif-recorder.version resource not found")
}

internal const val DEFAULT_REMOTE_SUBDIR = "gif-recorder"
