import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.10"
    java
    `maven-publish`
}

dependencies {
    api("org.telegram:telegrambots-abilities:6.0.1")
    api("org.slf4j:slf4j-api:1.7.36")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testImplementation("org.mockito:mockito-core:4.4.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.withType<Javadoc> {
    options {
        encoding = "UTF-8"
    }
}

java {
    withJavadocJar()
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

publishing {
    repositories {
        val repoRootKey = "maven.repo.local.root"
        val snapshot = project.version.toString().endsWith("-SNAPSHOT")
        val repoRoot = System.getProperty(repoRootKey)?.trim()
        if (repoRoot == null || repoRoot.isEmpty()) {
            logger.warn(
                "\"$repoRootKey\" configuration item is not specified, " +
                        "please add start parameter \"-D$repoRootKey {localPublishRepo}\"" +
                        " (if you are not currently executing the publish task, " +
                        "you can ignore this information)"
            )
            return@repositories
        }
        val repoUri = if (snapshot) {
            uri("$repoRoot/snapshots")
        } else {
            uri("$repoRoot/releases")
        }
        maven(repoUri)
    }

    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("ScalaBot-Extension-api")
                description.set(
                    "Dependencies for developing scalabot " +
                            "(a robotic application based on the TelegramBots[Github@rubenlagus/TelegramBots] project)"
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
