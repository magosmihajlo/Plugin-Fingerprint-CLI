package com.jetbrains.plugin.fingerprint.infrastructure.fingerprint

import com.jetbrains.plugin.fingerprint.domain.model.MinHashSignature
import kotlin.random.Random

class MinHashGenerator(
    private val numHashes: Int = MinHashSignature.DEFAULT_NUM_HASHES,
    private val seed: Long = 42L
) {
    private val hashFunctions: List<HashFunction>

    init {
        val random = Random(seed)
        hashFunctions = (0 until numHashes).map {
            HashFunction(
                a = random.nextInt(1, Int.MAX_VALUE),
                b = random.nextInt(0, Int.MAX_VALUE),
                prime = 2147483647 // Large prime number
            )
        }
    }

    fun generate(features: Set<String>, pluginId: String): MinHashSignature {
        val signatures = IntArray(numHashes) { Int.MAX_VALUE }

        features.forEach { feature ->
            val featureHash = feature.hashCode()

            hashFunctions.forEachIndexed { index, hashFunc ->
                val hashValue = hashFunc.hash(featureHash)
                if (hashValue < signatures[index]) {
                    signatures[index] = hashValue
                }
            }
        }

        return MinHashSignature(
            pluginId = pluginId,
            signatures = signatures.toList(),
            numHashes = numHashes
        )
    }

    fun estimateSimilarity(sig1: MinHashSignature, sig2: MinHashSignature): Double {
        require(sig1.numHashes == sig2.numHashes) {
            "Signatures must have same number of hashes"
        }

        val matches = sig1.signatures.zip(sig2.signatures).count { (a, b) -> a == b }
        return matches.toDouble() / sig1.numHashes
    }

    private data class HashFunction(val a: Int, val b: Int, val prime: Int) {
        fun hash(x: Int): Int {
            // Universal hashing: h(x) = ((a * x + b) mod prime)
            val hash = ((a.toLong() * x + b) % prime).toInt()
            return if (hash < 0) hash + prime else hash
        }
    }
}