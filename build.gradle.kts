plugins {
    kotlin("jvm") version "1.9.23" apply false
    id("org.jetbrains.kotlinx.kover") version "0.7.5" apply false
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.14.0" apply false
}

allprojects {
    repositories {
        mavenCentral()
    }

    dependencies {

    }
    group = "net.lamgc"
    version = "0.7.0"
}
