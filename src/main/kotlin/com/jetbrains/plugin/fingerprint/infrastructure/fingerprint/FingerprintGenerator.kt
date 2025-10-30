package com.jetbrains.plugin.fingerprint.infrastructure.fingerprint

import com.jetbrains.plugin.fingerprint.domain.model.Fingerprint
import com.jetbrains.plugin.fingerprint.domain.model.PluginStructure
import java.security.MessageDigest

class FingerprintGenerator(
    private val minHashGenerator: MinHashGenerator = MinHashGenerator()
) {

    fun generate(structure: PluginStructure): Fingerprint {
        val features = extractFeatures(structure)
        val hash = calculateStructuralHash(features)
        val minHash = minHashGenerator.generate(features, structure.pluginId)

        return Fingerprint(
            pluginId = structure.pluginId,
            structuralHash = hash,
            features = features,
            minHashSignature = minHash
        )
    }

    private fun extractFeatures(structure: PluginStructure): Set<String> {
        val features = mutableSetOf<String>()

        structure.classInfos.forEach { classInfo ->
            features.add("class:${classInfo.className}")

            classInfo.methods.forEach { method ->
                features.add("method:${classInfo.className}::${method.name}${method.descriptor}")
            }

            classInfo.fields.forEach { field ->
                features.add("field:${classInfo.className}::${field.name}")
            }

            classInfo.superClass?.let {
                features.add("extends:${classInfo.className}:$it")
            }

            classInfo.interfaces.forEach { iface ->
                features.add("implements:${classInfo.className}:$iface")
            }
        }

        structure.fileEntries.forEach { entry ->
            if (entry.fileType == com.jetbrains.plugin.fingerprint.domain.model.FileEntry.FileType.CLASS) {
                features.add("file:${entry.path}")
            }
        }

        return features
    }

    private fun calculateStructuralHash(features: Set<String>): String {
        val sorted = features.sorted().joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(sorted.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}