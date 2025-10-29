package com.jetbrains.plugin.fingerprint.application

import com.jetbrains.plugin.fingerprint.domain.model.PluginStructure
import com.jetbrains.plugin.fingerprint.infrastructure.fingerprint.FingerprintGenerator
import com.jetbrains.plugin.fingerprint.infrastructure.parser.ArchiveParser
import com.jetbrains.plugin.fingerprint.infrastructure.parser.BytecodeParser
import com.jetbrains.plugin.fingerprint.infrastructure.storage.OutputStorage
import java.io.File

class AnalyzeUseCase(
    private val archiveParser: ArchiveParser = ArchiveParser(),
    private val bytecodeParser: BytecodeParser = BytecodeParser(),
    private val fingerprintGenerator: FingerprintGenerator = FingerprintGenerator(),
    private val storage: OutputStorage = OutputStorage()
) {

    fun execute(archivePath: String, outputPath: String) {

        require(archivePath.isNotBlank()) { "Archive path cannot be empty" }
        require(File(archivePath).exists()) { "File not found: $archivePath" }
        require(archivePath.endsWith(".jar") || archivePath.endsWith(".zip")) {
            "File must be .jar or .zip"
        }

        println("Analyzing plugin: $archivePath")

        val fileEntries = archiveParser.parse(archivePath)
        println("Found ${fileEntries.size} files")

        val classFiles = archiveParser.extractClassFiles(archivePath)
        println("Extracting ${classFiles.size} class files")

        val classInfos = bytecodeParser.parseClasses(classFiles)
        println("Parsed ${classInfos.size} classes")

        if (classInfos.isEmpty()) {
            println("Warning: No classes found in archive!")
        }

        val pluginId = File(archivePath).nameWithoutExtension

        val structure = PluginStructure(
            pluginId = pluginId,
            fileEntries = fileEntries,
            classInfos = classInfos,
            totalFiles = fileEntries.size,
            totalClasses = classInfos.size,
            totalSize = fileEntries.sumOf { it.size }
        )

        val fingerprint = fingerprintGenerator.generate(structure)
        println("Generated fingerprint with ${fingerprint.features.size} features")

        storage.writeStructure(structure, "$outputPath.structure.json")
        storage.writeFingerprint(fingerprint, "$outputPath.fingerprint.json")
        storage.writeSummaryReport(structure, fingerprint, "$outputPath.report.txt")

        println("✓ Analysis complete!")
        println("  - $outputPath.structure.json")
        println("  - $outputPath.fingerprint.json")
        println("  - $outputPath.report.txt")
    }
}