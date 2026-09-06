plugins {
    kotlin("jvm") version "2.2.10"
    id("com.typewritermc.module-plugin") version "2.2.0"
}

group = "btcrenaud"
version = "0.1.3"

repositories {
    maven("https://jitpack.io/")
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://repo.bluecolored.de/releases")
    maven("https://maven.typewritermc.com/beta/")
    maven("https://maven.typewritermc.com/external/")
    maven("https://mvn.lumine.io/repository/maven-public/")
}

dependencies {
    compileOnly("de.bluecolored:bluemap-api:2.7.3")
    compileOnly("io.lumine:Mythic-Dist:5.8.2")
}

typewriter {
    namespace = "typewritermc"

    extension {
        name = "Protection"
        shortDescription = "DEPRECATED — WorldGuard-grade protections managed in TypeWriter"
        description = """DEPRECATED compatibility release. Provides the existing region engine with flag presets, selection tools and Paper/Folia-safe runtime enforcement. New deployments should use Typewriter's native region system; no new features are planned for this extension."""
        engineVersion = "0.9.0-beta-176"
        channel = com.typewritermc.moduleplugin.ReleaseChannel.BETA

        dependencies {
            paper {
                dependency("TypeWriter")
            }
        }
        paper()
    }
}

    

kotlin {
    jvmToolchain(21)
}

