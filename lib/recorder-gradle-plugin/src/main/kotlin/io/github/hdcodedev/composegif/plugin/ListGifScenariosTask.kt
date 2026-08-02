package io.github.hdcodedev.composegif.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

internal abstract class ListGifScenariosTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val scenarioMetadataFile: RegularFileProperty

    @TaskAction
    public fun listScenarios() {
        val file = scenarioMetadataFile.get().asFile
        if (!file.exists()) {
            throw IllegalStateException(
                "Generated scenario metadata not found at ${file.path}. Run kspDebugKotlin first.",
            )
        }
        val scenarios = parseScenarioMetadata(file)
        if (scenarios.isEmpty()) {
            throw IllegalStateException("No GIF scenarios found in generated metadata.")
        }

        logger.lifecycle("Compose GIF scenarios:")
        scenarios.forEach { logger.lifecycle(" - ${it.name}") }
    }
}

internal fun gifFiles(directory: File): List<File> =
    directory
        .listFiles { file -> file.isFile && file.extension == "gif" }
        ?.sortedBy { it.name }
        ?: emptyList()
