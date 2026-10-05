plugins {
    id("gg.grounds.minestom-conventions")
    id("com.github.gmazzo.buildconfig")
}

buildConfig {
    className("BuildInfo")
    packageName("gg.grounds")
    useKotlinOutput()
    buildConfigField("String", "VERSION", "\"${project.version}\"")
}

dependencies {
    implementation(platform("gg.grounds:grounds-dependencies:0.1.0"))

    api("gg.grounds:grounds-minestom-runtime-runtime-api:0.1.0")
    // Minecraft 26.3, from the groundsgg fork. Overrides the older Minestom the conventions
    // and the platform bring: 26.3 changed getOnlinePlayers() to return a Set, so a module
    // compiled against 26.2 throws NoSuchMethodError on a 26.3 server.
    implementation("net.minestom:minestom:2026.10.02-26.3-grounds.1")
    implementation(project(":common"))
    implementation("org.slf4j:slf4j-api")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
