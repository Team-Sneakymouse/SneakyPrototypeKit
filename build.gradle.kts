plugins {
    java
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("xyz.jpenilla.run-paper") version "2.2.2"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "net.sneakyprototypekit"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly(files("libs/SneakyPocketbase-1.0.jar"))
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
        minecraftVersion("1.21.4")
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    compileKotlin {
        kotlinOptions {
            jvmTarget = "21"
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

sourceSets {
    main {
        java.srcDir("src/main/kotlin")
        resources.srcDir("src/main/resources")
    }
}

kotlin {
    jvmToolchain(21)
} 