import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("io.papermc.paperweight.userdev")
}

dependencies {
    paperweight.devBundle("net.sparklypower.sparklypaper", "1.21.4-R0.1-SNAPSHOT")
    compileOnly(project(":bukkit:DreamCore"))
    implementation("net.citizensnpcs:citizensapi:2.0.30-SNAPSHOT")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION