package com.jetbrains.plugin.fingerprint.application

import com.jetbrains.plugin.fingerprint.infrastructure.similarity.SimilarityCalculator
import com.jetbrains.plugin.fingerprint.infrastructure.storage.OutputStorage

class CompareUseCase(
    private val calculator: SimilarityCalculator = SimilarityCalculator(),
    private val storage: OutputStorage = OutputStorage()
) {

    fun execute(
        structure1Path: String,
        structure2Path: String,
        fingerprint1Path: String,
        fingerprint2Path: String,
        outputPath: String
    ) {
        println("Loading plugins...")

        val structure1 = storage.readStructure(structure1Path)
        val structure2 = storage.readStructure(structure2Path)
        val fingerprint1 = storage.readFingerprint(fingerprint1Path)
        val fingerprint2 = storage.readFingerprint(fingerprint2Path)

        println("Comparing ${structure1.pluginId} vs ${structure2.pluginId}")

        val result = calculator.compare(structure1, structure2, fingerprint1, fingerprint2)

        storage.writeComparison(result, "$outputPath.comparison.json")
        storage.writeComparisonReport(result, "$outputPath.comparison.txt")

        println()
        println("✓ Comparison complete!")
        println("  Similarity: ${(result.similarityScore * 100).format(2)}%")
        println("  Added classes: ${result.addedClasses.size}")
        println("  Removed classes: ${result.removedClasses.size}")
        println()
        println("  - $outputPath.comparison.json")
        println("  - $outputPath.comparison.txt")
    }

    private fun Double.format(decimals: Int) = "%.${decimals}f".format(this)
}