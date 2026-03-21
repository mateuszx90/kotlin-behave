rootProject.name = "kotlin-behave"
include(":core")
include(":kotest")
include(":annotations")
include(":ksp")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
