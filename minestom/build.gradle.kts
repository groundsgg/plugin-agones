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
    implementation(project(":common"))
    implementation("org.slf4j:slf4j-api")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
