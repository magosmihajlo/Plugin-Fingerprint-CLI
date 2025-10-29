package com.jetbrains.plugin.fingerprint.infrastructure.parser

import com.jetbrains.plugin.fingerprint.domain.model.ClassInfo
import com.jetbrains.plugin.fingerprint.domain.model.FieldSignature
import com.jetbrains.plugin.fingerprint.domain.model.MethodSignature
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.FieldNode

class BytecodeParser {

    fun parseClasses(classFiles: Map<String, ByteArray>): List<ClassInfo> {
        return classFiles.mapNotNull { (path, bytes) ->
            try {
                parseClass(bytes)
            } catch (e: Exception) {
                println("Warning: Failed to parse $path: ${e.message}")
                null
            }
        }
    }

    private fun parseClass(bytes: ByteArray): ClassInfo {
        val classNode = ClassNode()
        val reader = ClassReader(bytes)
        reader.accept(classNode, ClassReader.SKIP_DEBUG)

        val className = classNode.name.replace('/', '.')
        val packageName = className.substringBeforeLast('.', "")

        return ClassInfo(
            className = className,
            packageName = packageName,
            superClass = classNode.superName?.replace('/', '.'),
            interfaces = classNode.interfaces.map { it.replace('/', '.') },
            methods = parseMethods(classNode.methods),
            fields = parseFields(classNode.fields),
            isInterface = (classNode.access and Opcodes.ACC_INTERFACE) != 0,
            isAbstract = (classNode.access and Opcodes.ACC_ABSTRACT) != 0
        )
    }

    private fun parseMethods(methods: List<MethodNode>?): List<MethodSignature> {
        if (methods == null) return emptyList()

        return methods.map { method ->
            MethodSignature(
                name = method.name,
                descriptor = method.desc,
                returnType = extractReturnType(method.desc),
                parameterTypes = extractParameterTypes(method.desc)
            )
        }
    }

    private fun parseFields(fields: List<FieldNode>?): List<FieldSignature> {
        if (fields == null) return emptyList()

        return fields.map { field ->
            FieldSignature(name = field.name, type = field.desc)
        }
    }

    private fun extractReturnType(descriptor: String): String {
        val returnPart = descriptor.substringAfter(')')
        return parseType(returnPart, 0).first
    }

    private fun extractParameterTypes(descriptor: String): List<String> {
        val paramsPart = descriptor.substringAfter('(').substringBefore(')')
        if (paramsPart.isEmpty()) return emptyList()

        val types = mutableListOf<String>()
        var i = 0
        while (i < paramsPart.length) {
            val (type, nextIndex) = parseType(paramsPart, i)
            types.add(type)
            i = nextIndex
        }
        return types
    }

    private fun parseType(descriptor: String, startIndex: Int): Pair<String, Int> {
        var index = startIndex
        var arrayDepth = 0

        while (index < descriptor.length && descriptor[index] == '[') {
            arrayDepth++
            index++
        }

        val (baseType, nextIndex) = when (descriptor[index]) {
            'L' -> {
                val endIndex = descriptor.indexOf(';', index)
                val type = descriptor.substring(index + 1, endIndex).replace('/', '.')
                type to (endIndex + 1)
            }
            'V' -> "void" to (index + 1)
            'I' -> "int" to (index + 1)
            'Z' -> "boolean" to (index + 1)
            'B' -> "byte" to (index + 1)
            'C' -> "char" to (index + 1)
            'S' -> "short" to (index + 1)
            'J' -> "long" to (index + 1)
            'F' -> "float" to (index + 1)
            'D' -> "double" to (index + 1)
            else -> "unknown" to (index + 1)
        }

        val fullType = baseType + "[]".repeat(arrayDepth)
        return fullType to nextIndex
    }
}