package com.jetbrains.plugin.fingerprint.infrastructure.parser

import com.jetbrains.plugin.fingerprint.domain.model.FileEntry
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

class ArchiveParser {

    fun parse(archivePath: String): List<FileEntry> {
        val file = File(archivePath)
        if (!file.exists()) throw IllegalArgumentException("File not found: $archivePath")

        val entries = mutableListOf<FileEntry>()

        ZipFile(file).use { zipFile ->
            zipFile.entries().toList().forEach { entry ->
                if (!entry.isDirectory) {
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
                }
            }
        }

        return entries
    }

    fun extractClassFiles(archivePath: String): Map<String, ByteArray> {
        val file = File(archivePath)
        val classFiles = mutableMapOf<String, ByteArray>()

        ZipFile(file).use { zipFile ->
            zipFile.entries().toList()
                .filter { !it.isDirectory && it.name.endsWith(".class") }
                .forEach { entry ->
                    classFiles[entry.name] = zipFile.getInputStream(entry).readBytes()
                }
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