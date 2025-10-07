import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("java")
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
}

group = "com.patternalarm"
version = "1.0.0"
description = "PatternAlarm Dashboard"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

// Version management
extra["awsSdkVersion"] = "2.21.0"
extra["kafkaVersion"] = "3.6.0"

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Database
    implementation("org.postgresql:postgresql")

    // AWS SDK v2
    implementation(platform("software.amazon.awssdk:bom:${property("awsSdkVersion")}"))
    implementation("software.amazon.awssdk:lambda")
    implementation("software.amazon.awssdk:kafka")
    implementation("software.amazon.awssdk:rds")
    implementation("software.amazon.awssdk:ecs")

    // Kafka Client (for health checks)
    implementation("org.apache.kafka:kafka-clients:${property("kafkaVersion")}")

    // JSON Processing (included in spring-boot-starter-web, but explicit for clarity)
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()

    // Better test output
    testLogging {
        events = setOf(
            TestLogEvent.PASSED,
            TestLogEvent.FAILED,
            TestLogEvent.SKIPPED
        )
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf(
        "-parameters",  // Preserve parameter names for Spring
        "-Xlint:unchecked",
        "-Xlint:deprecation"
    ))
}

// Spring Boot configuration
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("${project.name}-${project.version}.jar")
    launchScript()  // Makes JAR executable on Unix systems
}

// Disable plain JAR (we only need the Boot JAR)
tasks.named<Jar>("jar") {
    enabled = false
}

// Task to display project info
tasks.register("projectInfo") {
    group = "help"
    description = "Displays project information"

    doLast {
        println("""
            |
            |=====================================================
            |  PatternAlarm Dashboard
            |=====================================================
            |  Version:      ${project.version}
            |  Java:         ${java.sourceCompatibility}
            |  Spring Boot:  3.2.0
            |  AWS SDK:      ${property("awsSdkVersion")}
            |=====================================================
            |
        """.trimMargin())
    }
}