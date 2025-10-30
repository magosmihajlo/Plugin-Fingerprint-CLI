package com.jetbrains.plugin.fingerprint.infrastructure.parser

import com.jetbrains.plugin.fingerprint.domain.exception.PluginFingerprintException
import com.jetbrains.plugin.fingerprint.domain.model.ClassInfo
import com.jetbrains.plugin.fingerprint.domain.model.FieldSignature
import com.jetbrains.plugin.fingerprint.domain.model.MethodSignature
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.FieldNode
import org.slf4j.LoggerFactory

class BytecodeParser {

    private val logger = LoggerFactory.getLogger(BytecodeParser::class.java)

    fun parseClasses(classFiles: Map<String, ByteArray>): List<ClassInfo> {
        logger.debug("Starting bytecode parsing for {} class files", classFiles.size)
        return classFiles.mapNotNull { (path, bytes) ->
            try {
                parseClass(bytes, path)
            } catch (e: Exception) {
                when (e) {
                    is PluginFingerprintException -> throw e
                    else -> {
                        logger.warn("Failed to parse class file: {} - {}", path, e.message)
                        logger.debug("Parse error details for: {}", path, e)
                        null
                    }
                }
            }
        }
    }

    private fun parseClass(bytes: ByteArray, path: String = "unknown"): ClassInfo {
        logger.trace("Parsing class: {}", path)
        try {
            val classNode = ClassNode()
            val reader = ClassReader(bytes)
            reader.accept(classNode, ClassReader.SKIP_DEBUG)

            val className = classNode.name.replace('/', '.')
            val packageName = className.substringBeforeLast('.', "")

            logger.trace("Parsed class: {} (package: {})", className, packageName)

            return ClassInfo(
                className = className,
                packageName = packageName,
                superClass = classNode.superName?.replace('/', '.'),
                interfaces = classNode.interfaces.map { it.replace('/', '.') },
                methods = parseMethods(classNode.methods, className),
                fields = parseFields(classNode.fields),
                isInterface = (classNode.access and Opcodes.ACC_INTERFACE) != 0,
                isAbstract = (classNode.access and Opcodes.ACC_ABSTRACT) != 0
            )
        } catch (e: Exception) {
            logger.error("Failed to parse bytecode for class: {}", path, e)
            throw PluginFingerprintException.BytecodeParsingException(path, e)
        }
    }

    private fun parseMethods(methods: List<MethodNode>?, className: String): List<MethodSignature> {
        if (methods == null) return emptyList()

        return methods.mapNotNull { method ->
            try {
                MethodSignature(
                    name = method.name,
                    descriptor = method.desc,
                    returnType = extractReturnType(method.desc),
                    parameterTypes = extractParameterTypes(method.desc)
                )
            } catch (e: Exception) {
                logger.debug("Failed to parse method {}.{}: {}",
                    className, method.name, e.message)
                null
            }
        }
    }

    private fun parseFields(fields: List<FieldNode>?): List<FieldSignature> {
        if (fields == null) return emptyList()

        return fields.map { field ->
            FieldSignature(name = field.name, type = field.desc)
        }
    }

    private fun extractReturnType(descriptor: String): String {
        return try {
            val returnPart = descriptor.substringAfter(')', "")
            if (returnPart.isEmpty()) "void"
            else parseType(returnPart, 0).first
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun extractParameterTypes(descriptor: String): List<String> {
        return try {
            val paramsPart = descriptor.substringAfter('(', "").substringBefore(')', "")
            if (paramsPart.isEmpty()) return emptyList()

            val types = mutableListOf<String>()
            var i = 0
            while (i < paramsPart.length) {
                val (type, nextIndex) = parseType(paramsPart, i)
                types.add(type)
                i = nextIndex
            }
            types
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseType(descriptor: String, startIndex: Int): Pair<String, Int> {
        if (startIndex >= descriptor.length) {
            return "unknown" to descriptor.length
        }

        var index = startIndex
        var arrayDepth = 0

        while (index < descriptor.length && descriptor[index] == '[') {
            arrayDepth++
            index++
        }

        if (index >= descriptor.length) {
            return "unknown" to descriptor.length
        }

        val (baseType, nextIndex) = when (descriptor[index]) {
            'L' -> {
                val endIndex = descriptor.indexOf(';', index)
                if (endIndex == -1) {
                    "unknown" to descriptor.length
                } else {
                    val type = descriptor.substring(index + 1, endIndex).replace('/', '.')
                    type to (endIndex + 1)
                }
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