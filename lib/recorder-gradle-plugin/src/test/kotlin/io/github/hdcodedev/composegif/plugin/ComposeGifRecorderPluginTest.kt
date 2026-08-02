package io.github.hdcodedev.composegif.plugin

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
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
    }
}
