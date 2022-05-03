import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.10"
    application
    // id("org.jetbrains.kotlin") version "1.6.10"
}

dependencies {
    implementation(project(":scalabot-extension"))

    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("io.github.microutils:kotlin-logging:2.1.21")
    implementation("ch.qos.logback:logback-classic:1.2.11")

    val aetherVersion = "1.1.0"
    implementation("org.eclipse.aether:aether-api:$aetherVersion")
    implementation("org.eclipse.aether:aether-util:$aetherVersion")
    implementation("org.eclipse.aether:aether-impl:$aetherVersion")
    implementation("org.eclipse.aether:aether-transport-file:$aetherVersion")
    implementation("org.eclipse.aether:aether-transport-http:$aetherVersion")
    implementation("org.eclipse.aether:aether-connector-basic:$aetherVersion")
    implementation("org.apache.maven:maven-aether-provider:3.3.9")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.6.20")
    implementation("com.google.code.gson:gson:2.9.0")

    implementation("org.jdom:jdom2:2.0.6.1")

    implementation("org.telegram:telegrambots-abilities:6.0.1")
    implementation("org.telegram:telegrambots:6.0.1")

    implementation("io.prometheus:simpleclient:0.15.0")
    implementation("io.prometheus:simpleclient_httpserver:0.15.0")

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.12.3")
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

tasks.jar.configure {
    exclude("**/logback-test.xml")
}
