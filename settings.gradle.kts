pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.fabricmc.net/")
    }
    plugins {
        id("com.gradleup.shadow") version providers.gradleProperty("shadowVersion").get()
        id("net.fabricmc.fabric-loom") version providers.gradleProperty("fabricLoomVersion").get()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "nubstack"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.fabricmc.net/")
        maven("https://jitpack.io")
    }
}

include(
    "spine",
    "aegis-common",
    "aegis-paper",
    "aegis-velocity",
    "harbor",
    "gatekeep",
    "loom",
    "spotlight",
    "cull",
    "ledger",
    "amber",
    "tether",
    "cairn",
)
