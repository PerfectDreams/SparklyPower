plugins {
    kotlin("jvm")
    id("io.papermc.paperweight.userdev")
    kotlin("plugin.serialization")
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
    compileOnly(files("../../libs/mcMMO.jar"))
    compileOnly("com.comphenix.protocol:ProtocolLib:4.8.0")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION