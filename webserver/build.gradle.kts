plugins {
    kotlin("jvm")

    //Spring
    id("org.springframework.boot") version "3.1.0"
    kotlin("plugin.spring") version "1.8.22"
}

group = "com.tahoshi"
version = "1.0-SNAPSHOT"

dependencies {
    testImplementation(kotlin("test"))

    implementation(project(":core"))
    implementation(libs.cloudstream.library)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.kotlinx.coroutines.core) // I use runBlocking i hope it is good otherwise use something else
    implementation ("org.apache.httpcomponents:httpclient:4.5.13")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(23)
}