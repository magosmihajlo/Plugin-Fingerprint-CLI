package com.jetbrains.plugin.fingerprint.application

import com.jetbrains.plugin.fingerprint.infrastructure.similarity.SimilarityCalculator
import com.jetbrains.plugin.fingerprint.infrastructure.storage.OutputStorage
import org.slf4j.LoggerFactory

class CompareUseCase(
    private val calculator: SimilarityCalculator = SimilarityCalculator(),
    private val storage: OutputStorage = OutputStorage()
) {
    private val logger = LoggerFactory.getLogger(CompareUseCase::class.java)

    fun execute(
        structure1Path: String,
        structure2Path: String,
        fingerprint1Path: String,
        fingerprint2Path: String,
        outputPath: String
    ) {
        logger.info("Starting comparison of two plugins")
        logger.debug("Loading structure from: {}", structure1Path)
        logger.debug("Loading structure from: {}", structure2Path)

        val structure1 = storage.readStructure(structure1Path)
        val structure2 = storage.readStructure(structure2Path)
        val fingerprint1 = storage.readFingerprint(fingerprint1Path)
        val fingerprint2 = storage.readFingerprint(fingerprint2Path)

        logger.info("Comparing {} vs {}", structure1.pluginId, structure2.pluginId)
        logger.debug("Plugin 1: {} classes, {} features",
            structure1.totalClasses, fingerprint1.features.size)
        logger.debug("Plugin 2: {} classes, {} features",
            structure2.totalClasses, fingerprint2.features.size)

        val useMinHash = fingerprint1.minHashSignature != null &&
                fingerprint2.minHashSignature != null
        logger.debug("Using {} for similarity calculation",
            if (useMinHash) "MinHash (fast)" else "Jaccard (exact)")

        val result = calculator.compare(structure1, structure2, fingerprint1, fingerprint2)

        logger.info("Similarity score: {}", result.similarityScore)
        logger.info("Added classes: {}, Removed classes: {}",
            result.addedClasses.size, result.removedClasses.size)

        storage.writeComparison(result, "$outputPath.comparison.json")
        storage.writeComparisonReport(result, "$outputPath.comparison.txt")

        logger.info("Comparison complete! Output files:")
        logger.info("  - {}.comparison.json", outputPath)
        logger.info("  - {}.comparison.txt", outputPath)

        // User-facing output
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