plugins {
    kotlin("jvm") version "1.7.10" apply false
    id("org.jetbrains.kotlinx.kover") version "0.5.1" apply false
}

allprojects {
    repositories {
        mavenCentral()
    }

    dependencies {

    }
    group = "net.lamgc"
    version = "0.5.2"
}