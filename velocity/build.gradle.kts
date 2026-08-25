plugins { id("gg.grounds.velocity-conventions") }

dependencies {
    implementation(platform("gg.grounds:grounds-dependencies:0.1.0"))

    implementation(project(":common"))
    implementation("io.kubernetes:client-java")

    // Never shaded: the ProxyServiceRegistry we read from must be the very class plugin-proxy
    // loaded, or we would be reading a registry nobody writes to. 2.2.0 is the first version
    // carrying ServerDisplayQuery.
    compileOnly("gg.grounds:plugin-proxy-api:2.2.0")

    testImplementation("gg.grounds:plugin-proxy-api:2.2.0")
    testImplementation("com.velocitypowered:velocity-api")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
