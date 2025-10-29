package com.jetbrains.plugin.fingerprint.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ComparisonResult(
    val plugin1: String,
    val plugin2: String,
    val similarityScore: Double,
    val addedClasses: List<String>,
    val removedClasses: List<String>
)