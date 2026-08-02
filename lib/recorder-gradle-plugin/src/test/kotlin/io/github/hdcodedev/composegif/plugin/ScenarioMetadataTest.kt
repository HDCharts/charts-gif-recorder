package io.github.hdcodedev.composegif.plugin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScenarioMetadataTest {
    @Test
    fun parsesScenarioMetadata() {
        val file =
            metadataFile(
                """
                schema=1
                count=2
                scenario.0.name=line_chart_demo
                scenario.0.fps=50
                scenario.1.name=bar-chart-demo
                scenario.1.fps=24
                """.trimIndent(),
            )

        assertEquals(
            listOf(
                GifScenarioMetadata("line_chart_demo", 50),
                GifScenarioMetadata("bar-chart-demo", 24),
            ),
            parseScenarioMetadata(file),
        )

        file.delete()
    }

    @Test
    fun returnsEmptyMetadataWhenFileMissing() {
        val file = File("build/tmp/does-not-exist-generated-metadata.properties")

        assertEquals(emptyList(), parseScenarioMetadata(file))
    }

    @Test
    fun rejectsUnsupportedSchema() {
        val file =
            metadataFile(
                """
                schema=2
                count=0
                """.trimIndent(),
            )

        assertFailsWith<IllegalStateException> { parseScenarioMetadata(file) }

        file.delete()
    }

    @Test
    fun rejectsMissingScenarioProperties() {
        val file =
            metadataFile(
                """
                schema=1
                count=1
                scenario.0.name=line_chart_demo
                """.trimIndent(),
            )

        assertFailsWith<IllegalStateException> { parseScenarioMetadata(file) }

        file.delete()
    }

    private fun metadataFile(contents: String): File =
        File.createTempFile("generated-scenario-metadata", ".properties").apply {
            writeText(contents)
        }
}
