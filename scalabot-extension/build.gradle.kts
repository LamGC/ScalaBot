plugins {
    `java-library`
    jacoco
    `maven-publish`
    signing
}

dependencies {
    implementation("commons-codec:commons-codec:1.16.1")
    api("org.telegram:telegrambots-abilities:8.0.0")
    api(project(":scalabot-meta"))

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.telegram:telegrambots-client:8.0.0")
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
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

publishing {
    repositories {
        maven("https://git.lamgc.me/api/packages/LamGC/maven") {
            credentials {
                username = project.properties["repo.credentials.self-git.username"].toString()
                password = project.properties["repo.credentials.self-git.password"].toString()
            }
        }
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

signing {
    useGpgCmd()
    sign(publishing.publications["maven"])
}
