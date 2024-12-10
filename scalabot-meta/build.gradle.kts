plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlinx.kover")
    id("org.jetbrains.dokka") version "1.9.20"
    `maven-publish`
    signing
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
}

dependencies {
    val aetherVersion = "1.1.0"
    api("org.eclipse.aether:aether-api:$aetherVersion")
    implementation("org.eclipse.aether:aether-util:$aetherVersion")

    implementation("org.telegram:telegrambots-meta:8.0.0")

    api("com.google.code.gson:gson:2.10.1")

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")

    dokkaHtmlPlugin("org.jetbrains.dokka:javadoc-plugin:1.9.10")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
    }
}

java {
    withJavadocJar()
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

val javadocJar = tasks.named<Jar>("javadocJar") {
    from(tasks.named("dokkaJavadoc"))
}

publishing {
    repositories {
        maven("https://git.lamgc.me/api/packages/LamGC/maven") {
            credentials {
                username = project.properties["repo.credentials.self-git.username"].toString()
                password = project.properties["repo.credentials.self-git.password"].toString()
            }
        }
        val kukuRepoUrl = if (project.version.toString().endsWith("-SNAPSHOT", ignoreCase = true)) {
            "https://nexus.kuku.me/repository/maven-snapshots/"
        } else {
            "https://nexus.kuku.me/repository/maven-releases/"
        }
        maven(kukuRepoUrl) {
            credentials {
                username = project.properties["repo.credentials.kuku-repo.username"].toString()
                password = project.properties["repo.credentials.kuku-repo.password"].toString()
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
