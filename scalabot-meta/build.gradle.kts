plugins {
    kotlin("jvm") version "1.6.10"
    id("org.jetbrains.kotlinx.kover") version "0.5.1"
}

group = "net.lamgc"
version = "0.3.1"

repositories {
    mavenCentral()
}

dependencies {
    val aetherVersion = "1.1.0"
    implementation("org.eclipse.aether:aether-api:$aetherVersion")
    implementation("org.eclipse.aether:aether-util:$aetherVersion")

    implementation("org.telegram:telegrambots-meta:6.0.1")

    implementation("com.google.code.gson:gson:2.9.0")

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.12.4")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}