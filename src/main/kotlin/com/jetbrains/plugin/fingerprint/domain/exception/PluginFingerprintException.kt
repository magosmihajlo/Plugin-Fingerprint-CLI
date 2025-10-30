package com.jetbrains.plugin.fingerprint.domain.exception

sealed class PluginFingerprintException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    class InvalidArchiveException(path: String, cause: Throwable? = null) :
        PluginFingerprintException("Invalid or corrupted archive: $path", cause)

    class BytecodeParsingException(className: String, cause: Throwable) :
        PluginFingerprintException("Failed to parse bytecode for class: $className", cause)

    class FileNotFoundException(path: String) :
        PluginFingerprintException("File not found: $path")

    class InvalidFileFormatException(path: String, expectedFormats: List<String>) :
        PluginFingerprintException(
            "Invalid file format: $path. Expected: ${expectedFormats.joinToString(", ")}"
        )

    class StorageException(operation: String, path: String, cause: Throwable) :
        PluginFingerprintException("Storage $operation failed for: $path", cause)
}