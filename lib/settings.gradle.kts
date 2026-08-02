rootProject.name = "compose-gif-recorder-lib"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

include(":recorder-annotations")
include(":recorder-core")
include(":recorder-ksp")
include(":recorder-android-test")
include(":recorder-gradle-plugin")
