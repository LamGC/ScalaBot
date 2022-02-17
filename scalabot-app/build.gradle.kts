import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.10"
    application
    // id("org.jetbrains.kotlin") version "1.6.10"
}

dependencies {
    implementation(project(":scalabot-extension"))

    implementation("org.slf4j:slf4j-api:1.7.33")
    implementation("io.github.microutils:kotlin-logging:2.1.21")
    implementation("ch.qos.logback:logback-classic:1.2.10")

    implementation("org.eclipse.aether:aether-api:1.1.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    implementation("com.google.code.gson:gson:2.8.9")

    implementation("org.jdom:jdom2:2.0.6.1")

    implementation("org.telegram:telegrambots-abilities:5.6.0")
    implementation("org.telegram:telegrambots:5.6.0")

    implementation("io.prometheus:simpleclient:0.15.0")
    implementation("io.prometheus:simpleclient_httpserver:0.15.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()

}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

application {
    mainClass.set("net.lamgc.scalabot.AppMainKt")
}