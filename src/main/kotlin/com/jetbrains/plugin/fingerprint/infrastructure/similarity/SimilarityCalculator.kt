package com.jetbrains.plugin.fingerprint.infrastructure.similarity

import com.jetbrains.plugin.fingerprint.domain.model.ComparisonResult
import com.jetbrains.plugin.fingerprint.domain.model.Fingerprint
import com.jetbrains.plugin.fingerprint.domain.model.PluginStructure

class SimilarityCalculator {

    fun compare(
        structure1: PluginStructure,
        structure2: PluginStructure,
        fingerprint1: Fingerprint,
        fingerprint2: Fingerprint
    ): ComparisonResult {

        val similarity = calculateJaccardSimilarity(
            fingerprint1.features,
            fingerprint2.features
        )

        val classes1 = structure1.classInfos.map { it.className }.toSet()
        val classes2 = structure2.classInfos.map { it.className }.toSet()

        return ComparisonResult(
            plugin1 = structure1.pluginId,
            plugin2 = structure2.pluginId,
            similarityScore = similarity,
            addedClasses = (classes2 - classes1).sorted(),
            removedClasses = (classes1 - classes2).sorted()
        )
    }

    private fun calculateJaccardSimilarity(set1: Set<String>, set2: Set<String>): Double {
        if (set1.isEmpty() && set2.isEmpty()) return 1.0
        if (set1.isEmpty() || set2.isEmpty()) return 0.0

        val intersection = (set1 intersect set2).size.toDouble()
        val union = (set1 union set2).size.toDouble()

        return intersection / union
    }
}