import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    java
    kotlin("jvm") version "2.4.10"
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.gradleup.shadow") version "9.2.2"
    `maven-publish`
}

group = "io.github.team-sneakymouse"

version = providers.exec {
    workingDir(rootDir)
    commandLine("git", "show", "-s", "--format=%ct:%h", "--abbrev=12", "HEAD")
}.standardOutput.asText.map { commit ->
    val (timestamp, hash) = commit.trim().split(":", limit = 2)
    val date = DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneOffset.UTC)
        .format(Instant.ofEpochSecond(timestamp.toLong()))
    "$date-$hash"
}.get()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    exclusiveContent {
        forRepository { maven("https://maven.sneakyrp.com/releases") }
        filter { includeGroup("io.github.team-sneakymouse") }
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
    compileOnly("io.github.team-sneakymouse:sneakydb-api:2026.10.26-fd4e29e90ad0")
    testImplementation("io.github.team-sneakymouse:sneakydb-api:2026.10.26-fd4e29e90ad0")
    compileOnly("io.github.team-sneakymouse:sneakyutils:2026.10.18-657d452bd6f4")
    testImplementation(kotlin("test-junit5"))
    testImplementation("io.papermc.paper:paper-api:26.2.build.121-stable")
    testImplementation("io.github.team-sneakymouse:sneakyutils:2026.10.18-657d452bd6f4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    test {
        useJUnitPlatform()
    }

    processResources {
        inputs.property("version", project.version.toString())
        filesMatching("paper-plugin.yml") {
            expand("version" to project.version.toString())
        }
    }

    shadowJar {
        archiveBaseName.set("SneakyPrototypeKit")
        archiveClassifier.set("")
        mergeServiceFiles()
    }

    jar {
        enabled = false
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("26.2")
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

sourceSets {
    main {
        java.srcDir("src/main/kotlin")
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

val verifyDatabaseIsolation by tasks.registering {
    dependsOn(tasks.shadowJar)
    doLast {
        val jarFile = tasks.shadowJar.get().archiveFile.get().asFile
        val entries = zipTree(jarFile)
        listOf(
            "kotlin/jvm/functions/Function1.class"
        ).forEach { path ->
            check(!entries.matching { include(path) }.isEmpty) {
                "$path must be bundled in ${jarFile.name}"
            }
        }
        val bundledDatabaseClasses = entries.matching {
            include("com/sneakydb/**", "com/danidipp/sneakypocketbase/**", "org/postgresql/**", "com/zaxxer/hikari/**")
        }
        check(bundledDatabaseClasses.isEmpty) { "Database manager API/driver classes must not be bundled" }
        val staleClasses = entries.matching { include("**/*.class") }.files.filter { classFile ->
            classFile.readBytes().toString(Charsets.ISO_8859_1).contains("sneakypocketbase", ignoreCase = true)
        }
        check(staleClasses.isEmpty()) { "PocketBase reference remains in: ${staleClasses.joinToString()}" }
    }
}

tasks.check {
    dependsOn(verifyDatabaseIsolation)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "SneakyPrototypeKit"
            artifact(tasks.shadowJar) {
                classifier = null
            }
            pom {
                name.set("SneakyPrototypeKit")
                description.set("Paper plugin for creating custom items with configurable abilities and charges.")
                url.set("https://github.com/Team-Sneakymouse/SneakyPrototypeKit")
                properties.put("sneakyrp.deployment.type", "paper-plugin")
                scm {
                    url.set("https://github.com/Team-Sneakymouse/SneakyPrototypeKit")
                    connection.set("scm:git:https://github.com/Team-Sneakymouse/SneakyPrototypeKit.git")
                }
            }
        }
    }
    repositories {
        maven {
            name = "sneakyrp"
            url = uri("https://maven.sneakyrp.com/releases")
            credentials(PasswordCredentials::class)
            authentication {
                create<org.gradle.authentication.http.BasicAuthentication>("basic")
            }
        }
    }
}

tasks.withType<PublishToMavenRepository>().configureEach {
    dependsOn(tasks.check)
}
