package com.jetbrains.plugin.fingerprint.application

import com.jetbrains.plugin.fingerprint.domain.exception.PluginFingerprintException
import com.jetbrains.plugin.fingerprint.infrastructure.fingerprint.FingerprintGenerator
import com.jetbrains.plugin.fingerprint.infrastructure.parser.ArchiveParser
import com.jetbrains.plugin.fingerprint.infrastructure.parser.BytecodeParser
import com.jetbrains.plugin.fingerprint.infrastructure.storage.OutputStorage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AnalyzeUseCaseTest : StringSpec({

    beforeTest {
        clearAllMocks()
    }

    "should throw exception when archive path is empty" {
        val useCase = AnalyzeUseCase()

        shouldThrow<IllegalArgumentException> {
            useCase.execute("", "output")
        }
    }

    "should throw exception when file does not exist" {
        val useCase = AnalyzeUseCase()

        shouldThrow<IllegalArgumentException> {
            useCase.execute("/nonexistent/file.jar", "output")
        }
    }

    "should throw exception when file is not JAR or ZIP" {
        val tempFile = File.createTempFile("test", ".txt")
        tempFile.deleteOnExit()

        val useCase = AnalyzeUseCase()

        shouldThrow<IllegalArgumentException> {
            useCase.execute(tempFile.absolutePath, "output")
        }
    }

    "should successfully analyze a valid plugin archive" {
        val tempJar = createTestJar()
        val outputDir = File.createTempFile("test-output", "").apply {
            delete()
            mkdir()
            deleteOnExit()
        }
        val outputPath = "${outputDir.absolutePath}/test-plugin"

        val useCase = AnalyzeUseCase()
        useCase.execute(tempJar.absolutePath, outputPath)

        // Verify output files exist
        File("$outputPath.structure.json").shouldExist()
        File("$outputPath.fingerprint.json").shouldExist()
        File("$outputPath.report.txt").shouldExist()

        // Cleanup
        tempJar.delete()
        outputDir.deleteRecursively()
    }

    "should invoke all dependencies in correct order" {
        val mockParser = mockk<ArchiveParser>(relaxed = true)
        val mockBytecodeParser = mockk<BytecodeParser>(relaxed = true)
        val mockFingerprintGenerator = mockk<FingerprintGenerator>(relaxed = true)
        val mockStorage = mockk<OutputStorage>(relaxed = true)

        every { mockParser.parse(any()) } returns emptyList()
        every { mockParser.extractClassFiles(any()) } returns emptyMap()
        every { mockBytecodeParser.parseClasses(any()) } returns emptyList()
        every { mockFingerprintGenerator.generate(any()) } returns mockk(relaxed = true)

        val tempJar = createTestJar()
        val useCase = AnalyzeUseCase(
            mockParser,
            mockBytecodeParser,
            mockFingerprintGenerator,
            mockStorage
        )

        useCase.execute(tempJar.absolutePath, "output")

        verifyOrder {
            mockParser.parse(tempJar.absolutePath)
            mockParser.extractClassFiles(tempJar.absolutePath)
            mockBytecodeParser.parseClasses(any())
            mockFingerprintGenerator.generate(any())
            mockStorage.writeStructure(any(), any())
            mockStorage.writeFingerprint(any(), any())
            mockStorage.writeSummaryReport(any(), any(), any())
        }

        tempJar.delete()
    }

    "should create PluginStructure with correct data" {
        val tempJar = createTestJarWithClasses()
        val outputDir = File.createTempFile("test-output", "").apply {
            delete()
            mkdir()
            deleteOnExit()
        }
        val outputPath = "${outputDir.absolutePath}/test-plugin"

        val capturedStructure = slot<com.jetbrains.plugin.fingerprint.domain.model.PluginStructure>()
        val mockStorage = mockk<OutputStorage>(relaxed = true)
        every { mockStorage.writeStructure(capture(capturedStructure), any()) } just Runs
        every { mockStorage.writeFingerprint(any(), any()) } just Runs
        every { mockStorage.writeSummaryReport(any(), any(), any()) } just Runs

        val useCase = AnalyzeUseCase(storage = mockStorage)
        useCase.execute(tempJar.absolutePath, outputPath)

        val structure = capturedStructure.captured
        structure.pluginId shouldBe tempJar.nameWithoutExtension
        structure.totalClasses shouldBe 1
        structure.classInfos.size shouldBe 1
        structure.classInfos.first().className shouldContain "TestClass"

        tempJar.delete()
        outputDir.deleteRecursively()
    }

    "should handle archives with no classes gracefully" {
        val tempJar = createEmptyJar()
        val outputDir = File.createTempFile("test-output", "").apply {
            delete()
            mkdir()
            deleteOnExit()
        }
        val outputPath = "${outputDir.absolutePath}/empty-plugin"

        val useCase = AnalyzeUseCase()
        useCase.execute(tempJar.absolutePath, outputPath)

        val structureFile = File("$outputPath.structure.json")
        structureFile.shouldExist()
        val content = structureFile.readText()
        content.replace("\\s".toRegex(), "") shouldContain "\"totalClasses\":0"
        tempJar.delete()
        outputDir.deleteRecursively()
    }

    "should propagate PluginFingerprintException from parser" {
        val mockParser = mockk<ArchiveParser>()
        every { mockParser.parse(any()) } throws
                PluginFingerprintException.InvalidArchiveException("test.jar")

        val tempJar = createTestJar()
        val useCase = AnalyzeUseCase(archiveParser = mockParser)

        shouldThrow<PluginFingerprintException.InvalidArchiveException> {
            useCase.execute(tempJar.absolutePath, "output")
        }

        tempJar.delete()
    }
})

// Helper functions to create test archives
private fun createTestJar(): File {
    val tempFile = File.createTempFile("test-plugin", ".jar")
    tempFile.deleteOnExit()

    ZipOutputStream(tempFile.outputStream()).use { zos ->
        // Add a simple resource file
        val entry = ZipEntry("META-INF/plugin.xml")
        zos.putNextEntry(entry)
        zos.write("<idea-plugin></idea-plugin>".toByteArray())
        zos.closeEntry()
    }

    return tempFile
}

private fun createEmptyJar(): File {
    val tempFile = File.createTempFile("empty-plugin", ".jar")
    tempFile.deleteOnExit()

    ZipOutputStream(tempFile.outputStream()).use { zos ->
        val entry = ZipEntry("META-INF/MANIFEST.MF")
        zos.putNextEntry(entry)
        zos.write("Manifest-Version: 1.0\n".toByteArray())
        zos.closeEntry()
    }

    return tempFile
}

private fun createTestJarWithClasses(): File {
    val tempFile = File.createTempFile("test-plugin", ".jar")
    tempFile.deleteOnExit()

    ZipOutputStream(tempFile.outputStream()).use { zos ->
        // Add a simple class file
        val classBytes = generateTestClass()
        val entry = ZipEntry("com/example/TestClass.class")
        zos.putNextEntry(entry)
        zos.write(classBytes)
        zos.closeEntry()

        // Add plugin.xml
        val xmlEntry = ZipEntry("META-INF/plugin.xml")
        zos.putNextEntry(xmlEntry)
        zos.write("<idea-plugin></idea-plugin>".toByteArray())
        zos.closeEntry()
    }

    return tempFile
}

private fun generateTestClass(): ByteArray {
    val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    cw.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC,
        "com/example/TestClass",
        null,
        "java/lang/Object",
        null
    )

    // Add constructor
    val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
    mv.visitCode()
    mv.visitVarInsn(Opcodes.ALOAD, 0)
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
    mv.visitInsn(Opcodes.RETURN)
    mv.visitMaxs(1, 1)
    mv.visitEnd()

    // Add a simple method
    val mv2 = cw.visitMethod(Opcodes.ACC_PUBLIC, "doSomething", "()V", null, null)
    mv2.visitCode()
    mv2.visitInsn(Opcodes.RETURN)
    mv2.visitMaxs(0, 1)
    mv2.visitEnd()

    cw.visitEnd()
    return cw.toByteArray()
}