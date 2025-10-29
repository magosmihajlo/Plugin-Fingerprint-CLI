package com.jetbrains.plugin.fingerprint.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Fingerprint(
    val pluginId: String,
    val structuralHash: String,
    val features: Set<String>
)