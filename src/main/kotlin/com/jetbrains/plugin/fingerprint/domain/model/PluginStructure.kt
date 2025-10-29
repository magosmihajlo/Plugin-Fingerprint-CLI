package com.jetbrains.plugin.fingerprint.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PluginStructure(
    val pluginId: String,
    val fileEntries: List<FileEntry>,
    val classInfos: List<ClassInfo>,
    val totalFiles: Int,
    val totalClasses: Int,
    val totalSize: Long
)