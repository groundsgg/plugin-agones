plugins { id("gg.grounds.velocity") version "0.1.1" }

dependencies {
    implementation(project(":common"))
    implementation("io.kubernetes:client-java:25.0.0")
}
