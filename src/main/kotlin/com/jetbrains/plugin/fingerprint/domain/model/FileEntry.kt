package com.jetbrains.plugin.fingerprint.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FileEntry(
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val sha256Hash: String?,
    val fileType: FileType
) {
    @Serializable
    enum class FileType {
        CLASS, JAR, XML, KOTLIN_MODULE, RESOURCE, UNKNOWN
    }
}