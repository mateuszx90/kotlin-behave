package io.mcol.behave.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class BehaveProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = BehaveProcessor(
        codeGenerator = environment.codeGenerator,
        logger = environment.logger,
        options = environment.options,
        projectDir = environment.options["behave.projectDir"] ?: ".",
    )
}
