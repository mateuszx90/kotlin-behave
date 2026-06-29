package io.mcol.behave.ksp

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.mcol.behave.gherkin.FeatureFileParser
import io.mcol.behave.gherkin.MethodNameGenerator
import java.io.File

private enum class Kind { PLACEHOLDER, QUOTED, VARIABLE, NUMBER }
private data class Tok(val pos: Int, val kind: Kind, val name: String)

/**
 * Key for matching a generated step method against a mixin method.
 * name + canonical parameter type names. Reading the docs: param types are simple names
 * (kotlin.* stripped). Suspend modifier is not part of the key — mixin authors are
 * expected to declare `suspend fun`.
 */
private data class MixinMethodKey(val name: String, val paramTypes: List<String>)

private data class MixinInfo(val qualifiedName: String, val simpleName: String)

private data class TypeConverterInfo(
    val functionName: String, // simple name
    val qualifiedName: String, // fully qualified name
    val returnType: String,
    val paramCount: Int, // number of parameters the converter function takes
)

private data class StepBuildContext(
    val typeMappings: List<io.mcol.behave.ksp.CodeGenerator.TypeMapping>,
    val templatesByNormKey: Map<String, List<FeatureFileParser.ParsedStep>>,
    val behaveCasts: Map<String, Set<Int>>,
    val typeConversions: Map<String, Map<Int, String>>,
    val classDecl: KSClassDeclaration,
    val typeConverters: Map<String, TypeConverterInfo>,
    val resolver: Resolver,
    val allStepInstances: List<FeatureFileParser.RawStep> = emptyList(), // every concrete step (Examples rows + standalone)
)

class BehaveProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>,
    private val projectDir: String,
) : SymbolProcessor {

    private var mixinRegistry: Map<MixinMethodKey, MixinInfo> = emptyMap()

    // Files that declare `@StepsMixin` interfaces. Every generated spec must declare a
    // dependency on these (and run in aggregating mode) so that adding, removing, or
    // editing a mixin invalidates the cached spec output.
    private var mixinSourceFiles: List<KSFile> = emptyList()

    // Generated method name -> set of qualified `*Steps` class names that declare that step.
    // Used to detect cross-feature duplication that the user hasn't covered with @StepsMixin.
    private var stepToFeatures: Map<String, Set<String>> = emptyMap()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val mixins = collectMixins(resolver)
        mixinRegistry = buildMixinRegistry(mixins)
        mixinSourceFiles = mixins.mapNotNull { it.containingFile }.distinct()

        val featureAnnotation = "io.mcol.behave.annotations.BehaveFeature"
        val symbols = resolver.getSymbolsWithAnnotation(featureAnnotation)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        stepToFeatures = buildStepToFeaturesMap(symbols)

        // Collect all @TypeConverter functions
        val typeConverters = collectTypeConverters(resolver)

        for (classDecl in symbols) {
            processClass(classDecl, resolver, typeConverters)
        }

        return emptyList()
    }

    /**
     * Pass 1 helper: parse every feature file once and record which generated method names
     * each `*Steps` class will need. Used by [validateDivergence] in pass 2.
     */
    private fun buildStepToFeaturesMap(featureSymbols: List<KSClassDeclaration>): Map<String, Set<String>> {
        val map = mutableMapOf<String, MutableSet<String>>()
        featureSymbols.mapNotNull(::extractFeatureSteps).forEach { (qName, methodNames) ->
            methodNames.forEach { name -> map.getOrPut(name) { mutableSetOf() }.add(qName) }
        }
        return map
    }

    private fun extractFeatureSteps(classDecl: KSClassDeclaration): Pair<String, Set<String>>? {
        val qName = classDecl.qualifiedName?.asString() ?: return null
        val featureFile = resolveFeatureFile(classDecl)?.takeIf { it.exists() } ?: return null
        val parsed = runCatching { FeatureFileParser.parse(featureFile.readText()) }
            .getOrNull()
            ?.takeUnless { it.hasErrors }
            ?: return null
        val stepPairs = parsed.steps.map { it.keyword to it.text }
        return qName to MethodNameGenerator.resolveCollisions(stepPairs).toSet()
    }

    private fun resolveFeatureFile(classDecl: KSClassDeclaration): File? {
        val annotation = classDecl.annotations.firstOrNull {
            it.shortName.asString() == "BehaveFeature"
        } ?: return null
        val path = annotation.arguments
            .firstOrNull { it.name?.asString() == "path" }
            ?.value as? String ?: return null
        val featureDir = options["behave.featureDir"] ?: "src/commonTest/resources"
        return File(projectDir, "$featureDir/$path")
    }

    /**
     * For every step in this feature that's NOT inherited from a mixin and IS declared by
     * another feature step file, require the override to carry `@DivergentStep`.
     * Otherwise emit an error pointing users to either extract a mixin or annotate explicitly.
     */
    private fun validateDivergence(
        generatedSteps: List<io.mcol.behave.ksp.CodeGenerator.GeneratedStep>,
        inheritedMethodNames: Set<String>,
        classDecl: KSClassDeclaration,
    ) {
        val classQName = classDecl.qualifiedName?.asString() ?: return
        val declaredFuncs = classDecl.getDeclaredFunctions().toList()
        generatedSteps.forEach { step ->
            validateStepDivergence(step, inheritedMethodNames, classQName, declaredFuncs, classDecl)
        }
    }

    private fun validateStepDivergence(
        step: io.mcol.behave.ksp.CodeGenerator.GeneratedStep,
        inheritedMethodNames: Set<String>,
        classQName: String,
        declaredFuncs: List<KSFunctionDeclaration>,
        classDecl: KSClassDeclaration,
    ) {
        val implMethod = declaredFuncs.firstOrNull { it.simpleName.asString() == step.methodName }
        val hasDivergent = implMethod?.annotations?.any {
            it.shortName.asString() == "DivergentStep"
        } ?: false
        val check = Validation.checkDivergence(
            methodName = step.methodName,
            originalKeyword = step.originalKeyword,
            originalText = step.originalText,
            inheritedMethodNames = inheritedMethodNames,
            sharers = stepToFeatures[step.methodName].orEmpty(),
            thisClassName = classQName,
            hasDivergentAnnotation = hasDivergent,
        )
        check.errorMessage?.let { logger.error(it, implMethod ?: classDecl) }
    }

    private fun collectMixins(resolver: Resolver): List<KSClassDeclaration> {
        val mixinAnnotation = "io.mcol.behave.annotations.StepsMixin"
        return resolver.getSymbolsWithAnnotation(mixinAnnotation)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.INTERFACE }
            .toList()
    }

    private fun collectTypeConverters(resolver: Resolver): Map<String, TypeConverterInfo> {
        // Map from target type FQN to converter function info
        val converters = mutableMapOf<String, TypeConverterInfo>()
        val converterAnnotation = "io.mcol.behave.annotations.TypeConverter"
        resolver.getSymbolsWithAnnotation(converterAnnotation)
            .filterIsInstance<KSFunctionDeclaration>()
            .forEach { func ->
                val returnType = func.returnType?.resolve()
                if (returnType != null) {
                    val returnTypeName = buildString {
                        val decl = returnType.declaration
                        val pkg = (decl as? KSClassDeclaration)?.packageName?.asString() ?: ""
                        if (pkg.isNotEmpty()) {
                            append(pkg)
                            append(".")
                        }
                        append(decl.simpleName.asString())
                    }
                    val funcName = func.simpleName.asString()
                    val funcPkg = func.packageName.asString()
                    val qualifiedName = if (funcPkg.isNotEmpty()) {
                        "$funcPkg.$funcName"
                    } else {
                        funcName
                    }
                    val paramCount = func.parameters.size
                    converters[returnTypeName] = TypeConverterInfo(
                        functionName = funcName,
                        qualifiedName = qualifiedName,
                        returnType = returnTypeName,
                        paramCount = paramCount,
                    )
                }
            }
        return converters
    }

    private fun matchStepsToMixins(
        generatedSteps: List<io.mcol.behave.ksp.CodeGenerator.GeneratedStep>,
    ): Pair<Map<String, String>, LinkedHashSet<String>> {
        val inheritedByMethod = mutableMapOf<String, String>()
        val mixinSimpleNames = linkedSetOf<String>()
        for (step in generatedSteps) {
            val paramTypes = getConvertedParamTypes(step)
            val key = MixinMethodKey(step.methodName, paramTypes)
            val info = mixinRegistry[key] ?: continue
            inheritedByMethod[step.methodName] = info.qualifiedName
            mixinSimpleNames.add(info.qualifiedName)
        }
        return inheritedByMethod to mixinSimpleNames
    }

    private fun getConvertedParamTypes(step: io.mcol.behave.ksp.CodeGenerator.GeneratedStep): List<String> {
        val consumedIndices = mutableSetOf<Int>()
        for ((idx, converter) in step.typeConverters) {
            if (converter.paramCount > 1) {
                for (i in 1 until converter.paramCount) {
                    consumedIndices.add(idx + i)
                }
            }
        }

        return step.params.mapIndexed { idx, param ->
            if (idx in consumedIndices) {
                null
            } else {
                step.typeConversions[idx]?.let { canonicalGeneratedParamType(it) }
                    ?: step.typeConverters[idx]?.returnType?.let { canonicalGeneratedParamType(it) }
                    ?: canonicalGeneratedParamType(param.typeName)
            }
        }.filterNotNull()
    }

    private fun buildMixinRegistry(mixins: List<KSClassDeclaration>): Map<MixinMethodKey, MixinInfo> {
        val registry = mutableMapOf<MixinMethodKey, MixinInfo>()
        for (mixin in mixins) {
            val qName = mixin.qualifiedName?.asString()
            if (qName == null) {
                logger.warn(
                    "@StepsMixin interface '${mixin.simpleName.asString()}' has no qualified name " +
                        "(likely in the default package) — skipped. Move it into a named package.",
                    mixin,
                )
                continue
            }
            val info = MixinInfo(qualifiedName = qName, simpleName = mixin.simpleName.asString())
            for (func in mixin.getDeclaredFunctions()) {
                if (func.isAbstract) continue // mixin entries must carry a default body to be useful
                val methodName = func.simpleName.asString()
                val paramTypes = func.parameters.map { canonicalTypeName(it.type.resolve()) }
                val key = MixinMethodKey(methodName, paramTypes)
                val prior = registry[key]
                val check = Validation.checkMixinClash(
                    methodName = methodName,
                    paramTypes = paramTypes,
                    newOwnerQualifiedName = qName,
                    priorOwnerQualifiedName = prior?.qualifiedName,
                )
                check.errorMessage?.let { logger.error(it, mixin) }
                if (check.shouldRegister) registry[key] = info
            }
        }
        return registry
    }

    /** Reduce KSP type names to the same form `CodeGenerator` uses on the generated side. */
    private fun canonicalTypeName(type: KSType): String {
        val raw = type.declaration.qualifiedName?.asString()
            ?: type.declaration.simpleName.asString()
        val stripped = raw.removePrefix("kotlin.").removePrefix("kotlin.collections.")
        val baseShort = if (stripped.startsWith("kotlin.")) stripped.substringAfterLast('.') else stripped
        if (type.arguments.isEmpty()) return baseShort
        val args = type.arguments.joinToString(", ") {
            it.type?.resolve()?.let { t -> canonicalTypeName(t) } ?: "*"
        }
        return "$baseShort<$args>"
    }

    private fun canonicalGeneratedParamType(typeName: String): String = typeName.removePrefix("kotlin.").removePrefix("kotlin.collections.")

    private fun processClass(classDecl: KSClassDeclaration, resolver: Resolver, typeConverters: Map<String, TypeConverterInfo>) {
        val className = classDecl.simpleName.asString()
        val packageName = classDecl.packageName.asString()

        // Read @BehaveFeature path
        val featureAnnotation = classDecl.annotations.first {
            it.shortName.asString() == "BehaveFeature"
        }
        val featurePath = featureAnnotation.arguments
            .first { it.name?.asString() == "path" }
            .value as String
        val generateTest = featureAnnotation.arguments
            .firstOrNull { it.name?.asString() == "generateTest" }
            ?.value as? Boolean ?: true

        // Read @Retry(times) — extra attempts for a failing scenario; absent → 0 (disabled).
        val retries = classDecl.annotations
            .firstOrNull { it.shortName.asString() == "Retry" }
            ?.arguments
            ?.firstOrNull { it.name?.asString() == "times" }
            ?.value as? Int ?: 0

        // Check which lifecycle interfaces the class implements
        val superTypeNames = classDecl.superTypes.map { ref ->
            ref.resolve().declaration.qualifiedName?.asString()
        }.toSet()
        val hasScenarioRunner = "io.mcol.behave.steps.ScenarioRunner" in superTypeNames
        val hasBeforeScenario = "io.mcol.behave.steps.BeforeScenario" in superTypeNames ||
            "io.mcol.behave.steps.ScenarioHooks" in superTypeNames
        val hasAfterScenario = "io.mcol.behave.steps.AfterScenario" in superTypeNames ||
            "io.mcol.behave.steps.ScenarioHooks" in superTypeNames

        // Resolve feature file
        val featureDir = options["behave.featureDir"] ?: "src/commonTest/resources"
        val featureFile = File(projectDir, "$featureDir/$featurePath")
        if (!featureFile.exists()) {
            logger.error(
                "Feature file not found: ${featureFile.absolutePath}",
                classDecl,
            )
            return
        }

        // Read @BehaveType annotations
        val behaveTypes = classDecl.annotations
            .filter { it.shortName.asString() == "BehaveType" }
            .map { ann ->
                val placeholder = ann.arguments.firstOrNull { it.name?.asString() == "placeholder" }?.value as? String ?: ""
                val type = ann.arguments.firstOrNull { it.name?.asString() == "type" }?.value as? KSType
                val fields = (ann.arguments.firstOrNull { it.name?.asString() == "fields" }?.value as? List<*>)
                    ?.mapNotNull { it as? String } ?: emptyList()
                Triple(placeholder, type, fields)
            }.toList()

        // Validate @BehaveType entries
        for ((placeholder, type, fields) in behaveTypes) {
            if (type == null) continue
            val typeDecl = type.declaration as? KSClassDeclaration ?: continue
            val constructorParams = typeDecl.primaryConstructor?.parameters ?: emptyList()
            if (placeholder.isEmpty() && fields.isEmpty() && constructorParams.isEmpty()) {
                logger.error(
                    "@BehaveType auto-detect on type '${typeDecl.simpleName.asString()}' which has zero primary constructor parameters",
                    classDecl,
                )
            }
        }

        // Read @BehaveCast from implementing class methods
        val behaveCasts = mutableMapOf<String, Set<Int>>() // methodName -> set of param indices with @BehaveCast
        for (func in classDecl.getDeclaredFunctions()) {
            val funcName = func.simpleName.asString()
            val castIndices = func.parameters.mapIndexedNotNull { idx, param ->
                val hasCast = param.annotations.any { it.shortName.asString() == "BehaveCast" }
                if (hasCast) idx else null
            }.toSet()
            if (castIndices.isNotEmpty()) {
                behaveCasts[funcName] = castIndices
            }
        }

        // Read @Type from implementing class methods
        val typeConversions = resolveTypeConversions(classDecl)

        // Parse feature file
        val parsed = FeatureFileParser.parse(featureFile.readText())
        for (error in parsed.errors) {
            logger.error("${featureFile.toURI()}:${error.line}: ${error.message}", classDecl)
        }
        if (parsed.hasErrors) return

        // Resolve type mappings
        val typeMappings = resolveTypeMappings(behaveTypes, resolver)

        // Generate method names with collision resolution
        val stepPairs = parsed.steps.map { it.keyword to it.text }
        val methodNames = MethodNameGenerator.resolveCollisions(stepPairs)

        // Build a map from normalised key to all pre-dedup template texts (for type unification)
        val templatesByNormKey = parsed.allStepTemplates.groupBy {
            FeatureFileParser.normalise(it.keyword, it.text)
        }

        // Build GeneratedStep list
        val buildContext = StepBuildContext(
            typeMappings = typeMappings,
            templatesByNormKey = templatesByNormKey,
            behaveCasts = behaveCasts,
            typeConversions = typeConversions,
            classDecl = classDecl,
            typeConverters = typeConverters,
            resolver = resolver,
            allStepInstances = parsed.allStepInstances,
        )
        val generatedSteps = parsed.steps.mapIndexed { i, step ->
            buildGeneratedStep(step, methodNames[i], buildContext)
        }

        // Validate concrete values against declared placeholder types (and enum constants)
        val enumConstants = collectEnumConstants(classDecl)
        validateTypes(generatedSteps, parsed.allStepInstances, classDecl, behaveCasts, enumConstants)

        // A step seen both with and without a | table | generates one binding shape; the other
        // usage fails at runtime (params.dataTable!! NPE, or a dropped table).
        dataTableConflictErrors(templatesByNormKey).forEach { logger.error(it, classDecl) }

        val rowClasses = buildRowClasses(generatedSteps, parsed.steps)

        // Match each generated step against the mixin registry. A step whose
        // (methodName, paramTypes) matches a @StepsMixin method becomes "inherited" —
        // omitted from the spec's abstract methods, with the spec extending that mixin.
        val (inheritedByMethod, mixinSimpleNames) = matchStepsToMixins(generatedSteps)

        // Detect cross-feature step duplication that's NOT covered by a mixin and NOT
        // explicitly marked @DivergentStep. Emits errors that fail the build.
        validateDivergence(generatedSteps, inheritedByMethod.keys, classDecl)

        val interfaceName = "${className}Spec"
        val iface = io.mcol.behave.ksp.CodeGenerator.GeneratedInterface(
            packageName = packageName,
            interfaceName = interfaceName,
            implementingClassName = className,
            featurePath = featurePath,
            generateTest = generateTest,
            retries = retries,
            hasScenarioRunner = hasScenarioRunner,
            hasBeforeScenario = hasBeforeScenario,
            hasAfterScenario = hasAfterScenario,
            steps = generatedSteps,
            rowClasses = rowClasses,
            inheritedMixins = mixinSimpleNames.toList(),
            inheritedMethodNames = inheritedByMethod.keys.toSet(),
        )

        val source = io.mcol.behave.ksp.CodeGenerator.render(iface)

        // Track every source that can change what the generated spec looks like:
        //  - the @BehaveFeature class file (always)
        //  - every @StepsMixin file in the compilation (any change affects what's inherited)
        // aggregating = true so that adding a NEW mixin file later also invalidates this output.
        val depSources = (listOfNotNull(classDecl.containingFile) + mixinSourceFiles).distinct()
        val outputFile = codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = true, sources = depSources.toTypedArray()),
            packageName = packageName,
            fileName = interfaceName,
        )
        outputFile.writer().use { it.write(source) }
    }

    private fun buildGeneratedStep(
        step: FeatureFileParser.ParsedStep,
        methodName: String,
        ctx: StepBuildContext,
    ): io.mcol.behave.ksp.CodeGenerator.GeneratedStep {
        // Infer a unified Kotlin type for each outline <variable>, considering EVERY concrete
        // instance of this step (Examples rows + standalone literals). All values must agree on a
        // non-String type, otherwise the variable stays String.
        val varTypes = inferVariableTypes(step, ctx.allStepInstances)
        val params = resolveParams(step, ctx.typeMappings, ctx.classDecl, varTypes)
        var rawExpr = io.mcol.behave.ksp.CodeGenerator.escapeStepExpression(
            io.mcol.behave.ksp.CodeGenerator.replaceOutlineVariablesTyped(
                io.mcol.behave.ksp.CodeGenerator.replaceNumberLiterals(
                    io.mcol.behave.ksp.CodeGenerator.replaceQuotedLiterals(step.text),
                ),
                varTypes,
            ),
        )
        if (rawExpr.contains("{word}")) {
            val normKey = FeatureFileParser.normalise(step.keyword, step.text)
            val siblings = ctx.templatesByNormKey[normKey] ?: emptyList()
            if (siblings.any { sibling -> Regex("\"[^\"]*\"").containsMatchIn(sibling.text) }) {
                rawExpr = rawExpr.replace("{word}", "{string}")
            }
        }
        val castIndices = ctx.behaveCasts[methodName] ?: emptySet()
        val castParams = mutableMapOf<Int, String>()
        for (idx in castIndices) {
            if (idx < params.size) castParams[idx] = params[idx].typeName
        }
        val conversions = ctx.typeConversions[methodName] ?: emptyMap()

        // Separate enum conversions from custom type conversions
        val enumConversions = mutableMapOf<Int, String>()
        val customConverters = mutableMapOf<Int, io.mcol.behave.ksp.CodeGenerator.ConverterInfo>()
        for ((idx, typeName) in conversions) {
            val converterInfo = ctx.typeConverters[typeName]
            if (converterInfo != null) {
                customConverters[idx] = io.mcol.behave.ksp.CodeGenerator.ConverterInfo(
                    functionName = converterInfo.functionName,
                    qualifiedName = converterInfo.qualifiedName,
                    returnType = converterInfo.returnType,
                    paramCount = converterInfo.paramCount,
                )
            } else {
                // No custom converter found, assume it's an enum that uses valueOf()
                enumConversions[idx] = typeName
            }
        }

        return io.mcol.behave.ksp.CodeGenerator.GeneratedStep(
            methodName = methodName,
            params = params,
            stepExpression = rawExpr,
            originalKeyword = step.keyword,
            originalText = step.text,
            castParams = castParams,
            typeConversions = enumConversions,
            typeConverters = customConverters,
            hasDocString = step.hasDocString,
        )
    }

    private fun buildRowClasses(
        generatedSteps: List<io.mcol.behave.ksp.CodeGenerator.GeneratedStep>,
        parsedSteps: List<FeatureFileParser.ParsedStep>,
    ): List<io.mcol.behave.ksp.CodeGenerator.GeneratedRowClass> = generatedSteps
        .filter { step ->
            step.params.any { param ->
                val innerType = param.typeName.removePrefix("List<").removeSuffix(">")
                param.typeName.startsWith("List<") && innerType.endsWith("Row")
            }
        }
        .map { step ->
            val listParam = step.params.first { it.typeName.startsWith("List<") }
            val rowClassName = listParam.typeName.removePrefix("List<").removeSuffix(">")
            val originalStep = parsedSteps.first { it.keyword == step.originalKeyword && it.text == step.originalText }
            val rowParams = originalStep.tableColumns.map { col ->
                io.mcol.behave.ksp.CodeGenerator.StepParam(name = col, typeName = "String")
            }
            io.mcol.behave.ksp.CodeGenerator.GeneratedRowClass(
                name = rowClassName,
                params = rowParams,
                shouldEmit = !rowClassName.contains("."),
            )
        }

    private fun resolveTypeConversions(
        classDecl: KSClassDeclaration,
    ): Map<String, Map<Int, String>> {
        val typeConversions = mutableMapOf<String, Map<Int, String>>()

        // Check direct class methods
        for (func in classDecl.getDeclaredFunctions()) {
            val conversions = extractTypeConversions(func)
            if (conversions.isNotEmpty()) {
                typeConversions[func.simpleName.asString()] = conversions
            }
        }

        // Also check @StepsMixin interfaces
        for (superType in classDecl.superTypes) {
            val superTypeDecl = superType.resolve().declaration as? KSClassDeclaration ?: continue
            if (superTypeDecl.classKind != ClassKind.INTERFACE) continue
            val isMixin = superTypeDecl.annotations.any { it.shortName.asString() == "StepsMixin" }
            if (!isMixin) continue

            for (func in superTypeDecl.getDeclaredFunctions()) {
                val conversions = extractTypeConversions(func)
                if (conversions.isNotEmpty()) {
                    typeConversions[func.simpleName.asString()] = conversions
                }
            }
        }

        return typeConversions
    }

    private fun extractTypeConversions(func: KSFunctionDeclaration): Map<Int, String> {
        val conversions = mutableMapOf<Int, String>()
        for ((idx, param) in func.parameters.withIndex()) {
            val typeAnnotation = param.annotations.firstOrNull { it.shortName.asString() == "Type" }
            if (typeAnnotation != null) {
                val typeArg = typeAnnotation.arguments.firstOrNull { it.name?.asString() == "type" }
                if (typeArg != null) {
                    val typeValue = typeArg.value
                    if (typeValue is KSType) {
                        val typeName = buildString {
                            val decl = typeValue.declaration
                            val pkg = (decl as? KSClassDeclaration)?.packageName?.asString() ?: ""
                            if (pkg.isNotEmpty()) {
                                append(pkg)
                                append(".")
                            }
                            append(decl.simpleName.asString())
                        }
                        conversions[idx] = typeName
                    }
                }
            }
        }
        return conversions
    }

    private fun resolveTypeMappings(
        behaveTypes: List<Triple<String, KSType?, List<String>>>,
        resolver: Resolver,
    ): List<io.mcol.behave.ksp.CodeGenerator.TypeMapping> {
        return behaveTypes.mapNotNull { (placeholder, type, fields) ->
            val typeDecl = type?.declaration as? KSClassDeclaration ?: return@mapNotNull null
            val typeName = buildString {
                val pkg = typeDecl.packageName.asString()
                if (pkg.isNotEmpty()) {
                    append(pkg)
                    append(".")
                }
                append(typeDecl.simpleName.asString())
            }
            val constructorParams = typeDecl.primaryConstructor?.parameters
                ?.map { it.name?.asString() ?: "" } ?: emptyList()
            val resolvedFields = if (fields.isNotEmpty()) fields else constructorParams
            io.mcol.behave.ksp.CodeGenerator.TypeMapping(
                placeholder = placeholder,
                typeName = typeName,
                fields = resolvedFields,
            )
        }
    }

    /**
     * Infer a unified Kotlin type for each outline `<variable>` in [step], scanning EVERY concrete
     * instance in [allRawSteps] (Examples-table rows AND standalone literal steps that share this
     * step's shape). Delegates to the shared [io.mcol.behave.gherkin.GherkinTypes] so the IntelliJ
     * plugin computes identical types.
     */
    private fun inferVariableTypes(
        step: FeatureFileParser.ParsedStep,
        allRawSteps: List<FeatureFileParser.RawStep>,
    ): Map<String, String> = io.mcol.behave.gherkin.GherkinTypes.inferVariableTypes(
        templateText = step.text,
        instanceTexts = allRawSteps.map { it.text },
    )

    private fun resolveParams(
        step: FeatureFileParser.ParsedStep,
        typeMappings: List<io.mcol.behave.ksp.CodeGenerator.TypeMapping>,
        classDecl: KSClassDeclaration,
        varTypes: Map<String, String> = emptyMap(),
    ): List<io.mcol.behave.ksp.CodeGenerator.StepParam> {
        // Always extract inline params (quoted literals, {placeholders}, <variables>)
        val inlineParams = resolveInlineParams(step.text, typeMappings, varTypes)
        if (!step.hasDataTable) return inlineParams
        // DataTable steps: prepend any inline string params before the rows param
        return inlineParams + resolveDataTableParams(step, typeMappings)
    }

    private fun resolveInlineParams(
        text: String,
        typeMappings: List<io.mcol.behave.ksp.CodeGenerator.TypeMapping>,
        varTypes: Map<String, String> = emptyMap(),
    ): List<io.mcol.behave.ksp.CodeGenerator.StepParam> {
        // Find quoted string ranges first so we can exclude <variable> found inside "..."
        val quotedRanges = Regex("\"[^\"]*\"").findAll(text).map { it.range }.toList()
        fun inQuotes(pos: Int) = quotedRanges.any { pos in it }

        // Collect all dynamic tokens in left-to-right order

        val toks = buildList {
            Regex("\\{([^}]+)}").findAll(text).forEach {
                add(Tok(it.range.first, Kind.PLACEHOLDER, it.groupValues[1]))
            }
            // "..." — if content is exactly <variable>, use the variable name; else use "string"
            Regex("\"([^\"]*)\"").findAll(text).forEach {
                val inner = it.groupValues[1]
                val varName = Regex("^<([^>]+)>$").find(inner)?.groupValues?.get(1)
                add(Tok(it.range.first, Kind.QUOTED, varName ?: "string"))
            }
            // <variable> tokens NOT inside "..."
            Regex("<([^>]+)>").findAll(text).filter { !inQuotes(it.range.first) }.forEach {
                add(Tok(it.range.first, Kind.VARIABLE, it.groupValues[1]))
            }
            // Standalone number literals (not inside quotes or placeholders)
            // Doubles first, then integers — same order as CodeGenerator.replaceNumberLiterals
            val coveredRanges = map { it.pos..(it.pos) } // approximate; refine below
            Regex("""(?<!\S)-?\d+\.\d+(?!\S)""").findAll(text)
                .filter { m -> !inQuotes(m.range.first) && none { tok -> m.range.first in (tok.pos..tok.pos) } }
                .forEach { m -> add(Tok(m.range.first, Kind.NUMBER, "double")) }
            Regex("""(?<!\S)-?\d+(?!\S)""").findAll(text)
                .filter { m ->
                    !inQuotes(m.range.first) &&
                        none { tok -> m.range.first in (tok.pos..tok.pos) && tok.kind == Kind.NUMBER }
                }
                .forEach { m -> add(Tok(m.range.first, Kind.NUMBER, "int")) }
        }.sortedBy { it.pos }

        val placeholderNames = toks.filter { it.kind == Kind.PLACEHOLDER }.map { it.name }

        // Apply field-based type mappings first (group multiple {placeholders} into one typed param)
        val params = mutableListOf<io.mcol.behave.ksp.CodeGenerator.StepParam>()
        val usedPlaceholders = mutableSetOf<String>()
        for (mapping in typeMappings.filter { it.fields.isNotEmpty() }) {
            if (mapping.fields.all { it in placeholderNames }) {
                params.add(
                    io.mcol.behave.ksp.CodeGenerator.StepParam(
                        name = mapping.typeName.substringAfterLast('.').replaceFirstChar { it.lowercase() },
                        typeName = mapping.typeName,
                    ),
                )
                usedPlaceholders.addAll(mapping.fields)
            }
        }

        // Process remaining tokens left-to-right
        val placeholderMappings = typeMappings.filter { it.placeholder.isNotEmpty() }
        val nameCounters = mutableMapOf<String, Int>()

        for (tok in toks) {
            if (tok.kind == Kind.PLACEHOLDER && tok.name in usedPlaceholders) continue

            val (typeName, baseName) = when (tok.kind) {
                Kind.PLACEHOLDER -> {
                    val custom = placeholderMappings.firstOrNull { it.placeholder == tok.name }
                    when {
                        custom != null -> custom.typeName to tok.name
                        tok.name in io.mcol.behave.ksp.CodeGenerator.builtinTypes ->
                            io.mcol.behave.ksp.CodeGenerator.builtinTypes[tok.name]!! to tok.name
                        else -> "String" to tok.name
                    }
                }
                Kind.QUOTED -> "String" to tok.name // tok.name is variable name or "string"
                Kind.VARIABLE -> {
                    // Use the unified inferred type for this variable, otherwise default to String
                    val inferredType = varTypes[tok.name] ?: "String"
                    inferredType to tok.name
                }
                Kind.NUMBER -> {
                    val kotlinType = io.mcol.behave.ksp.CodeGenerator.builtinTypes[tok.name] ?: "Int"
                    kotlinType to tok.name
                }
            }

            val idx = nameCounters.getOrDefault(baseName, 0)
            val totalWithBase = toks.count { it.name == baseName }
            val paramName = if (idx == 0 && totalWithBase == 1) baseName else "$baseName$idx"
            nameCounters[baseName] = idx + 1
            params.add(io.mcol.behave.ksp.CodeGenerator.StepParam(paramName, typeName))
        }

        return params
    }

    private fun validateTypes(
        generatedSteps: List<io.mcol.behave.ksp.CodeGenerator.GeneratedStep>,
        allRawSteps: List<FeatureFileParser.RawStep>,
        classDecl: KSClassDeclaration,
        behaveCasts: Map<String, Set<Int>> = emptyMap(),
        enumConstants: Map<String, Set<String>> = emptyMap(),
    ) {
        if (allRawSteps.isEmpty()) return

        for (genStep in generatedSteps) {
            val placeholderTypes = TypeValidator.extractPlaceholderTypes(genStep.stepExpression)
            // Enum conversions ride on {string} placeholders, so a step may have enum params even
            // when no built-in {int}/{double}/… placeholder is present — don't skip on that alone.
            if (placeholderTypes.isEmpty() && genStep.typeConversions.isEmpty()) continue

            val castIndices = behaveCasts[genStep.methodName] ?: emptySet()

            val normalisedGen = FeatureFileParser.normalise(genStep.originalKeyword, genStep.originalText)
            val matchingRaw = allRawSteps.filter { raw ->
                FeatureFileParser.normalise(raw.keyword, raw.text) == normalisedGen
            }

            for (raw in matchingRaw) {
                val concreteValues = TypeValidator.extractConcreteValues(raw.text, genStep.originalText)
                for ((i, value) in concreteValues.withIndex()) {
                    if (i in castIndices) continue // @BehaveCast suppresses validation
                    val enumType = genStep.typeConversions[i]
                    val enumConsts = enumType?.let { enumConstants[it] }
                    when {
                        // Enum params: the generated code calls Enum.valueOf(value.uppercase()),
                        // which throws at runtime for an unknown constant. Reject it at compile time.
                        enumConsts != null ->
                            enumMismatchError(value, enumType, enumConsts, raw)?.let { logger.error(it, classDecl) }
                        i < placeholderTypes.size ->
                            placeholderMismatchError(value, placeholderTypes[i], raw)?.let { logger.error(it, classDecl) }
                    }
                }
            }
        }
    }

    private fun resolveDataTableParams(
        step: FeatureFileParser.ParsedStep,
        typeMappings: List<io.mcol.behave.ksp.CodeGenerator.TypeMapping>,
    ): List<io.mcol.behave.ksp.CodeGenerator.StepParam> {
        val columns = step.tableColumns
        if (columns.isEmpty()) {
            return listOf(io.mcol.behave.ksp.CodeGenerator.StepParam("rows", "List<io.mcol.behave.model.DataTableRow>"))
        }

        // Check if a single type covers all columns
        val singleMatch = typeMappings.firstOrNull { mapping ->
            when {
                mapping.fields.isNotEmpty() -> mapping.fields.toSet() == columns.toSet()
                mapping.placeholder.isEmpty() -> columns.all { col ->
                    mapping.fields.isEmpty() || col in mapping.fields
                }
                else -> false
            }
        }

        if (singleMatch != null && typeMappings.size == 1) {
            return listOf(io.mcol.behave.ksp.CodeGenerator.StepParam("rows", "List<${singleMatch.typeName}>"))
        }

        // Multiple groups → generate a Row class
        val methodName = MethodNameGenerator.generate(step.keyword, step.text)
        val rowClassName = methodName.replaceFirstChar { it.uppercase() } + "Row"
        return listOf(io.mcol.behave.ksp.CodeGenerator.StepParam("rows", "List<$rowClassName>"))
    }
}

/**
 * For every `@Type(SomeEnum::class)` parameter on [classDecl] (and its `@StepsMixin`
 * supertypes) whose target is an enum, collect that enum's constant names keyed by the same
 * qualified type name `extractTypeConversions` produces. Used to reject feature literals that
 * name no real constant — which would otherwise blow up at runtime inside `valueOf(...)`.
 */
private fun collectEnumConstants(classDecl: KSClassDeclaration): Map<String, Set<String>> {
    val result = mutableMapOf<String, Set<String>>()
    fun scan(func: KSFunctionDeclaration) {
        func.parameters.forEach { param ->
            val decl = param.annotations
                .firstOrNull { it.shortName.asString() == "Type" }
                ?.arguments?.firstOrNull { it.name?.asString() == "type" }?.value
                ?.let { it as? KSType }?.declaration as? KSClassDeclaration
            if (decl != null && decl.classKind == ClassKind.ENUM_CLASS) {
                val pkg = decl.packageName.asString()
                val typeName = if (pkg.isNotEmpty()) "$pkg.${decl.simpleName.asString()}" else decl.simpleName.asString()
                result[typeName] = decl.declarations
                    .filterIsInstance<KSClassDeclaration>()
                    .filter { it.classKind == ClassKind.ENUM_ENTRY }
                    .map { it.simpleName.asString() }
                    .toSet()
            }
        }
    }
    classDecl.getDeclaredFunctions().forEach(::scan)
    classDecl.superTypes
        .mapNotNull { it.resolve().declaration as? KSClassDeclaration }
        .filter { it.classKind == ClassKind.INTERFACE && it.annotations.any { a -> a.shortName.asString() == "StepsMixin" } }
        .forEach { it.getDeclaredFunctions().forEach(::scan) }
    return result
}

/**
 * Detects a step whose DataTable usage is inconsistent across the feature: the same step text
 * appears both with and without a `| table |`. The generated binding has a single shape, so the
 * other usage fails at runtime — `params.dataTable!!` NPEs when the table is absent, or an
 * unexpected table is silently dropped. Returns one diagnostic per conflicting step.
 */
private fun dataTableConflictErrors(
    templatesByNormKey: Map<String, List<FeatureFileParser.ParsedStep>>,
): List<String> = templatesByNormKey.values.mapNotNull { group ->
    if (group.map { it.hasDataTable }.distinct().size < 2) {
        null
    } else {
        val sample = group.first()
        "Inconsistent DataTable usage for step '${sample.keyword} ${sample.text}': it appears both " +
            "with and without a | table |. The generated binding reads params.dataTable!! and will fail " +
            "at runtime where the table is missing. Make the table present (or absent) in every scenario " +
            "that uses this step."
    }
}

/** Diagnostic for an enum literal that names no real constant; null when [value] is valid. */
private fun enumMismatchError(
    value: String,
    enumType: String,
    constants: Set<String>,
    raw: FeatureFileParser.RawStep,
): String? {
    if (value.uppercase() in constants) return null
    return "Invalid enum value in scenario '${raw.scenarioName}': '$value' is not a constant of " +
        "${enumType.substringAfterLast('.')} (expected one of ${constants.sorted().joinToString()}) " +
        "in step '${raw.keyword} ${raw.text}'"
}

/** Diagnostic for a concrete value that doesn't match its built-in placeholder type; null when valid. */
private fun placeholderMismatchError(
    value: String,
    expectedType: String,
    raw: FeatureFileParser.RawStep,
): String? {
    val pattern = TypeValidator.typeValidationPatterns[expectedType] ?: return null
    if (!pattern.matches(value)) {
        return "Type mismatch in scenario '${raw.scenarioName}': value '$value' does not match " +
            "{$expectedType} in step '${raw.keyword} ${raw.text}'"
    }
    // The regex accepts any run of digits, but the generated code parses {int}/{long} via
    // toInt()/toLong(), which overflow at runtime for values outside the Kotlin type's range.
    val overflowType = when (expectedType) {
        "int" -> if (value.toIntOrNull() == null) "Int" else null
        "long" -> if (value.toLongOrNull() == null) "Long" else null
        else -> null
    } ?: return null
    return "Numeric overflow in scenario '${raw.scenarioName}': value '$value' does not fit in " +
        "$overflowType in step '${raw.keyword} ${raw.text}'"
}
