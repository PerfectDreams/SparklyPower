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
    paperDevBundle("1.19.4-R0.1-SNAPSHOT")
    compileOnly(project(":bukkit:DreamCore"))
    compileOnly(project(":bukkit:DreamCustomItems"))
    implementation("com.github.ChestShop-authors:ChestShop-3:3.11")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks {
    reobfJar {
        // For some reason the userdev plugin is using "unspecified" as the suffix, and that's not a good name
        // So we are going to change it to "PluginName-reobf.jar"
        outputJar.set(layout.buildDirectory.file("libs/${project.name}-reobf.jar"))
    }

    // Configure reobfJar to run when invoking the build task
    build {
        dependsOn(reobfJar)
    }
}