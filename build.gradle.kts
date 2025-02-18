plugins {
    java
    kotlin("jvm") version "1.9.22"
    id("xyz.jpenilla.run-paper") version "2.2.2"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "net.sneakyprototypekit"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(kotlin("stdlib"))
    compileOnly("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")
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
        minecraftVersion("1.20.6")
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    compileKotlin {
        kotlinOptions {
            jvmTarget = "21"
            javaParameters = true
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