package com.jetbrains.plugin.fingerprint.infrastructure.storage

import com.jetbrains.plugin.fingerprint.domain.exception.PluginFingerprintException
import com.jetbrains.plugin.fingerprint.domain.model.ComparisonResult
import com.jetbrains.plugin.fingerprint.domain.model.Fingerprint
import com.jetbrains.plugin.fingerprint.domain.model.PluginStructure
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException

class OutputStorage {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun writeStructure(structure: PluginStructure, path: String) {
        try {
            File(path).parentFile?.mkdirs()
            File(path).writeText(json.encodeToString(structure))
        } catch (e: IOException) {
            throw PluginFingerprintException.StorageException("write", path, e)
        } catch (e: SerializationException) {
            throw PluginFingerprintException.StorageException("serialize", path, e)
        }
    }

    fun writeFingerprint(fingerprint: Fingerprint, path: String) {
        try {
            File(path).parentFile?.mkdirs()
            File(path).writeText(json.encodeToString(fingerprint))
        } catch (e: IOException) {
            throw PluginFingerprintException.StorageException("write", path, e)
        } catch (e: SerializationException) {
            throw PluginFingerprintException.StorageException("serialize", path, e)
        }
    }

    fun writeComparison(result: ComparisonResult, path: String) {
        try {
            File(path).parentFile?.mkdirs()
            File(path).writeText(json.encodeToString(result))
        } catch (e: IOException) {
            throw PluginFingerprintException.StorageException("write", path, e)
        } catch (e: SerializationException) {
            throw PluginFingerprintException.StorageException("serialize", path, e)
        }
    }

    fun readStructure(path: String): PluginStructure {
        try {
            if (!File(path).exists()) {
                throw PluginFingerprintException.FileNotFoundException(path)
            }
            return json.decodeFromString(File(path).readText())
        } catch (e: IOException) {
            throw PluginFingerprintException.StorageException("read", path, e)
        } catch (e: SerializationException) {
            throw PluginFingerprintException.StorageException("deserialize", path, e)
        }
    }

    fun readFingerprint(path: String): Fingerprint {
        try {
            if (!File(path).exists()) {
                throw PluginFingerprintException.FileNotFoundException(path)
            }
            return json.decodeFromString(File(path).readText())
        } catch (e: IOException) {
            throw PluginFingerprintException.StorageException("read", path, e)
        } catch (e: SerializationException) {
            throw PluginFingerprintException.StorageException("deserialize", path, e)
        }
    }

    fun writeSummaryReport(structure: PluginStructure, fingerprint: Fingerprint, path: String) {
        try {
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

                fingerprint.minHashSignature?.let { minHash ->
                    appendLine()
                    appendLine("MinHash Signatures: ${minHash.numHashes} hashes")
                    appendLine("First 10 signatures: ${minHash.signatures.take(10).joinToString(", ")}")
                }

                appendLine()
                appendLine("=" .repeat(80))
            }

            File(path).writeText(report)
        } catch (e: IOException) {
            throw PluginFingerprintException.StorageException("write", path, e)
        }
    }

    fun writeComparisonReport(result: ComparisonResult, path: String) {
        try {
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
                if (result.addedClasses.size > 10) {
                    appendLine("  ... and ${result.addedClasses.size - 10} more")
                }
                appendLine()
                appendLine("Removed Classes: ${result.removedClasses.size}")
                result.removedClasses.take(10).forEach { appendLine("  - $it") }
                if (result.removedClasses.size > 10) {
                    appendLine("  ... and ${result.removedClasses.size - 10} more")
                }
                appendLine()
                appendLine("=" .repeat(80))
            }

            File(path).writeText(report)
        } catch (e: IOException) {
            throw PluginFingerprintException.StorageException("write", path, e)
        }
    }

    private fun Double.format(decimals: Int) = "%.${decimals}f".format(this)
}