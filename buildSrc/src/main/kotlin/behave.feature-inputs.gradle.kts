// Declares the feature files directory as a KSP task input so Gradle re-runs
// KSP automatically when any .feature file changes.
tasks.matching { it.name.startsWith("ksp") }.configureEach {
    val featureDir = (
        extensions
            .findByName("ksp")
            ?.let { ext ->
                ext.javaClass.methods
                    .firstOrNull { it.name == "getArguments" }
                    ?.invoke(ext)
                    ?.let {
                        @Suppress("UNCHECKED_CAST")
                        (it as? Map<String, String>)?.get("behave.featureDir")
                    }
            } ?: "src/test/resources"
    )
    inputs.dir(layout.projectDirectory.dir(featureDir))
}
