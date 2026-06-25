plugins { id("gg.grounds.velocity-conventions") }

dependencies {
    implementation(platform("gg.grounds:grounds-dependencies:0.1.0"))

    implementation(project(":common"))
    implementation("io.kubernetes:client-java")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
