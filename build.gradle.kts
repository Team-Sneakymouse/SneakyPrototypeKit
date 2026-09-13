plugins {
    java
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.gradleup.shadow") version "9.2.2"
}

group = "net.sneakyprototypekit"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly(files("../SneakyPocketbase/build/libs/SneakyPocketbase-1.0-api.jar"))
}

tasks {
    processResources {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        from(sourceSets.main.get().resources.srcDirs) {
            expand(
                "version" to project.version
            )
        }
    }
    
    shadowJar {
        archiveBaseName.set(project.name)
        archiveClassifier.set("")
        mergeServiceFiles()
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
        resources.srcDir("src/main/resources")
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
} 

val verifyPocketbaseIsolation by tasks.registering {
    dependsOn(tasks.shadowJar)
    doLast {
        val jarFile = tasks.shadowJar.get().archiveFile.get().asFile
        val entries = zipTree(jarFile)
        listOf(
            "kotlin/jvm/functions/Function1.class",
            "kotlinx/serialization/json/JsonKt.class"
        ).forEach { path ->
            check(!entries.matching { include(path) }.isEmpty) {
                "$path must be bundled in ${jarFile.name}"
            }
        }
        val staleClasses = entries.matching { include("**/*.class") }.files.filter { classFile ->
            classFile.readBytes().toString(Charsets.ISO_8859_1)
                .contains("com/danidipp/sneakypocketbase/PBRunnable")
        }
        check(staleClasses.isEmpty()) {
            "Obsolete PBRunnable reference remains in: ${staleClasses.joinToString()}"
        }
    }
}

tasks.check {
    dependsOn(verifyPocketbaseIsolation)
}
