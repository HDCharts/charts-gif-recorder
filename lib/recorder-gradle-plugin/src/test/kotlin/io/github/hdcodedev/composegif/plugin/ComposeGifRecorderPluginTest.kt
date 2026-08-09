package io.github.hdcodedev.composegif.plugin

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ComposeGifRecorderPluginTest {
    @Test
    fun registersExpectedTasks() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.hdcodedev.compose-gif-recorder")

        assertNotNull(project.tasks.findByName("listGifScenarios"))
        assertNotNull(project.tasks.findByName("recordGifDebug"))
        assertNotNull(project.tasks.findByName("recordGifsDebug"))
        assertNotNull(project.tasks.findByName("validateGifBaselines"))

        val extension = project.extensions.getByType(GifRecorderExtension::class.java)
        assertEquals(DEFAULT_MAX_CHANGED_PIXEL_PERCENTAGE, extension.maxChangedPixelPercentage.get())
        assertEquals("black", extension.canvasBackgroundColor.get())

        extension.canvasBackgroundColor.set("0xFCFCFD")
        val recordTask = project.tasks.getByName("recordGifsDebug") as RecordGifTask
        assertEquals("0xFCFCFD", recordTask.canvasBackgroundColor.get())
    }
}
