plugins {
    kotlin("jvm") version "2.2.10"
    id("com.typewritermc.module-plugin") version "2.1.0"
}

repositories {
    maven("https://repo.codemc.io/repository/maven-public/")
    mavenCentral()
    maven("https://repo.bluecolored.de/releases")
    flatDir {
        dir("libs")
    }
}
dependencies {
    implementation("com.typewritermc:QuestExtension:0.9.0")
    implementation(files("libs/QuestPlusExtension.jar"))
    implementation(kotlin("reflect"))
    compileOnly("de.bluecolored:bluemap-api:2.7.3")
    compileOnly("com.flowpowered:flow-math:1.0.3")
}



group = "btc.renaud"
version = "0.1.0"

typewriter {
    namespace = "renaud"

    extension {
        name = "Protection"
        shortDescription = "Region management system for TypeWriter"
        description = """
            |Protection Extension is a region management system for TypeWriter, 
            |engineered for BTC Studio infrastructure. It provides WorldGuard-grade 
            |protection features, fully optimized for Paper and Folia environments.
            """.trimMargin()
        engineVersion = "0.9.0-beta-171"
        channel = com.typewritermc.moduleplugin.ReleaseChannel.BETA

        dependencies {
            dependency("typewritermc", "Quest")
            paper()
        }
    }
}

kotlin {
    jvmToolchain(21)
}

