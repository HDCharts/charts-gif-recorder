import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.dokka)
    `maven-publish`
    signing
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(
        libs.versions.java
            .get()
            .toInt(),
    )
}

dokka {
    dokkaSourceSets.configureEach {
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

dependencies {
    implementation(project(":recorder-annotations"))
    implementation(project(":recorder-core"))
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
}

mavenPublishing {
    coordinates(
        groupId = ProjectConfig.group,
        artifactId = "compose-gif-recorder-ksp",
        version = ProjectConfig.version,
    )

    pom {
        ProjectPublishing.configurePom(
            pom = this,
            moduleName = "Compose GIF Recorder KSP",
            moduleDescription = "KSP processor for compose-gif-recorder",
        )
    }
}
