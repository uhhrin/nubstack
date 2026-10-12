plugins {
    id("net.fabricmc.fabric-loom")
}

base {
    archivesName.set("cairn")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(providers.gradleProperty("javaVersion").get().toInt()))
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    // Minecraft 26.1+ is unobfuscated: no mappings, standard dependency scopes.
    "minecraft"("com.mojang:minecraft:${property("minecraftVersion")}")
    implementation("net.fabricmc:fabric-loader:${property("fabricLoaderVersion")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabricApiVersion")}")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(providers.gradleProperty("javaVersion").get().toInt())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    filesMatching("fabric.mod.json") {
        expand("version" to pluginVersion)
    }
}
