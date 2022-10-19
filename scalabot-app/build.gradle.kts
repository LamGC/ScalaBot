import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    application
    id("org.jetbrains.kotlinx.kover")
}

dependencies {
    implementation(project(":scalabot-meta"))
    implementation(project(":scalabot-extension"))

    implementation("org.slf4j:slf4j-api:2.0.0")
    implementation("io.github.microutils:kotlin-logging:2.1.23")
    implementation("ch.qos.logback:logback-classic:1.4.0")

    val aetherVersion = "1.1.0"
    implementation("org.eclipse.aether:aether-api:$aetherVersion")
    implementation("org.eclipse.aether:aether-util:$aetherVersion")
    implementation("org.eclipse.aether:aether-impl:$aetherVersion")
    implementation("org.eclipse.aether:aether-transport-file:$aetherVersion")
    implementation("org.eclipse.aether:aether-transport-http:$aetherVersion")
    implementation("org.eclipse.aether:aether-connector-basic:$aetherVersion")
    implementation("org.apache.maven:maven-aether-provider:3.3.9")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.6.20")
    implementation("com.google.code.gson:gson:2.9.0")

    implementation("org.jdom:jdom2:2.0.6.1")

    implementation("org.telegram:telegrambots-abilities:6.1.0")
    implementation("org.telegram:telegrambots:6.1.0")

    // Added as a mitigation measure for vulnerabilities.
    // When the relevant reference dependency updates it, it will be removed.
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.4.2")

    implementation("io.prometheus:simpleclient:0.16.0")
    implementation("io.prometheus:simpleclient_httpserver:0.16.0")

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.12.7")
    testImplementation("com.github.stefanbirkner:system-lambda:1.2.1")
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

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
