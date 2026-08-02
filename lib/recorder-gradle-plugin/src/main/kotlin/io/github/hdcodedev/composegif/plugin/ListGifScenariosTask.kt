package io.github.hdcodedev.composegif.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

internal abstract class ListGifScenariosTask : DefaultTask() {
    @get:Internal
    public abstract val generatedRegistryFile: RegularFileProperty

    @TaskAction
    public fun listScenarios() {
        val file = generatedRegistryFile.get().asFile
        if (!file.exists()) {
            throw IllegalStateException(
                "Generated registry not found at ${file.path}. Run kspDebugKotlin first.",
            )
        }
        val names = parseScenarioNames(file)
        if (names.isEmpty()) {
            throw IllegalStateException("No GIF scenarios found in generated registry.")
        }

        logger.lifecycle("Compose GIF scenarios:")
        names.forEach { logger.lifecycle(" - $it") }
    }
}

internal fun gifFiles(directory: File): List<File> =
    directory
        .listFiles { file -> file.isFile && file.extension == "gif" }
        ?.sortedBy { it.name }
        ?: emptyList()
