plugins {
    kotlin("jvm")
    id("io.papermc.paperweight.userdev")
    kotlin("plugin.serialization")
}

repositories {
    maven("https://jitpack.io")
}

dependencies {
    paperweight.devBundle("net.sparklypower.sparklypaper", "1.21.3-R0.1-SNAPSHOT")
    compileOnly(project(":bukkit:DreamCore"))
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION