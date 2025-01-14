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
    compileOnly(project(":bukkit:DreamMini"))
    compileOnly(project(":bukkit:DreamBedrockIntegrations"))
    // TODO: This causes a circular dependency, how to fix it?
    // compileOnly(project(":bukkit:DreamPicaretaMonstra"))
    // compileOnly(project(":bukkit:DreamMochilas"))
    compileOnly(files("../../libs/mcMMO.jar"))
    compileOnly("com.comphenix.protocol:ProtocolLib:4.8.0")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION