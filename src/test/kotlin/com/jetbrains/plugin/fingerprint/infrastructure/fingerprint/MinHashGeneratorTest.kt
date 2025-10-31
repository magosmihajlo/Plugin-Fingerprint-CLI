package com.jetbrains.plugin.fingerprint.infrastructure.fingerprint

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class MinHashGeneratorTest : StringSpec({

    "should generate signature with correct number of hashes" {
        val generator = MinHashGenerator(numHashes = 128)
        val features = setOf("class:A", "method:A::foo", "field:A::bar")

        val signature = generator.generate(features, "test-plugin")

        signature.numHashes shouldBe 128
        signature.signatures.size shouldBe 128
        signature.pluginId shouldBe "test-plugin"
    }

    "should generate deterministic signatures for same input" {
        val generator = MinHashGenerator(seed = 42)
        val features = setOf("class:A", "method:A::foo", "field:A::bar")

        val sig1 = generator.generate(features, "plugin1")
        val sig2 = generator.generate(features, "plugin2")

        sig1.signatures shouldBe sig2.signatures
    }

    "should estimate 100% similarity for identical feature sets" {
        val generator = MinHashGenerator()
        val features = setOf("class:A", "method:A::foo", "field:A::bar")

        val sig1 = generator.generate(features, "plugin1")
        val sig2 = generator.generate(features, "plugin2")

        val similarity = generator.estimateSimilarity(sig1, sig2)
        similarity shouldBe 1.0
    }

    "should estimate 0% similarity for completely different feature sets" {
        val generator = MinHashGenerator()
        val features1 = setOf("class:A", "method:A::foo")
        val features2 = setOf("class:Z", "method:Z::bar")

        val sig1 = generator.generate(features1, "plugin1")
        val sig2 = generator.generate(features2, "plugin2")

        val similarity = generator.estimateSimilarity(sig1, sig2)
        similarity shouldBe 0.0
    }

    "should approximate Jaccard similarity for partially overlapping sets" {
        val generator = MinHashGenerator(numHashes = 256)

        // Create sets with known Jaccard similarity
        // Set1: {A, B, C, D, E} (5 elements)
        // Set2: {C, D, E, F, G} (5 elements)
        // Intersection: {C, D, E} (3 elements)
        // Union: {A, B, C, D, E, F, G} (7 elements)
        // Jaccard = 3/7 = 0.428
        val features1 = setOf("A", "B", "C", "D", "E")
        val features2 = setOf("C", "D", "E", "F", "G")

        val sig1 = generator.generate(features1, "plugin1")
        val sig2 = generator.generate(features2, "plugin2")

        val estimatedSimilarity = generator.estimateSimilarity(sig1, sig2)
        val expectedJaccard = 3.0 / 7.0 // 0.428

        estimatedSimilarity shouldBeGreaterThan (expectedJaccard - 0.20)
        estimatedSimilarity shouldBeLessThan (expectedJaccard + 0.20)
    }

    "should handle empty feature sets" {
        val generator = MinHashGenerator()
        val emptyFeatures = emptySet<String>()

        val signature = generator.generate(emptyFeatures, "empty-plugin")

        signature.signatures.size shouldBe 128
        signature.signatures.all { it == Int.MAX_VALUE } shouldBe true
    }

    "should handle large feature sets efficiently" {
        val generator = MinHashGenerator()
        val largeFeatureSet = (1..10000).map { "feature:$it" }.toSet()

        val start = System.currentTimeMillis()
        val signature = generator.generate(largeFeatureSet, "large-plugin")
        val duration = System.currentTimeMillis() - start

        signature.signatures.size shouldBe 128
        duration shouldBeLessThan 1000
    }

    "should throw exception when comparing signatures with different hash counts" {
        val gen1 = MinHashGenerator(numHashes = 64)
        val gen2 = MinHashGenerator(numHashes = 128)

        val sig1 = gen1.generate(setOf("A"), "plugin1")
        val sig2 = gen2.generate(setOf("B"), "plugin2")

        val result = runCatching { gen1.estimateSimilarity(sig1, sig2) }
        result.isFailure shouldBe true
    }

    "should produce different signatures with different seeds" {
        val gen1 = MinHashGenerator(seed = 42)
        val gen2 = MinHashGenerator(seed = 99)
        val features = setOf("class:A", "method:A::foo")

        val sig1 = gen1.generate(features, "plugin")
        val sig2 = gen2.generate(features, "plugin")

        sig1.signatures shouldNotBe sig2.signatures
    }
})