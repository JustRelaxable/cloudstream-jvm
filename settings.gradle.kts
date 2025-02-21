rootProject.name = "cloudstream-jvm"

pluginManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
    }
}

dependencyResolutionManagement{
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}

include("core")
include("webserver")
