plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlinx.kover")
    id("org.jetbrains.dokka") version "1.7.0"
    `maven-publish`
    signing
}

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

    dokkaHtmlPlugin("org.jetbrains.dokka:javadoc-plugin:1.7.0")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
    }
}

java {
    withJavadocJar()
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

val javadocJar = tasks.named<Jar>("javadocJar") {
    from(tasks.named("dokkaJavadoc"))
}

publishing {
    repositories {
        if (project.version.toString().endsWith("-SNAPSHOT", ignoreCase = true)) {
            maven("https://nexus.kuku.me/repository/maven-snapshots/") {
                credentials {
                    username = project.properties["repo.credentials.private.username"].toString()
                    password = project.properties["repo.credentials.private.password"].toString()
                }
            }
        } else {
            maven("https://nexus.kuku.me/repository/maven-releases/") {
                credentials {
                    username = project.properties["repo.credentials.private.username"].toString()
                    password = project.properties["repo.credentials.private.password"].toString()
                }
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
            artifact(javadocJar)
            artifact(tasks.named("sourcesJar"))
            pom {
                name.set("ScalaBot-meta")
                description.set(
                    "Shared components used by scalabot (such as configuration classes)"
                )
                url.set("https://github.com/LamGC/ScalaBot")
                licenses {
                    license {
                        name.set("The MIT License")
                        url.set("https://www.opensource.org/licenses/mit-license.php")
                    }
                }
                developers {
                    developer {
                        id.set("LamGC")
                        name.set("LamGC")
                        email.set("lam827@lamgc.net")
                        url.set("https://github.com/LamGC")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/LamGC/ScalaBot.git")
                    developerConnection.set("scm:git:https://github.com/LamGC/ScalaBot.git")
                    url.set("https://github.com/LamGC/ScalaBot")
                }
                issueManagement {
                    url.set("https://github.com/LamGC/ScalaBot/issues")
                    system.set("Github Issues")
                }
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["maven"])
}
