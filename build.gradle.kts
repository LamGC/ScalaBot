plugins {
    kotlin("jvm") version "1.8.10" apply false
    id("org.jetbrains.kotlinx.kover") version "0.6.1" apply false
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.13.0" apply false
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