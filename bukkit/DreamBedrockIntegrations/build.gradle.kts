import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    id("io.papermc.paperweight.userdev")
    id("com.gradleup.shadow") version "9.0.0-beta4"
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://repo.md-5.net/content/repositories/snapshots/")
    maven("https://repo.md-5.net/content/repositories/releases/")
}

dependencies {
    paperweight.devBundle("net.sparklypower.sparklypaper", "1.21.4-R0.1-SNAPSHOT")
    compileOnly(project(":bukkit:DreamCore"))
    compileOnly("fr.neatmonster:nocheatplus:3.16.1-SNAPSHOT")
    compileOnly("com.comphenix.protocol:ProtocolLib:4.8.0")
    api("org.geysermc.cumulus:cumulus:1.1.2")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    val shadowJar = named<ShadowJar>("shadowJar") {
        dependencies {
            include {
                it.name == "org.geysermc.cumulus:cumulus:1.1.2"
            }
        }
    }

    "build" {
        dependsOn(shadowJar)
    }
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION