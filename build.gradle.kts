plugins {
    kotlin("jvm") version "2.1.0" apply false
    id("org.jetbrains.kotlinx.kover") version "0.8.3" apply false
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.16.3" apply false
}

allprojects {
    repositories {
        mavenCentral()
    }

    dependencies {

    }
    group = "net.lamgc"
    version = "0.8.0"
}
