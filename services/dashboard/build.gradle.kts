import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("java")
    id("org.springframework.boot") version "3.4.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.patternalarm"
version = "1.0.0"
description = "PatternAlarm Dashboard"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
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
    // Spring Boot Starters - MINIMAL for first run
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

    // Add these back incrementally:
    // implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    // implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Database - commented out for now
    // implementation("org.postgresql:postgresql")

    // AWS SDK v2 - commented out for now
    // implementation(platform("software.amazon.awssdk:bom:${property("awsSdkVersion")}"))
    // implementation("software.amazon.awssdk:lambda")
    // implementation("software.amazon.awssdk:kafka")
    // implementation("software.amazon.awssdk:rds")
    // implementation("software.amazon.awssdk:ecs")

    // Kafka Client - commented out for now
    // implementation("org.apache.kafka:kafka-clients:${property("kafkaVersion")}")

    // Lombok (1.18.30+ required for Java 17)
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()

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
        "-parameters",
        "-Xlint:unchecked",
        "-Xlint:deprecation"
    ))
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("${project.name}-${project.version}.jar")
    launchScript()
}

tasks.named<Jar>("jar") {
    enabled = false
}

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
            |  Mode:         Minimal (Web only)
            |=====================================================
            |
        """.trimMargin())
    }
}