import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.papermc.paperweight.userdev")
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    paperweight.devBundle("net.sparklypower.sparklypaper", "1.21.4-R0.1-SNAPSHOT")
    compileOnly(project(":bukkit:DreamCore"))
    compileOnly(project(":bukkit:DreamEmptyWorldGenerator"))
    compileOnly(project(":bukkit:DreamKits"))
    compileOnly(project(":bukkit:DreamWarps"))
    compileOnly(project(":bukkit:DreamScoreboard"))
    compileOnly(project(":bukkit:DreamBedrockIntegrations"))
    compileOnly(project(":bukkit:DreamChat"))
    compileOnly("com.github.GriefPrevention:GriefPrevention:885c0eeb1f") // Using commits instead of pinning a version because GP hasn't released a new version yet
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION