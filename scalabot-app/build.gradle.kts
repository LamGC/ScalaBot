import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    application
    id("org.jetbrains.kotlinx.kover")
}

dependencies {
    implementation(project(":scalabot-meta"))
    implementation(project(":scalabot-extension"))

    implementation("org.slf4j:slf4j-api:2.0.11")
    implementation("io.github.microutils:kotlin-logging:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    val aetherVersion = "1.1.0"
    implementation("org.eclipse.aether:aether-api:$aetherVersion")
    implementation("org.eclipse.aether:aether-util:$aetherVersion")
    implementation("org.eclipse.aether:aether-impl:$aetherVersion")
    implementation("org.eclipse.aether:aether-transport-file:$aetherVersion")
    implementation("org.eclipse.aether:aether-transport-http:$aetherVersion")
    implementation("org.eclipse.aether:aether-connector-basic:$aetherVersion")
    implementation("org.apache.maven:maven-aether-provider:3.3.9")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.22")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("org.jdom:jdom2:2.0.6.1")

    implementation("org.telegram:telegrambots-abilities:6.9.7.1")
    implementation("org.telegram:telegrambots:6.8.0")

    implementation("io.prometheus:simpleclient:0.16.0")
    implementation("io.prometheus:simpleclient_httpserver:0.16.0")

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.9")
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
