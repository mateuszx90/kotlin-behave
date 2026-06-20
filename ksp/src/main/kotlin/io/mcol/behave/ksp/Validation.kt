package io.mcol.behave.ksp

/**
 * Pure decision logic for the diagnostics emitted by [BehaveProcessor]. Extracted from
 * the processor so it can be unit-tested without constructing KSP fixtures — the processor
 * is responsible for gathering inputs from `KSClassDeclaration`s; this object decides
 * whether (and what) to report.
 */
internal object Validation {

    /**
     * Result of checking a single step for cross-feature duplication.
     *
     * @param errorMessage non-null when the build should fail because the same step text
     *                     appears in 2+ feature files and is not covered by a `@StepsMixin`
     *                     or `@DivergentStep`. The message is the exact text to surface
     *                     to the user.
     */
    data class DivergenceCheck(val errorMessage: String?)

    fun checkDivergence(
        methodName: String,
        originalKeyword: String,
        originalText: String,
        inheritedMethodNames: Set<String>,
        sharers: Set<String>,
        thisClassName: String,
        hasDivergentAnnotation: Boolean,
    ): DivergenceCheck {
        if (methodName in inheritedMethodNames) return DivergenceCheck(null)
        if (sharers.size < 2) return DivergenceCheck(null)
        if (hasDivergentAnnotation) return DivergenceCheck(null)
        val others = (sharers - thisClassName).sorted()
        val othersBlock = others.joinToString("\n  - ", prefix = "  - ")
        val message = "Step '$methodName' (\"$originalKeyword $originalText\") " +
            "is declared in this feature and ${others.size} other:\n$othersBlock\n" +
            "Fix either by:\n" +
            "  - moving the shared body into a @StepsMixin interface (recommended when impls match), OR\n" +
            "  - adding @DivergentStep on the override in EVERY listed class to mark the divergence as intentional."
        return DivergenceCheck(message)
    }

    /**
     * Result of checking whether a new mixin method clashes with one already in the registry.
     *
     * @param errorMessage non-null when the same `(methodName, paramTypes)` is declared by
     *                     two different `@StepsMixin` interfaces.
     * @param shouldRegister true when the new entry should be added to the registry. Always
     *                     false on clash (we keep the first) and on identical re-declaration.
     */
    data class MixinClashCheck(val errorMessage: String?, val shouldRegister: Boolean)

    fun checkMixinClash(
        methodName: String,
        paramTypes: List<String>,
        newOwnerQualifiedName: String,
        priorOwnerQualifiedName: String?,
    ): MixinClashCheck {
        if (priorOwnerQualifiedName == null) return MixinClashCheck(null, shouldRegister = true)
        if (priorOwnerQualifiedName == newOwnerQualifiedName) {
            return MixinClashCheck(null, shouldRegister = false)
        }
        val message = "Step method '$methodName(${paramTypes.joinToString()})' is declared by multiple @StepsMixin interfaces:\n" +
            "  - $priorOwnerQualifiedName\n" +
            "  - $newOwnerQualifiedName\n" +
            "Each step method must be unique across all @StepsMixin interfaces. Either:\n" +
            "  - move the duplicated method into a single mixin, OR\n" +
            "  - rename one of them."
        return MixinClashCheck(message, shouldRegister = false)
    }
}
