plugins {
    java
}

dependencies {
    compileOnly(project(":scalabot-extension"))

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<Javadoc> {
    options {
        encoding = "UTF-8"
    }
}
