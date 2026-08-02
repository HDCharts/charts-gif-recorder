package io.github.hdcodedev.composegif.plugin

import java.io.File

internal fun parseScenarioNames(generatedRegistry: File): List<String> {
    if (!generatedRegistry.exists()) return emptyList()
    val text = generatedRegistry.readText()
    val regex = Regex("name\\s*=\\s*\"([^\"]+)\"")
    return regex
        .findAll(text)
        .map { it.groupValues[1] }
        .distinct()
        .toList()
}

internal fun parseScenarioFps(
    generatedRegistry: File,
    scenarioName: String,
): Int? {
    if (!generatedRegistry.exists()) return null
    val text = generatedRegistry.readText()
    val captureBlock = findScenarioCaptureBlock(text, scenarioName) ?: return null
    return Regex("""\bfps\s*=\s*(\d+)""")
        .find(captureBlock)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()
}

private fun findScenarioCaptureBlock(
    registryText: String,
    scenarioName: String,
): String? {
    var searchFrom = 0
    val namePattern = Regex("""\bname\s*=\s*"([^"]+)"""")
    val capturePattern = Regex("""\bcapture\s*=\s*GifCaptureConfig\s*\(""")

    while (true) {
        val scenarioSpecStart = registryText.indexOf("GifScenarioSpec(", startIndex = searchFrom)
        if (scenarioSpecStart == -1) return null

        val specOpenParen = registryText.indexOf('(', startIndex = scenarioSpecStart)
        if (specOpenParen == -1) return null

        val specCloseParen = findMatchingClosingParen(registryText, specOpenParen) ?: return null
        val specBody = registryText.substring(specOpenParen + 1, specCloseParen)

        val parsedName = namePattern.find(specBody)?.groupValues?.get(1)
        if (parsedName == scenarioName) {
            val captureMatch = capturePattern.find(specBody) ?: return null
            val captureOpenParen = captureMatch.range.last
            val captureCloseParen = findMatchingClosingParen(specBody, captureOpenParen) ?: return null
            return specBody.substring(captureOpenParen + 1, captureCloseParen)
        }

        searchFrom = specCloseParen + 1
    }
}

private fun findMatchingClosingParen(
    text: String,
    openingParenIndex: Int,
): Int? {
    if (openingParenIndex !in text.indices || text[openingParenIndex] != '(') return null

    var depth = 0
    var inString = false
    var escaped = false

    for (index in openingParenIndex until text.length) {
        val char = text[index]

        if (inString) {
            if (escaped) {
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else if (char == '"') {
                inString = false
            }
            continue
        }

        when (char) {
            '"' -> inString = true
            '(' -> depth += 1
            ')' -> {
                depth -= 1
                if (depth == 0) return index
            }
        }
    }

    return null
}
