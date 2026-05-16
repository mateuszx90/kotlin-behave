plugins {
    `maven-publish`
}

publishing {
    publications.withType<MavenPublication> {
        pom {
            description = "BDD testing framework for Kotlin Multiplatform"
            url = "https://github.com/onecodeman/kotlin-behave"
            licenses {
                license {
                    name = "Apache License, Version 2.0"
                    url = "https://www.apache.org/licenses/LICENSE-2.0"
                    distribution = "repo"
                }
            }
            developers {
                developer {
                    id = "onecodeman"
                    name = "Mateusz Maciołek"
                    email = "mateuszx90@gmail.com"
                }
            }
            scm {
                url = "https://github.com/onecodeman/kotlin-behave"
                connection = "scm:git:git://github.com/onecodeman/kotlin-behave.git"
                developerConnection = "scm:git:ssh://github.com/onecodeman/kotlin-behave.git"
            }
        }
    }
}
