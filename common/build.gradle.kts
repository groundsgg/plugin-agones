plugins { id("gg.grounds.kotlin-conventions") }

dependencies {
    api(platform("gg.grounds:grounds-dependencies:0.1.0"))

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    api("com.google.code.gson:gson")
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
