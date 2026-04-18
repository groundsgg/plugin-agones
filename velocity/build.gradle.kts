plugins { id("gg.grounds.velocity") version "0.1.1" }

dependencies {
    implementation(project(":common"))
    implementation("io.kubernetes:client-java:26.0.0")
}
