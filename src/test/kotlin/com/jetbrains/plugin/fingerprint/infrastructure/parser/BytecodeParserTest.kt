package com.jetbrains.plugin.fingerprint.infrastructure.parser

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

class BytecodeParserTest : StringSpec({

    val parser = BytecodeParser()

    "should parse simple class with methods and fields" {
        val classBytes = generateSimpleClass()
        val classFiles = mapOf("SimpleClass.class" to classBytes)

        val results = parser.parseClasses(classFiles)

        results shouldHaveSize 1
        val classInfo = results.first()

        classInfo.className shouldBe "com.example.SimpleClass"
        classInfo.packageName shouldBe "com.example"
        classInfo.superClass shouldBe "java.lang.Object"
        classInfo.isInterface shouldBe false
        classInfo.isAbstract shouldBe false
    }

    "should extract method signatures correctly" {
        val classBytes = generateClassWithMethods()
        val classFiles = mapOf("TestClass.class" to classBytes)

        val results = parser.parseClasses(classFiles)
        val classInfo = results.first()

        classInfo.methods shouldHaveSize 3 // constructor + 2 methods

        val methodNames = classInfo.methods.map { it.name }
        methodNames shouldContain "<init>"
        methodNames shouldContain "doSomething"
        methodNames shouldContain "calculate"

        // Check method with parameters
        val calculateMethod = classInfo.methods.find { it.name == "calculate" }
        calculateMethod shouldNotBe null
        calculateMethod?.parameterTypes?.shouldHaveSize(2)
        calculateMethod?.returnType shouldBe "int"
    }

    "should extract field information" {
        val classBytes = generateClassWithFields()
        val classFiles = mapOf("TestClass.class" to classBytes)

        val results = parser.parseClasses(classFiles)
        val classInfo = results.first()

        classInfo.fields shouldHaveSize 2

        val fieldNames = classInfo.fields.map { it.name }
        fieldNames shouldContain "counter"
        fieldNames shouldContain "name"
    }

    "should detect interface classes" {
        val classBytes = generateInterface()
        val classFiles = mapOf("TestInterface.class" to classBytes)

        val results = parser.parseClasses(classFiles)
        val classInfo = results.first()

        classInfo.isInterface shouldBe true
        classInfo.className shouldBe "com.example.TestInterface"
    }

    "should extract inheritance information" {
        val classBytes = generateClassWithInheritance()
        val classFiles = mapOf("ChildClass.class" to classBytes)

        val results = parser.parseClasses(classFiles)
        val classInfo = results.first()

        classInfo.superClass shouldBe "com.example.BaseClass"
        classInfo.interfaces shouldContain "com.example.MyInterface"
    }

    "should handle multiple classes" {
        val class1 = generateSimpleClass()
        val class2 = generateClassWithMethods()
        val classFiles = mapOf(
            "Class1.class" to class1,
            "Class2.class" to class2
        )

        val results = parser.parseClasses(classFiles)

        results shouldHaveSize 2
    }

    "should skip invalid bytecode gracefully" {
        val validClass = generateSimpleClass()
        val invalidClass = byteArrayOf(0xCA.toByte(), 0xFE.toByte()) // Invalid magic number

        val classFiles = mapOf(
            "ValidClass.class" to validClass,
            "InvalidClass.class" to invalidClass
        )

        val results = parser.parseClasses(classFiles)

        // Should parse only the valid class
        results shouldHaveSize 1
    }

    "should handle empty input" {
        val results = parser.parseClasses(emptyMap())
        results shouldHaveSize 0
    }

    "should parse array types in method descriptors" {
        val classBytes = generateClassWithArrayMethods()
        val classFiles = mapOf("ArrayClass.class" to classBytes)

        val results = parser.parseClasses(classFiles)
        val classInfo = results.first()

        val arrayMethod = classInfo.methods.find { it.name == "processArray" }
        arrayMethod shouldNotBe null
        arrayMethod?.parameterTypes?.first() shouldBe "int[]"
        arrayMethod?.returnType shouldBe "java.lang.String[]"
    }
})

// Helper functions to generate bytecode for testing
private fun generateSimpleClass(): ByteArray {
    val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    cw.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC,
        "com/example/SimpleClass",
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

    cw.visitEnd()
    return cw.toByteArray()
}

private fun generateClassWithMethods(): ByteArray {
    val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    cw.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC,
        "com/example/TestClass",
        null,
        "java/lang/Object",
        null
    )

    // Constructor
    var mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
    mv.visitCode()
    mv.visitVarInsn(Opcodes.ALOAD, 0)
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
    mv.visitInsn(Opcodes.RETURN)
    mv.visitMaxs(1, 1)
    mv.visitEnd()

    // doSomething() method
    mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "doSomething", "()V", null, null)
    mv.visitCode()
    mv.visitInsn(Opcodes.RETURN)
    mv.visitMaxs(0, 1)
    mv.visitEnd()

    // calculate(int, int) method
    mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "calculate", "(II)I", null, null)
    mv.visitCode()
    mv.visitVarInsn(Opcodes.ILOAD, 1)
    mv.visitVarInsn(Opcodes.ILOAD, 2)
    mv.visitInsn(Opcodes.IADD)
    mv.visitInsn(Opcodes.IRETURN)
    mv.visitMaxs(2, 3)
    mv.visitEnd()

    cw.visitEnd()
    return cw.toByteArray()
}

private fun generateClassWithFields(): ByteArray {
    val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    cw.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC,
        "com/example/TestClass",
        null,
        "java/lang/Object",
        null
    )

    // Add fields
    cw.visitField(Opcodes.ACC_PRIVATE, "counter", "I", null, null).visitEnd()
    cw.visitField(Opcodes.ACC_PRIVATE, "name", "Ljava/lang/String;", null, null).visitEnd()

    // Constructor
    val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
    mv.visitCode()
    mv.visitVarInsn(Opcodes.ALOAD, 0)
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
    mv.visitInsn(Opcodes.RETURN)
    mv.visitMaxs(1, 1)
    mv.visitEnd()

    cw.visitEnd()
    return cw.toByteArray()
}

private fun generateInterface(): ByteArray {
    val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    cw.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC + Opcodes.ACC_INTERFACE + Opcodes.ACC_ABSTRACT,
        "com/example/TestInterface",
        null,
        "java/lang/Object",
        null
    )

    // Add abstract method
    cw.visitMethod(
        Opcodes.ACC_PUBLIC + Opcodes.ACC_ABSTRACT,
        "process",
        "()V",
        null,
        null
    ).visitEnd()

    cw.visitEnd()
    return cw.toByteArray()
}

private fun generateClassWithInheritance(): ByteArray {
    val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    cw.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC,
        "com/example/ChildClass",
        null,
        "com/example/BaseClass",
        arrayOf("com/example/MyInterface")
    )

    // Constructor
    val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
    mv.visitCode()
    mv.visitVarInsn(Opcodes.ALOAD, 0)
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "com/example/BaseClass", "<init>", "()V", false)
    mv.visitInsn(Opcodes.RETURN)
    mv.visitMaxs(1, 1)
    mv.visitEnd()

    cw.visitEnd()
    return cw.toByteArray()
}

private fun generateClassWithArrayMethods(): ByteArray {
    val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    cw.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC,
        "com/example/ArrayClass",
        null,
        "java/lang/Object",
        null
    )

    // Constructor
    var mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
    mv.visitCode()
    mv.visitVarInsn(Opcodes.ALOAD, 0)
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
    mv.visitInsn(Opcodes.RETURN)
    mv.visitMaxs(1, 1)
    mv.visitEnd()

    // processArray(int[]) : String[]
    mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "processArray", "([I)[Ljava/lang/String;", null, null)
    mv.visitCode()
    mv.visitInsn(Opcodes.ACONST_NULL)
    mv.visitInsn(Opcodes.ARETURN)
    mv.visitMaxs(1, 2)
    mv.visitEnd()

    cw.visitEnd()
    return cw.toByteArray()
}