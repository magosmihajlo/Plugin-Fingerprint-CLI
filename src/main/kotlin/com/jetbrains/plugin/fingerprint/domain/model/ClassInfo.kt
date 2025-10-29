package com.jetbrains.plugin.fingerprint.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ClassInfo(
    val className: String,
    val packageName: String,
    val superClass: String?,
    val interfaces: List<String>,
    val methods: List<MethodSignature>,
    val fields: List<FieldSignature>,
    val isInterface: Boolean,
    val isAbstract: Boolean
)