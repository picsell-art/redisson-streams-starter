import java.util.Base64

plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("java-library")

    id("io.spring.dependency-management") version "1.1.7"
    id("org.springframework.boot") version "3.5.7" apply false

    id("com.vanniktech.maven.publish") version "0.34.0"
    id("signing")
}

val starterVersion = "0.0.2"
val artifact = "redisson-streams-starter"
val starterGroup = "art.picsell.starter"

group = starterGroup
version = starterVersion
description = "Spring Boot starter for redisson streams producers and consumers in Kafka-like way"

// --- Java / Kotlin setup ---
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

// --- Custom source set for application tests ---
sourceSets {
    val main by getting
    val test by getting
    val applicationTest by creating {
        kotlin.setSrcDirs(listOf("src/applicationTest/kotlin"))
        resources.setSrcDirs(listOf("src/applicationTest/resources"))
        compileClasspath += main.output + configurations["testCompileClasspath"]
        runtimeClasspath += output + main.output + configurations["testRuntimeClasspath"]
    }
}

configurations {
    named("applicationTestImplementation") {
        extendsFrom(configurations["testImplementation"])
    }
    named("applicationTestRuntimeOnly") {
        extendsFrom(configurations["testRuntimeOnly"])
    }
}

// --- Dependency Management ---
dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.2.0")
    }
}

repositories {
    mavenCentral()
}

// --- Dependencies ---
dependencies {
    // Spring Boot core
    compileOnly("org.springframework.boot:spring-boot")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")

    // Runtime dependencies that must propagate to consumer project
    api("com.fasterxml.jackson.module:jackson-module-kotlin")
    api("org.jetbrains.kotlin:kotlin-reflect")
    api("io.github.microutils:kotlin-logging:4.0.0-beta-2")
    api("org.slf4j:slf4j-api:2.0.13")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    api("org.redisson:redisson:3.37.0")
    api("org.springframework.boot:spring-boot-starter-data-redis")
    api("org.springframework.boot:spring-boot-starter-aop")

    // Optional / compile-time only dependencies
    compileOnly("org.springframework.boot:spring-boot-starter-webflux")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")

    // Annotation processor for configuration properties
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Application test source set
    add("applicationTestImplementation", "org.wiremock:wiremock-standalone:3.9.1")
    add("applicationTestImplementation", "org.springframework.boot:spring-boot-starter-webflux")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<Test>("applicationTest") {
    description = "Runs the application tests that rely on WireMock stubs."
    group = "verification"
    val applicationTestSourceSet = sourceSets["applicationTest"]
    testClassesDirs = applicationTestSourceSet.output.classesDirs
    classpath = applicationTestSourceSet.runtimeClasspath
    shouldRunAfter("test")
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn("applicationTest")
}

// --- Signing setup ---
signing {
    useInMemoryPgpKeys(
        findProperty("signing.keyId") as String?,
        String(Base64.getDecoder().decode(findProperty("signingKey") as String)),
        findProperty("signing.password") as String?
    )
    sign(publishing.publications)
}

// --- Publishing setup ---
publishing {
    publications {
        create<MavenPublication>("github") {
            groupId = starterGroup
            artifactId = artifact
            version = starterVersion

            from(components["java"])

            pom {
                name.set("Redisson Redis streams Spring Boot starter")
                description.set(project.description)
                url.set("https://github.com/picsell-art/redisson-streams-starter")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("vonpartridge")
                        name.set("Lev Kurashchenko")
                        email.set("l.kurashchenko@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/picsell-art/redisson-streams-starter.git")
                    developerConnection.set("scm:git:ssh://github.com/picsell-art/redisson-streams-starter.git")
                    url.set("https://github.com/picsell-art/redisson-streams-starter")
                }
                properties.put("java.version", "17")
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/picsell-art/redisson-streams-starter")
            credentials {
                username = findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

// --- Maven Central (via Vanniktech plugin) ---
mavenPublishing {
    coordinates(starterGroup, artifact, starterVersion)
    publishToMavenCentral(true)
    signAllPublications()

    pom {
        name.set("Redisson Redis streams Spring Boot starter")
        description.set(project.description)
        url.set("https://github.com/picsell-art/redisson-streams-starter")
        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("vonpartridge")
                name.set("Lev Kurashchenko")
                email.set("l.kurashchenko@gmail.com")
            }
        }
        scm {
            connection.set("scm:git:https://github.com/picsell-art/redisson-streams-starter.git")
            developerConnection.set("scm:git:ssh://github.com/picsell-art/redisson-streams-starter.git")
            url.set("https://github.com/picsell-art/redisson-streams-starter")
        }
    }
}

// --- Helper to enforce signing dependency ordering ---
fun setDependants(parent: String, child: String) {
    afterEvaluate {
        val parentTask = tasks.named(parent)
        tasks.matching { it.name.startsWith(child) }.configureEach {
            dependsOn(parentTask)
        }
    }
}

setDependants("signMavenPublication", "publish")
setDependants("signGithubPublication", "publish")
setDependants("plainJavadocJar", "generateMetadataFileFor")
