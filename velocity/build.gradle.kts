plugins { id("gg.grounds.velocity-conventions") }

dependencies {
    implementation(platform("gg.grounds:grounds-dependencies:0.1.0"))

    implementation(project(":common"))
    implementation("gg.grounds:library-i18n:0.4.4")
    implementation("io.kubernetes:client-java")

    // Never shaded: the ProxyServiceRegistry we read from must be the very class plugin-proxy
    // loaded, or we would be reading a registry nobody writes to. 2.2.0 is the first version
    // carrying ServerDisplayQuery.
    compileOnly("gg.grounds:plugin-proxy-api:2.8.0")

    testImplementation("gg.grounds:plugin-proxy-api:2.8.0")
    testImplementation("com.velocitypowered:velocity-api")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Keep the publishing artifact version with its catalog when bundled into a host JAR.
tasks.named<org.gradle.language.jvm.tasks.ProcessResources>("processResources") {
    inputs.property("permissionManifestVersion", project.version.toString())
    filesMatching("META-INF/grounds/permissions/*.json") {
        expand("version" to project.version.toString())
    }
}
