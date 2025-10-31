package com.jetbrains.plugin.fingerprint.infrastructure.parser

import com.jetbrains.plugin.fingerprint.domain.exception.PluginFingerprintException
import com.jetbrains.plugin.fingerprint.domain.model.FileEntry
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

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

                            if (entry.name.endsWith(".jar")) {
                                logger.debug("Found nested JAR: {}, extracting contents", entry.name)
                                val nestedEntries = extractNestedJar(
                                    zipFile.getInputStream(entry).readBytes(),
                                    entry.name
                                )
                                entries.addAll(nestedEntries)
                                logger.debug("Extracted {} entries from nested JAR: {}",
                                    nestedEntries.size, entry.name)
                            }

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

    private fun extractNestedJar(jarBytes: ByteArray, parentPath: String): List<FileEntry> {
        val entries = mutableListOf<FileEntry>()

        try {
            ZipInputStream(ByteArrayInputStream(jarBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val nestedPath = "$parentPath!/${entry.name}"
                        val fileType = determineFileType(entry.name)

                        val bytes = zis.readBytes()
                        val hash = if (shouldHash(fileType)) {
                            calculateHash(bytes)
                        } else null

                        entries.add(FileEntry(
                            path = nestedPath,
                            size = entry.size.let { if (it == -1L) bytes.size.toLong() else it },
                            isDirectory = false,
                            sha256Hash = hash,
                            fileType = fileType
                        ))
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to extract nested JAR: {} - {}", parentPath, e.message)
        }

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
                zipFile.entries().toList().forEach { entry ->
                    if (!entry.isDirectory) {
                        if (entry.name.endsWith(".class")) {
                            classFiles[entry.name] = zipFile.getInputStream(entry).readBytes()
                            logger.trace("Extracted class file: {}", entry.name)
                        }
                        else if (entry.name.endsWith(".jar")) {
                            logger.debug("Extracting classes from nested JAR: {}", entry.name)
                            val nestedClasses = extractClassesFromJar(
                                zipFile.getInputStream(entry).readBytes(),
                                entry.name
                            )
                            classFiles.putAll(nestedClasses)
                            logger.debug("Found {} classes in nested JAR: {}",
                                nestedClasses.size, entry.name)
                        }
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

    private fun extractClassesFromJar(jarBytes: ByteArray, parentPath: String): Map<String, ByteArray> {
        val classFiles = mutableMapOf<String, ByteArray>()

        try {
            ZipInputStream(ByteArrayInputStream(jarBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".class")) {
                        val nestedPath = "$parentPath!/${entry.name}"
                        classFiles[nestedPath] = zis.readBytes()
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to extract classes from nested JAR: {} - {}",
                parentPath, e.message)
        }

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