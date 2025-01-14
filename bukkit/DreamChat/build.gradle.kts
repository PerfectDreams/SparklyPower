import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.papermc.paperweight.userdev")
}

repositories {
    mavenCentral()
    maven("https://repo.alessiodp.com/releases/")
}

dependencies {
    paperweight.devBundle("net.sparklypower.sparklypaper", "1.21.4-R0.1-SNAPSHOT")
    compileOnly(project(":bukkit:DreamCore"))
    compileOnly(project(":bukkit:DreamCorreios"))
    compileOnly(project(":bukkit:DreamCasamentos"))
    compileOnly(project(":bukkit:DreamClubes"))
    compileOnly(project(":bukkit:DreamVanish"))
    compileOnly(project(":bukkit:DreamBedrockIntegrations"))
    compileOnly(project(":bukkit:DreamSonecas"))
    compileOnly(files("../../libs/mcMMO.jar"))
    compileOnly("net.luckperms:api:5.0")
    compileOnly("com.github.ChestShop-authors:ChestShop-3:3.12")
    compileOnly("net.citizensnpcs:citizens-main:2.0.30-SNAPSHOT")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION
