import org.jetbrains.kotlin.gradle.dsl.JvmTarget
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
    implementation("ch.qos.logback:logback-classic:1.5.12")

    val aetherVersion = "1.1.0"
    implementation("org.eclipse.aether:aether-api:$aetherVersion")
    implementation("org.eclipse.aether:aether-util:$aetherVersion")
    implementation("org.eclipse.aether:aether-impl:$aetherVersion")
    implementation("org.eclipse.aether:aether-transport-file:$aetherVersion")
    implementation("org.eclipse.aether:aether-transport-http:$aetherVersion")
    implementation("org.eclipse.aether:aether-connector-basic:$aetherVersion")
    implementation("org.apache.maven:maven-aether-provider:3.3.9")
    implementation("org.codehaus.plexus:plexus-utils:3.5.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.0")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("org.jdom:jdom2:2.0.6.1")

    implementation("org.telegram:telegrambots-abilities:8.0.0")
    implementation("org.telegram:telegrambots-longpolling:8.0.0")
    implementation("org.telegram:telegrambots-client:8.0.0")

    implementation("io.prometheus:simpleclient:0.16.0")
    implementation("io.prometheus:simpleclient_httpserver:0.16.0")

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("com.github.stefanbirkner:system-lambda:1.2.1")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--add-opens", "java.base/java.util=ALL-UNNAMED", "--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
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
