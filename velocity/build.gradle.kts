plugins { id("gg.grounds.velocity-conventions") }

dependencies {
    implementation(project(":common"))
    implementation("io.kubernetes:client-java:26.0.0")
}
