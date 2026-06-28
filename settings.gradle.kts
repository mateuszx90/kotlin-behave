rootProject.name = "kotlin-behave"
include(":gherkin")
include(":core")
include(":kotest")
include(":annotations")
include(":ksp")
include(":gradle-plugin")
include(":examples")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
