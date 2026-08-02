pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.github.hdcodedev.compose-gif-recorder") {
                useModule("io.github.hdcodedev:compose-gif-recorder-gradle-plugin:${requested.version}")
            }
        }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

includeBuild("lib") {
    dependencySubstitution {
        substitute(module("io.github.hdcodedev:compose-gif-recorder-gradle-plugin"))
            .using(project(":recorder-gradle-plugin"))
        substitute(module("io.github.hdcodedev:compose-gif-recorder-annotations"))
            .using(project(":recorder-annotations"))
        substitute(module("io.github.hdcodedev:compose-gif-recorder-core"))
            .using(project(":recorder-core"))
        substitute(module("io.github.hdcodedev:compose-gif-recorder-ksp"))
            .using(project(":recorder-ksp"))
        substitute(module("io.github.hdcodedev:compose-gif-recorder-android"))
            .using(project(":recorder-android"))
    }
}

rootProject.name = "demo"
include(":sample")
