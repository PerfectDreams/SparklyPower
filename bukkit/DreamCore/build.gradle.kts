import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    `java-library`
    id("com.gradleup.shadow") version "9.0.0-beta4"
    kotlin("plugin.serialization")
    id("io.papermc.paperweight.userdev")
}

// This is our DreamCore configuration that has all the dependencies (so plugins can just need to include the project as a project dependency)
//
// The reason we needed to do this is because we want ALL the declared "api" dependencies here BUT we also want the relocated dependencies
// done by the shadow JAR!
//
// So, to do that, we create a configuration that extends the default "shadow" configuration and the "compileClasspath" configuration, this allows us
// to satisfy both of our needs!
val shadowWithRuntimeDependencies by configurations.creating {
    extendsFrom(
        configurations["shadow"], // This has issues with kotlinx.serialization for some reason, investigate later
        configurations["compileClasspath"]
    )
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    api(project(":common:KotlinRuntime"))
    api(project(":common:rpc-payloads"))

    paperweight.devBundle("net.sparklypower.sparklypaper", "1.21.4-R0.1-SNAPSHOT")
    compileOnlyApi("com.comphenix.protocol:ProtocolLib:4.8.0")
    compileOnlyApi(files("../../libs/WorldEdit.jar"))
    compileOnlyApi(files("../../libs/WorldGuard.jar"))
    compileOnlyApi(files("../../libs/spark-1.10.34-bukkit.jar")) // jitpack doesn't compile spark because it requires Java 17
    compileOnlyApi("com.github.TechFortress:GriefPrevention:194aaf4e8b") // Using commits instead of pinning a version because GP hasn't released a new version yet
    compileOnlyApi("com.github.ChestShop-authors:ChestShop-3:3.12")
    api("net.perfectdreams.commands:command-framework-core:0.0.8")
    api("com.github.SparklyPower:PacketWrapper:88ddd591d8")
    compileOnlyApi("net.milkbowl.vault:VaultAPI:1.6") {
        exclude("org.bukkit", "bukkit") // Vault includes the Bukkit 1.9 API, this breaks our project
    }
    api("com.michael-bull.kotlin-coroutines-jdbc:kotlin-coroutines-jdbc:1.0.2")
    compileOnlyApi("com.github.apachezy:LangUtils:3.2.2")
    api(project(":common:tables"))
    api("com.google.code.gson:gson:2.8.9")
    api("com.zaxxer:HikariCP:5.0.1")
    api("org.postgresql:postgresql:42.3.6")
    api("com.okkero.skedule:skedule:1.2.4.1-SNAPSHOT")
    api("com.github.kevinsawicki:http-request:6.0")
    api("org.ow2.asm:asm:7.0")
    api("org.ow2.asm:asm-util:7.0")
    api("commons-codec:commons-codec:1.12")
    api("org.apache.commons:commons-lang3:3.9")
    api("org.mindrot:jbcrypt:0.4")
    api("com.github.salomonbrys.kotson:kotson:2.5.0")
    api(Dependencies.DISCORD_WEBHOOKS)
    api("com.github.ben-manes.caffeine:caffeine:3.0.5")
    api("org.apache.commons:commons-text:1.8")
    api("org.jsoup:jsoup:1.14.3")

    // Prometheus, for metrics
    api("io.prometheus:simpleclient:${Versions.PROMETHEUS}")
    api("io.prometheus:simpleclient_hotspot:${Versions.PROMETHEUS}")
    api("io.prometheus:simpleclient_common:${Versions.PROMETHEUS}")
    api("io.micrometer:micrometer-registry-prometheus:1.13.6")

    // Ktor
    api("io.ktor:ktor-server-netty:${Versions.KTOR}")
    api("io.ktor:ktor-client-cio:${Versions.KTOR}")
    api("com.charleskorn.kaml:kaml:0.49.0")

    // We use the lib JAR directly because the API artifact is outdated and doesn't have the chunktype classes in the right package
    // compileOnlyApi("com.viaversion:viaversion-api:4.4.1") // Used for packet manipulation
    compileOnlyApi(files("../../libs/ViaVersion.jar")) // Used for packet manipulation

    implementation("com.github.luben:zstd-jni:1.5.5-7")
    api("org.dyn4j:dyn4j:5.0.2")

    compileOnlyApi("me.lucko.luckperms:luckperms-api:4.3")
    // testCompileOnly(files("../../libs/paper_server.jar"))
    testCompileOnly("org.junit.jupiter:junit-jupiter-api:5.3.0-M1")
    testCompileOnly("org.junit.jupiter:junit-jupiter-engine:5.3.0-M1")
    testCompileOnly("io.mockk:mockk:1.9")
    testCompileOnly("org.assertj:assertj-core:3.10.0")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    val shadowJar = named<ShadowJar>("shadowJar") {
        archiveBaseName.set("DreamCore-shadow")

        exclude {
            it?.file?.name?.startsWith("patched_") == true
        }
    }

    "build" {
        dependsOn(shadowJar)
    }
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION