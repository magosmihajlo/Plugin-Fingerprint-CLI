package com.jetbrains.plugin.fingerprint.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.jetbrains.plugin.fingerprint.application.AnalyzeUseCase
import com.jetbrains.plugin.fingerprint.application.CompareUseCase

class PluginFingerprintCli : CliktCommand(
    name = "plugin-fingerprint",
    help = "Analyze and compare JetBrains plugin structures"
) {
    override fun run() = Unit
}

class AnalyzeCommand : CliktCommand(
    name = "analyze",
    help = "Analyze a plugin archive"
) {
    private val input by option("-i", "--input", help = "Path to plugin JAR/ZIP").required()
    private val output by option("-o", "--output", help = "Output path prefix").required()

    override fun run() {
        try {
            val useCase = AnalyzeUseCase()
            useCase.execute(input, output)
        } catch (e: Exception) {
            echo("Error: ${e.message}", err = true)
            throw e
        }
    }
}

class CompareCommand : CliktCommand(
    name = "compare",
    help = "Compare two analyzed plugins"
) {
    private val plugin1 by option("--plugin1", help = "First plugin structure JSON").required()
    private val plugin2 by option("--plugin2", help = "Second plugin structure JSON").required()
    private val fp1 by option("--fp1", help = "First plugin fingerprint JSON").required()
    private val fp2 by option("--fp2", help = "Second plugin fingerprint JSON").required()
    private val output by option("-o", "--output", help = "Output path prefix").required()

    override fun run() {
        try {
            val useCase = CompareUseCase()
            useCase.execute(plugin1, plugin2, fp1, fp2, output)
        } catch (e: Exception) {
            echo("Error: ${e.message}", err = true)
            throw e
        }
    }
}

fun main(args: Array<String>) = PluginFingerprintCli()
    .subcommands(AnalyzeCommand(), CompareCommand())
    .main(args)