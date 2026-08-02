package io.github.hdcodedev.composegif.core

import androidx.compose.runtime.Composable

/** Contract implemented by generated scenario registries. */
interface GifScenarioRegistry {
    /** Returns all registered capture scenarios. */
    fun scenarios(): List<GifScenarioSpec>

    /** Renders the scenario content for the provided [name]. */
    @Composable
    fun Render(name: String)
}
