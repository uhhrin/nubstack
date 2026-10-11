plugins {
    id("net.fabricmc.fabric-loom")
}

base {
    archivesName.set("cairn")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraftVersion")}")
    implementation("net.fabricmc:fabric-loader:${property("fabricLoaderVersion")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabricApiVersion")}")
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    filesMatching("fabric.mod.json") {
        expand("version" to pluginVersion)
    }
}

java {
    withSourcesJar()
}
