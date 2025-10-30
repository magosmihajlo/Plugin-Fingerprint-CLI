package com.jetbrains.plugin.fingerprint.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.jetbrains.plugin.fingerprint.application.AnalyzeUseCase
import com.jetbrains.plugin.fingerprint.application.CompareUseCase
import com.jetbrains.plugin.fingerprint.domain.exception.PluginFingerprintException
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

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

    private val logger = LoggerFactory.getLogger(AnalyzeCommand::class.java)
    private val input by option("-i", "--input", help = "Path to plugin JAR/ZIP").required()
    private val output by option("-o", "--output", help = "Output path prefix").required()

    override fun run() {
        try {
            logger.info("Analyze command started with input: {}, output: {}", input, output)
            val useCase = AnalyzeUseCase()
            useCase.execute(input, output)
            logger.info("Analyze command completed successfully")
        } catch (e: PluginFingerprintException) {
            logger.error("Analysis failed with domain exception", e)
            echo("❌ Error: ${e.message}", err = true)
            exitProcess(1)
        } catch (e: Exception) {
            logger.error("Analysis failed with unexpected exception", e)
            echo("❌ Unexpected error: ${e.message}", err = true)
            echo("Check logs/plugin-fingerprint.log for details", err = true)
            exitProcess(2)
        }
    }
}

class CompareCommand : CliktCommand(
    name = "compare",
    help = "Compare two analyzed plugins"
) {

    private val logger = LoggerFactory.getLogger(CompareCommand::class.java)
    private val plugin1 by option("--plugin1", help = "First plugin structure JSON").required()
    private val plugin2 by option("--plugin2", help = "Second plugin structure JSON").required()
    private val fp1 by option("--fp1", help = "First plugin fingerprint JSON").required()
    private val fp2 by option("--fp2", help = "Second plugin fingerprint JSON").required()
    private val output by option("-o", "--output", help = "Output path prefix").required()

    override fun run() {
        try {
            logger.info("Compare command started")
            logger.debug("Plugin 1: {}, Fingerprint 1: {}", plugin1, fp1)
            logger.debug("Plugin 2: {}, Fingerprint 2: {}", plugin2, fp2)
            logger.debug("Output: {}", output)

            val useCase = CompareUseCase()
            useCase.execute(plugin1, plugin2, fp1, fp2, output)
            logger.info("Compare command completed successfully")
        } catch (e: PluginFingerprintException) {
            logger.error("Comparison failed with domain exception", e)
            echo("❌ Error: ${e.message}", err = true)
            exitProcess(1)
        } catch (e: Exception) {
            logger.error("Comparison failed with unexpected exception", e)
            echo("❌ Unexpected error: ${e.message}", err = true)
            echo("Check logs/plugin-fingerprint.log for details", err = true)
            exitProcess(2)
        }
    }
}

fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("Main")
    logger.info("Application started with args: {}", args.joinToString(" "))

    try {
        PluginFingerprintCli()
            .subcommands(AnalyzeCommand(), CompareCommand())
            .main(args)
    } catch (e: Exception) {
        logger.error("Application terminated with error", e)
        throw e
    }
}