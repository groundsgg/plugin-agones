plugins { id("gg.grounds.minestom-conventions") }

dependencies {
    api("gg.grounds:grounds-minestom-runtime-runtime-api:0.1.0")
    implementation(project(":common"))
    implementation("org.slf4j:slf4j-api:2.0.18")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
