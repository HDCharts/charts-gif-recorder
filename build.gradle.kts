import org.jlleitschuh.gradle.ktlint.KtlintExtension

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.compose.gif.recorder) apply false
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    plugins.withId("org.jlleitschuh.gradle.ktlint") {
        extensions.configure<KtlintExtension>("ktlint") {
            android.set(true)
            ignoreFailures.set(false)
        }
    }
}

tasks.register("sampleTest") {
    group = "verification"
    description = "Runs sample unit tests for the demo project"
    dependsOn(":sample:testDebugUnitTest")
}

tasks.named("clean") {
    dependsOn(":sample:clean")
    dependsOn(gradle.includedBuild("lib").task(":clean"))
}
