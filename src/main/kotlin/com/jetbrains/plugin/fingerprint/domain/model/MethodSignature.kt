package com.jetbrains.plugin.fingerprint.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MethodSignature(
    val name: String,
    val descriptor: String,
    val returnType: String,
    val parameterTypes: List<String>
)