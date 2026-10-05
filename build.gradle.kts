plugins { id("gg.grounds.base-conventions") version "0.8.0" }

allprojects {
    repositories {
        // Minestom for Minecraft 26.3 comes from our fork until upstream releases it.
        maven {
            url = uri("https://maven.pkg.github.com/groundsgg/minestom")
            credentials {
                username = providers.gradleProperty("github.user").get()
                password = providers.gradleProperty("github.token").get()
            }
            content { includeGroup("net.minestom") }
        }
        maven {
            url = uri("https://maven.pkg.github.com/groundsgg/*")
            credentials {
                username = providers.gradleProperty("github.user").get()
                password = providers.gradleProperty("github.token").get()
            }
        }
    }
}
