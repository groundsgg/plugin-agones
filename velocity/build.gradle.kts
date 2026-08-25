plugins { id("gg.grounds.velocity-conventions") }

dependencies {
    implementation(platform("gg.grounds:grounds-dependencies:0.1.0"))

    implementation(project(":common"))
    implementation("io.kubernetes:client-java")

    // Never shaded: the ProxyServiceRegistry we read from must be the very class plugin-proxy
    // loaded, or we would be reading a registry nobody writes to. local-SNAPSHOT is the first
    // version carrying ServerDisplayQuery (Maven Local until GitHub Packages publishes it).
    compileOnly("gg.grounds:plugin-proxy-api:local-SNAPSHOT")

    testImplementation("gg.grounds:plugin-proxy-api:local-SNAPSHOT")
    testImplementation("com.velocitypowered:velocity-api")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
