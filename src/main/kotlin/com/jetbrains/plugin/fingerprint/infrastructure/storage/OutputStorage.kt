package com.jetbrains.plugin.fingerprint.infrastructure.storage

import com.jetbrains.plugin.fingerprint.domain.model.ComparisonResult
import com.jetbrains.plugin.fingerprint.domain.model.Fingerprint
import com.jetbrains.plugin.fingerprint.domain.model.PluginStructure
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class OutputStorage {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun writeStructure(structure: PluginStructure, path: String) {
        File(path).parentFile?.mkdirs()
        File(path).writeText(json.encodeToString(structure))
    }

    fun writeFingerprint(fingerprint: Fingerprint, path: String) {
        File(path).parentFile?.mkdirs()
        File(path).writeText(json.encodeToString(fingerprint))
    }

    fun writeComparison(result: ComparisonResult, path: String) {
        File(path).parentFile?.mkdirs()
        File(path).writeText(json.encodeToString(result))
    }

    fun readStructure(path: String): PluginStructure {
        return json.decodeFromString(File(path).readText())
    }

    fun readFingerprint(path: String): Fingerprint {
        return json.decodeFromString(File(path).readText())
    }

    fun writeSummaryReport(structure: PluginStructure, fingerprint: Fingerprint, path: String) {
        File(path).parentFile?.mkdirs()

        val report = buildString {
            appendLine("=" .repeat(80))
            appendLine("PLUGIN ANALYSIS REPORT")
            appendLine("=" .repeat(80))
            appendLine()
            appendLine("Plugin: ${structure.pluginId}")
            appendLine("Total Files: ${structure.totalFiles}")
            appendLine("Total Classes: ${structure.totalClasses}")
            appendLine("Total Size: ${structure.totalSize / 1024} KB")
            appendLine()
            appendLine("Structural Hash: ${fingerprint.structuralHash}")
            appendLine("Total Features: ${fingerprint.features.size}")
            appendLine()
            appendLine("=" .repeat(80))
        }

        File(path).writeText(report)
    }

    fun writeComparisonReport(result: ComparisonResult, path: String) {
        File(path).parentFile?.mkdirs()

        val report = buildString {
            appendLine("=" .repeat(80))
            appendLine("COMPARISON REPORT")
            appendLine("=" .repeat(80))
            appendLine()
            appendLine("Plugin 1: ${result.plugin1}")
            appendLine("Plugin 2: ${result.plugin2}")
            appendLine()
            appendLine("Similarity: ${(result.similarityScore * 100).format(2)}%")
            appendLine()
            appendLine("Added Classes: ${result.addedClasses.size}")
            result.addedClasses.take(10).forEach { appendLine("  + $it") }
            appendLine()
            appendLine("Removed Classes: ${result.removedClasses.size}")
            result.removedClasses.take(10).forEach { appendLine("  - $it") }
            appendLine()
            appendLine("=" .repeat(80))
        }

        File(path).writeText(report)
    }

    private fun Double.format(decimals: Int) = "%.${decimals}f".format(this)
}