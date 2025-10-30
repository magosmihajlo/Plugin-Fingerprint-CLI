package com.jetbrains.plugin.fingerprint.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MinHashSignature(
    val pluginId: String,
    val signatures: List<Int>,
    val numHashes: Int
) {
    companion object {
        const val DEFAULT_NUM_HASHES = 128
    }
}