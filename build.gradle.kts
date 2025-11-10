plugins {
    kotlin("jvm") version "2.2.10"
    id("com.typewritermc.module-plugin")
}

val typewriterEngineVersion = file("../../version.txt").readText().trim()

group = "com.typewritermc.protection"
version = "0.1.0"

repositories {
    maven("https://maven.enginehub.org/repo/")
    maven("https://maven.typewritermc.com/external")
    maven("https://repo.fastasyncworldedit.dev/releases/") { content { includeGroup("com.fastasyncworldedit") } }
    maven("https://mvn.lumine.io/repository/maven-public/")
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenLocal()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
    implementation(platform("com.intellectualsites.bom:bom-newest:1.55"))
    compileOnly(platform("com.intellectualsites.bom:bom-newest:1.55"))
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.17")
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Core")
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Bukkit") { isTransitive = false }
    compileOnly("io.lumine:Mythic-Dist:5.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
    testImplementation("io.mockk:mockk:1.13.11")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.98.0")
    testImplementation("ch.qos.logback:logback-classic:1.5.12")
    testImplementation("com.typewritermc:engine-core:$typewriterEngineVersion")
    testImplementation("com.typewritermc:engine-paper:$typewriterEngineVersion")
}

kotlin {
    jvmToolchain(21)
}


typewriter {
    namespace = "typewritermc"

    extension {
        name = "Protection"
        shortDescription = "WorldGuard-grade protections managed in TypeWriter"
        description = """
            Provides a full-featured region engine with flag presets, selection tools and
            Paper/Folia-safe runtime enforcement so BornToCraft servers can drop the
            WorldGuard + ExtraFlags dependency entirely.
        """.trimIndent()
        engineVersion = file("../../version.txt").readText().trim()
        channel = com.typewritermc.moduleplugin.ReleaseChannel.BETA

        dependencies {
            paper {
                dependency("TypeWriter")
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}


