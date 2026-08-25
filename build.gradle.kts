plugins { id("gg.grounds.base-conventions") version "0.8.0" }

allprojects {
    repositories {
        mavenLocal()
        maven {
            url = uri("https://maven.pkg.github.com/groundsgg/*")
            credentials {
                username = providers.gradleProperty("github.user").get()
                password = providers.gradleProperty("github.token").get()
            }
        }
    }
}
