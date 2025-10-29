package com.jetbrains.plugin.fingerprint.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FieldSignature(
    val name: String,
    val type: String
)