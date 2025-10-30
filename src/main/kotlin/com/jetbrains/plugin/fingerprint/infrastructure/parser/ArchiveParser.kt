package com.jetbrains.plugin.fingerprint.infrastructure.parser

import com.jetbrains.plugin.fingerprint.domain.exception.PluginFingerprintException
import com.jetbrains.plugin.fingerprint.domain.model.FileEntry
import org.slf4j.LoggerFactory
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipException
import java.util.zip.ZipFile

class ArchiveParser {
    private val logger = LoggerFactory.getLogger(ArchiveParser::class.java)

    fun parse(archivePath: String): List<FileEntry> {
        logger.debug("Starting archive parsing: {}", archivePath)

        val file = File(archivePath)

        if (!file.exists()) {
            logger.error("Archive file not found: {}", archivePath)
            throw PluginFingerprintException.FileNotFoundException(archivePath)
        }

        if (!file.canRead()) {
            logger.error("Archive file is not readable: {}", archivePath)
            throw PluginFingerprintException.InvalidArchiveException(
                archivePath,
                IllegalAccessException("File is not readable")
            )
        }

        val entries = mutableListOf<FileEntry>()
        var skippedEntries = 0

        try {
            ZipFile(file).use { zipFile ->
                val totalEntries = zipFile.entries().toList().size
                logger.debug("Archive contains {} entries", totalEntries)

                zipFile.entries().toList().forEach { entry ->
                    if (!entry.isDirectory) {
                        try {
                            val fileType = determineFileType(entry.name)
                            val hash = if (shouldHash(fileType)) {
                                calculateHash(zipFile.getInputStream(entry).readBytes())
                            } else null

                            entries.add(FileEntry(
                                path = entry.name,
                                size = entry.size,
                                isDirectory = false,
                                sha256Hash = hash,
                                fileType = fileType
                            ))

                            logger.trace("Processed entry: {} (type: {}, size: {} bytes)",
                                entry.name, fileType, entry.size)
                        } catch (e: Exception) {
                            skippedEntries++
                            logger.warn("Failed to process archive entry: {} - {}",
                                entry.name, e.message)
                            logger.debug("Entry processing error details", e)
                        }
                    }
                }
            }
        } catch (e: ZipException) {
            logger.error("Invalid or corrupted ZIP archive: {}", archivePath, e)
            throw PluginFingerprintException.InvalidArchiveException(archivePath, e)
        } catch (e: Exception) {
            logger.error("Unexpected error parsing archive: {}", archivePath, e)
            throw PluginFingerprintException.InvalidArchiveException(archivePath, e)
        }

        logger.info("Archive parsing complete: {} entries processed, {} skipped",
            entries.size, skippedEntries)

        return entries
    }

    fun extractClassFiles(archivePath: String): Map<String, ByteArray> {
        logger.debug("Extracting class files from: {}", archivePath)

        val file = File(archivePath)
        val classFiles = mutableMapOf<String, ByteArray>()

        if (!file.exists()) {
            logger.error("Archive file not found: {}", archivePath)
            throw PluginFingerprintException.FileNotFoundException(archivePath)
        }

        try {
            ZipFile(file).use { zipFile ->
                val classEntries = zipFile.entries().toList()
                    .filter { !it.isDirectory && it.name.endsWith(".class") }

                logger.debug("Found {} class files in archive", classEntries.size)

                classEntries.forEach { entry ->
                    try {
                        classFiles[entry.name] = zipFile.getInputStream(entry).readBytes()
                        logger.trace("Extracted class file: {}", entry.name)
                    } catch (e: Exception) {
                        logger.warn("Failed to extract class file: {} - {}",
                            entry.name, e.message)
                    }
                }
            }
        } catch (e: ZipException) {
            logger.error("Invalid or corrupted ZIP archive: {}", archivePath, e)
            throw PluginFingerprintException.InvalidArchiveException(archivePath, e)
        }

        logger.info("Class file extraction complete: {} files extracted", classFiles.size)

        return classFiles
    }

    private fun determineFileType(path: String): FileEntry.FileType {
        return when {
            path.endsWith(".class") -> FileEntry.FileType.CLASS
            path.endsWith(".jar") -> FileEntry.FileType.JAR
            path.endsWith(".xml") -> FileEntry.FileType.XML
            path.endsWith(".kotlin_module") -> FileEntry.FileType.KOTLIN_MODULE
            else -> FileEntry.FileType.RESOURCE
        }
    }

    private fun shouldHash(fileType: FileEntry.FileType): Boolean {
        return fileType == FileEntry.FileType.CLASS || fileType == FileEntry.FileType.XML
    }

    private fun calculateHash(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}