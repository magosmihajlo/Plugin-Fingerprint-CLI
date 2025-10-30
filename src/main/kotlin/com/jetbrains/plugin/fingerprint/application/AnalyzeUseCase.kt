package com.jetbrains.plugin.fingerprint.application

import com.jetbrains.plugin.fingerprint.domain.model.PluginStructure
import com.jetbrains.plugin.fingerprint.infrastructure.fingerprint.FingerprintGenerator
import com.jetbrains.plugin.fingerprint.infrastructure.parser.ArchiveParser
import com.jetbrains.plugin.fingerprint.infrastructure.parser.BytecodeParser
import com.jetbrains.plugin.fingerprint.infrastructure.storage.OutputStorage
import java.io.File
import org.slf4j.LoggerFactory


class AnalyzeUseCase(
    private val archiveParser: ArchiveParser = ArchiveParser(),
    private val bytecodeParser: BytecodeParser = BytecodeParser(),
    private val fingerprintGenerator: FingerprintGenerator = FingerprintGenerator(),
    private val storage: OutputStorage = OutputStorage()
) {

    private val logger = LoggerFactory.getLogger(AnalyzeUseCase::class.java)

    fun execute(archivePath: String, outputPath: String) {

        require(archivePath.isNotBlank()) { "Archive path cannot be empty" }
        require(File(archivePath).exists()) { "File not found: $archivePath" }
        require(archivePath.endsWith(".jar") || archivePath.endsWith(".zip")) {
            "File must be .jar or .zip"
        }

        logger.info("Starting analysis of plugin: {}", archivePath)

        val fileEntries = archiveParser.parse(archivePath)
        logger.info("Found {} files in archive", fileEntries.size)

        logger.debug("File types breakdown: {} classes, {} XML files, {} resources",
            fileEntries.count { it.fileType == com.jetbrains.plugin.fingerprint.domain.model.FileEntry.FileType.CLASS },
            fileEntries.count { it.fileType == com.jetbrains.plugin.fingerprint.domain.model.FileEntry.FileType.XML },
            fileEntries.count { it.fileType == com.jetbrains.plugin.fingerprint.domain.model.FileEntry.FileType.RESOURCE }
        )

        val classFiles = archiveParser.extractClassFiles(archivePath)
        logger.info("Extracting {} class files for bytecode analysis", classFiles.size)

        val classInfos = bytecodeParser.parseClasses(classFiles)
        logger.info("Successfully parsed {} classes", classInfos.size)

        if (classInfos.isEmpty()) {
            logger.warn("No classes found in archive! This may not be a valid plugin.")
        }

        val pluginId = File(archivePath).nameWithoutExtension
        logger.debug("Using plugin ID: {}", pluginId)

        val structure = PluginStructure(
            pluginId = pluginId,
            fileEntries = fileEntries,
            classInfos = classInfos,
            totalFiles = fileEntries.size,
            totalClasses = classInfos.size,
            totalSize = fileEntries.sumOf { it.size }
        )

        logger.debug("Plugin structure created - {} bytes total", structure.totalSize)


        val fingerprint = fingerprintGenerator.generate(structure)
        logger.info("Generated fingerprint with {} features", fingerprint.features.size)
        logger.debug("Structural hash: {}", fingerprint.structuralHash)

        fingerprint.minHashSignature?.let { minHash ->
            logger.debug("MinHash signature generated with {} hash functions", minHash.numHashes)
        }

        storage.writeStructure(structure, "$outputPath.structure.json")
        storage.writeFingerprint(fingerprint, "$outputPath.fingerprint.json")
        storage.writeSummaryReport(structure, fingerprint, "$outputPath.report.txt")

        logger.info("Analysis complete! Output files:")
        logger.info("  - {}.structure.json", outputPath)
        logger.info("  - {}.fingerprint.json", outputPath)
        logger.info("  - {}.report.txt", outputPath)

        // Still print success to the console for user feedback
        println("✓ Analysis complete!")
        println("  - $outputPath.structure.json")
        println("  - $outputPath.fingerprint.json")
        println("  - $outputPath.report.txt")
    }
}