package com.jyotirmoyb.loan.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardrail unit test that scans all Kotlin source files under src/main/java for
 * hardcoded Compose Text("...") or Text(text = "...") string literals, and verifies
 * full EN/HI key parity and format-specifier consistency in strings.xml.
 */
class HardcodedStringsGuardTest {

    private val hardcodedTextRegex = Regex("""\bText\s*\(\s*(?:text\s*=\s*)?"([^"\\]*(?:\\.[^"\\]*)*)"""")
    private val formatSpecRegex = Regex("""%\d+\$[sdfoxeg]|%[sdfoxeg]""")

    private fun resolveMainRoot(): File {
        val direct = File("src/main")
        if (direct.exists()) return direct
        val nested = File("app/src/main")
        if (nested.exists()) return nested
        error("Could not locate app/src/main from ${File(".").absolutePath}")
    }

    @Test
    fun noHardcodedTextLiteralsInMainKotlinSources() {
        val javaRoot = File(resolveMainRoot(), "java")
        assertTrue("Expected $javaRoot to exist", javaRoot.exists())

        val violations = mutableListOf<String>()
        javaRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .forEach { file ->
                val relPath = file.relativeTo(javaRoot).path
                file.readLines().forEachIndexed { idx, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
                    val match = hardcodedTextRegex.find(line) ?: return@forEachIndexed
                    val literal = match.groupValues[1]
                    // Ignore pure punctuation/symbol separators without letters (e.g., "•", "—")
                    if (literal.any { it.isLetter() }) {
                        violations.add("$relPath:${idx + 1} -> Text(\"$literal\")")
                    }
                }
            }

        assertTrue(
            "Found ${violations.size} hardcoded Text(\"...\") literals in src/main/java. " +
                "Move them to strings.xml and values-hi/strings.xml:\n" +
                violations.take(25).joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun englishAndHindiStringsHaveCompleteKeyAndFormatParity() {
        val resRoot = File(resolveMainRoot(), "res")
        val enMap = parseStringsXml(File(resRoot, "values/strings.xml"))
        val hiMap = parseStringsXml(File(resRoot, "values-hi/strings.xml"))

        assertTrue("values/strings.xml must not be empty", enMap.isNotEmpty())

        val missingInHindi = enMap.keys - hiMap.keys
        val extraInHindi = hiMap.keys - enMap.keys
        assertTrue(
            "Missing Hindi translations for keys: ${missingInHindi.take(20)}",
            missingInHindi.isEmpty()
        )
        assertTrue(
            "Extra orphan keys in values-hi/strings.xml: ${extraInHindi.take(20)}",
            extraInHindi.isEmpty()
        )

        val formatMismatches = mutableListOf<String>()
        for ((key, enVal) in enMap) {
            val hiVal = hiMap[key] ?: continue
            val enSpecs = formatSpecRegex.findAll(enVal).map { it.value }.sorted().toList()
            val hiSpecs = formatSpecRegex.findAll(hiVal).map { it.value }.sorted().toList()
            if (enSpecs != hiSpecs) {
                formatMismatches.add("$key: EN=$enSpecs vs HI=$hiSpecs")
            }
        }
        assertTrue(
            "Format specifier mismatch between EN and HI strings:\n${formatMismatches.take(20).joinToString("\n")}",
            formatMismatches.isEmpty()
        )
    }

    private fun parseStringsXml(file: File): Map<String, String> {
        assertTrue("Expected ${file.path} to exist", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val map = LinkedHashMap<String, String>(nodes.length)
        for (i in 0 until nodes.length) {
            val el = nodes.item(i)
            val attrs = el.attributes ?: continue
            val translatable = attrs.getNamedItem("translatable")?.nodeValue
            if (translatable == "false") continue
            val name = attrs.getNamedItem("name")?.nodeValue ?: continue
            map[name] = el.textContent ?: ""
        }
        return map
    }
}
