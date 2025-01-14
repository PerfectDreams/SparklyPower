import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("io.papermc.paperweight.userdev")
}

dependencies {
    paperweight.devBundle("net.sparklypower.sparklypaper", "1.21.4-R0.1-SNAPSHOT")
    implementation(kotlin("stdlib-jdk8"))
    implementation(files("../../libs/paper_server.jar"))
    implementation(files("../../libs/DreamCore-shadow.jar"))
    implementation(files("../../libs/WorldGuard.jar"))
}
