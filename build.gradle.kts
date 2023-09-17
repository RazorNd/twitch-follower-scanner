plugins {
    id("org.springframework.boot") version "3.1.2"
    id("io.spring.dependency-management") version "1.1.2"
    id("org.graalvm.buildtools.native") version "0.9.23"
    kotlin("jvm") version "1.8.22"
    id("org.asciidoctor.jvm.convert") version "3.3.2"
}

group = "ru.razornd.twitch"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

val asciiDoc: Configuration by configurations.creating

repositories {
    mavenCentral()
}

extra["springmockk"] = "4.0.2"
extra["assertj-db"] = "2.0.2"
extra["wiremock"] = "3.0.4"
val snippetsDir = file("build/generated-snippets")

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.session:spring-session-data-redis")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.flywaydb:flyway-core")
    implementation("org.postgresql:r2dbc-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    asciiDoc("org.springframework.restdocs:spring-restdocs-asciidoctor")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("com.ninja-squad:springmockk:${property("springmockk")}")
    testImplementation("org.assertj:assertj-db:${property("assertj-db")}")
    testImplementation("org.postgresql:postgresql")
    testImplementation("com.squareup.okhttp3:mockwebserver")
    testImplementation("org.wiremock:wiremock:${property("wiremock")}")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:r2dbc")
    testImplementation("org.springframework.restdocs:spring-restdocs-webtestclient")
}

tasks.compileKotlin {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "17"
    }
}

tasks.test {
    useJUnitPlatform()
    outputs.dir(snippetsDir)
}

tasks.asciidoctor {
    inputs.dir(snippetsDir)
    configurations("asciiDoc")
    dependsOn(tasks.test)
}

tasks.processAot {
    jvmArgs = listOf("-Dspring.profiles.active=process-aot")
}

tasks.bootBuildImage {
    val registryUrl: String? by project
    val registryUsername: String? by project
    val registryPassword: String? by project

    val domain = listOfNotNull(registryUrl, registryUsername?.lowercase(), project.name).joinToString("/")

    imageName = "$domain:${project.version}"

    docker {
        publishRegistry {
            url = registryUrl
            username = registryUsername
            password = registryPassword
        }
    }
}