package io.github.hdcodedev.composegif.plugin

import java.io.File
import java.util.Properties

internal data class GifScenarioMetadata(
    val name: String,
    val fps: Int,
)

private const val METADATA_SCHEMA_VERSION = 1

internal fun parseScenarioMetadata(metadataFile: File): List<GifScenarioMetadata> {
    if (!metadataFile.exists()) return emptyList()

    val properties = Properties()
    metadataFile.inputStream().use(properties::load)

    val schema = properties.requiredInt("schema")
    check(schema == METADATA_SCHEMA_VERSION) {
        "Unsupported GIF scenario metadata schema $schema in ${metadataFile.path}. " +
            "Expected $METADATA_SCHEMA_VERSION."
    }

    val count = properties.requiredInt("count")
    check(count >= 0) { "GIF scenario metadata count must not be negative." }

    return (0 until count).map { index ->
        GifScenarioMetadata(
            name = properties.requiredValue("scenario.$index.name"),
            fps = properties.requiredInt("scenario.$index.fps"),
        )
    }
}

private fun Properties.requiredValue(key: String): String =
    getProperty(key)?.takeIf { it.isNotBlank() }
        ?: error("Missing GIF scenario metadata property '$key'.")

private fun Properties.requiredInt(key: String): Int =
    requiredValue(key).toIntOrNull()
        ?: error("GIF scenario metadata property '$key' must be an integer.")
