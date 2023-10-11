plugins {
    kotlin("jvm") version "1.9.10" apply false
    id("org.jetbrains.kotlinx.kover") version "0.7.4" apply false
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.13.2" apply false
}

allprojects {
    repositories {
        mavenCentral()
    }

    dependencies {

    }
    group = "net.lamgc"
    version = "0.6.1"
}