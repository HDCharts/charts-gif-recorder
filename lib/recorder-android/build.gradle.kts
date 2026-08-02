import org.jetbrains.dokka.gradle.engine.parameters.KotlinPlatform
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dokka)
    `maven-publish`
    signing
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "io.github.hdcodedev.composegif.android"
    compileSdk {
        version =
            release(
                libs.versions.androidCompileSdk
                    .get()
                    .toInt(),
            ) {
                minorApiLevel =
                    libs.versions.androidCompileSdkMinor
                        .get()
                        .toInt()
            }
    }

    defaultConfig {
        minSdk =
            libs.versions.androidMinSdk
                .get()
                .toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(
                JvmTarget
                    .fromTarget(libs.versions.java.get()),
            )
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":recorder-core"))

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)

    api(libs.compose.ui.test.junit4)
    api(libs.compose.ui.test.manifest)
    api(libs.androidx.test.ext.junit)
    api(libs.androidx.test.runner)
    api(libs.androidx.test.rules)
    api(libs.junit)

    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.compose.foundation)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}

dokka {
    dokkaSourceSets.maybeCreate("main").apply {
        sourceRoots.from(file("src/main/kotlin"))
        sourceRoots.from(file("src/main/java"))
        classpath.from(provider { configurations.getByName("releaseCompileClasspath") })
        analysisPlatform.set(KotlinPlatform.AndroidJVM)
        documentedVisibilities.set(
            setOf(VisibilityModifier.Public),
        )
        skipEmptyPackages.set(true)
        jdkVersion.set(
            libs.versions.java
                .get()
                .toInt(),
        )
    }
}

mavenPublishing {
    configure(
        com.vanniktech.maven.publish.AndroidSingleVariantLibrary(
            javadocJar =
                com.vanniktech.maven.publish.JavadocJar
                    .None(),
            sourcesJar =
                com.vanniktech.maven.publish.SourcesJar
                    .Sources(),
            variant = "release",
        ),
    )

    coordinates(
        groupId = ProjectConfig.group,
        artifactId = "compose-gif-recorder-android",
        version = ProjectConfig.version,
    )

    pom {
        ProjectPublishing.configurePom(
            pom = this,
            moduleName = "Compose GIF Recorder Android",
            moduleDescription = "Android deterministic frame capture runtime for compose-gif-recorder",
        )
    }
}
