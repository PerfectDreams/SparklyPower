import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("io.papermc.paperweight.userdev")
}

repositories {
    maven("https://jitpack.io")
}

dependencies {
    paperweight.devBundle("net.sparklypower.sparklypaper", "1.21.4-R0.1-SNAPSHOT")
    compileOnly(project(":bukkit:DreamCore"))
    compileOnly(project(":bukkit:DreamChat"))
    compileOnly(project(":bukkit:DreamCash"))
    compileOnly(project(":bukkit:DreamCorreios"))
    compileOnly("com.github.NuVotifier.NuVotifier:nuvotifier-api:2.7.2")
    compileOnly("com.github.NuVotifier.NuVotifier:nuvotifier-bukkit:2.7.2")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION