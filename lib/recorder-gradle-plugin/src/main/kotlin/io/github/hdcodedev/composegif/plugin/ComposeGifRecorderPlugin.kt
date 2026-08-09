package io.github.hdcodedev.composegif.plugin

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.artifacts.VersionCatalogsExtension

/** Gradle plugin that wires recorder dependencies and capture tasks into Android app projects. */
class ComposeGifRecorderPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("gifRecorder", GifRecorderExtension::class.java)
        configureDefaults(project, extension)

        val listTask =
            project.tasks.register("listGifScenarios", ListGifScenariosTask::class.java) { task ->
                task.scenarioMetadataFile.convention(
                    project.layout.buildDirectory.file(GENERATED_SCENARIO_METADATA_FILE),
                )
            }
        val singleTask =
            project.tasks.register("recordGifDebug", RecordGifTask::class.java) { task ->
                task.configureFromExtension(project, extension)
                task.allScenarios.convention(false)
                task.dependsOn(listTask)
            }
        val allTask =
            project.tasks.register("recordGifsDebug", RecordGifTask::class.java) { task ->
                task.configureFromExtension(project, extension)
                task.allScenarios.convention(true)
                task.dependsOn(listTask)
            }

        project.tasks.register("validateGifBaselines", ValidateGifBaselinesTask::class.java) { task ->
            task.actualGifDir.set(allTask.flatMap { it.outputDir })
            task.baselineDir.convention(extension.baselineDir)
            task.reportDir.convention(extension.validationReportDir)
            task.ffmpegBin.convention(extension.ffmpegBin)
            task.ffprobeBin.convention(extension.ffprobeBin)
            task.maxChangedPixelPercentage.convention(extension.maxChangedPixelPercentage)
            task.dependsOn(allTask)
        }

        project.plugins.withId("com.android.application") {
            configureDependencies(project, extension)
            listTask.configure { it.dependsOn("kspDebugKotlin") }
            singleTask.configure { it.dependsOn("kspDebugKotlin", "installDebug", "installDebugAndroidTest") }
            allTask.configure { it.dependsOn("kspDebugKotlin", "installDebug", "installDebugAndroidTest") }
        }
    }

    private fun configureDefaults(
        project: Project,
        extension: GifRecorderExtension,
    ) {
        extension.outputDir.convention(project.layout.projectDirectory.dir("artifacts/gifs"))
        extension.adbSerial.convention("auto")
        extension.adbBin.convention("adb")
        extension.ffmpegBin.convention("ffmpeg")
        extension.ffprobeBin.convention("ffprobe")
        extension.gifsicleBin.convention("gifsicle")
        extension.scenario.convention("all")
        extension.registryClass.convention(GENERATED_REGISTRY_CLASS)
        extension.testClass.convention(DEFAULT_TEST_CLASS)
        extension.libraryVersion.convention(DEFAULT_LIBRARY_VERSION)
        extension.gifWidth.convention(540)
        extension.gifHeight.convention(0)
        extension.canvasBackgroundColor.convention("white")
        extension.maxChangedPixelPercentage.convention(DEFAULT_MAX_CHANGED_PIXEL_PERCENTAGE)
        extension.baselineDir.convention(
            project.rootProject.layout.projectDirectory
                .dir("gif-baselines"),
        )
        extension.validationReportDir.convention(project.layout.buildDirectory.dir("reports/gif-validation"))
    }

    private fun configureDependencies(
        project: Project,
        extension: GifRecorderExtension,
    ) {
        val version = extension.libraryVersion.getOrElse(DEFAULT_LIBRARY_VERSION)
        val composeUiVersion = project.composeUiVersion()
        project.dependencies.add("implementation", "io.github.hdcodedev:compose-gif-recorder-annotations:$version")
        project.dependencies.add("implementation", "io.github.hdcodedev:compose-gif-recorder-core:$version")
        project.dependencies.add("ksp", "io.github.hdcodedev:compose-gif-recorder-ksp:$version")
        project.dependencies.add(
            "androidTestImplementation",
            "io.github.hdcodedev:compose-gif-recorder-android-test:$version",
        )
        project.dependencies.add(
            "debugImplementation",
            "androidx.compose.ui:ui-test-manifest:$composeUiVersion",
        )
    }

    private fun Project.composeUiVersion(): String {
        val catalogs =
            extensions.findByType(VersionCatalogsExtension::class.java)
                ?: throw GradleException(
                    "The Compose GIF recorder requires a libs.versions.toml version catalog.",
                )
        val composeUiVersion =
            try {
                catalogs.named("libs").findVersion("composeUi").orElse(null)
            } catch (error: UnknownDomainObjectException) {
                throw GradleException(
                    "The Compose GIF recorder requires the libs version catalog.",
                    error,
                )
            } ?: throw GradleException(
                "The Compose GIF recorder requires libs.versions.composeUi to be defined.",
            )
        return composeUiVersion.requiredVersion
    }
}
