import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("io.papermc.paperweight.userdev")
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    paperweight.devBundle("net.sparklypower.sparklypaper", "1.21.4-R0.1-SNAPSHOT")
    compileOnly(project(":bukkit:DreamCore"))
    compileOnly(project(":bukkit:DreamCasamentos"))
    compileOnly(project(":bukkit:DreamCash"))
    compileOnly(project(":bukkit:DreamChat"))
    compileOnly(project(":bukkit:DreamClubes"))
    compileOnly(project(":bukkit:DreamLoja"))
    compileOnly(project(":bukkit:DreamRaffle"))
    compileOnly(project(":bukkit:DreamVanish"))
    compileOnly(files("../../libs/mcMMO.jar"))
    compileOnly("net.luckperms:api:5.4")
    compileOnly("net.skinsrestorer:skinsrestorer-api:14.2.8")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION