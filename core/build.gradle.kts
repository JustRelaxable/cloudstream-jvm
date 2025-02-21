plugins {
    kotlin("jvm")
}

group = "com.tahoshi"
version = "1.0-SNAPSHOT"

dependencies {
    testImplementation(kotlin("test"))

    implementation(libs.cloudstream.library)
    implementation(libs.nicehttp)
    implementation(libs.kotlinx.coroutines.core) //Probably i don't need, it is possible to remove it if edited the source dependencies
    implementation(libs.jackson.module.kotlin)
    implementation(libs.gson)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(23)
}