plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(project(":scalabot-extension"))

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}