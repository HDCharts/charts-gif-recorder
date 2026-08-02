import org.gradle.api.tasks.Copy
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    base
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.vanniktech.publish) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.axion.release)
}

scmVersion {
    tag {
        prefix.set("")
    }
    repository {
        directory.set(project.rootProject.file("../").absolutePath)
    }
    versionIncrementer("incrementPatch")
}

val recorderReleaseVersion =
    providers.gradleProperty("recorderReleaseVersion")
        .orNull
        ?.takeIf { it.isNotBlank() }
val resolvedVersion = recorderReleaseVersion ?: scmVersion.version

tasks.register("axionPreviousVersion") {
    group = "versioning"
    description = "Prints the previous stable version resolved by Axion"
    doLast {
        println(scmVersion.previousVersion)
    }
}

val publishableModules = listOf(
    ":recorder-annotations",
    ":recorder-core",
    ":recorder-ksp",
    ":recorder-android",
    ":recorder-gradle-plugin"
)

dependencies {
    publishableModules.forEach { modulePath ->
        add("dokka", project(modulePath))
    }
}

subprojects {
    plugins.withId("org.jlleitschuh.gradle.ktlint") {
        extensions.configure<KtlintExtension>("ktlint") {
            android.set(true)
            ignoreFailures.set(false)
        }
    }
}

allprojects {
    group = ProjectConfig.group
    version = resolvedVersion

    repositories {
        google()
        mavenCentral()
    }
}

val testableJvmModules = listOf(
    ":recorder-annotations",
    ":recorder-core",
    ":recorder-ksp",
    ":recorder-gradle-plugin",
)

tasks.register("recorderTest") {
    group = "verification"
    description = "Runs JVM/unit tests for all recorder modules"
    dependsOn(testableJvmModules.map { "$it:test" })
    dependsOn(":recorder-android:testDebugUnitTest")
}

tasks.register("publishRecorderModules") {
    group = "publishing"
    description = "Publishes all recorder modules to the configured Maven repository"
    dependsOn(publishableModules.map { "$it:publish" })
}

tasks.register("publishRecorderModulesToMavenLocal") {
    group = "publishing"
    description = "Publishes all recorder modules to Maven Local"
    dependsOn(publishableModules.map { "$it:publishToMavenLocal" })
}

tasks.register<Copy>("dokkaPublicApi") {
    group = "documentation"
    description = "Generates aggregated Dokka HTML docs for published recorder modules"
    dependsOn("dokkaGeneratePublicationHtml")
    into(layout.buildDirectory.dir("dokka/public-api"))
    from(layout.buildDirectory.dir("dokka/html"))
}
