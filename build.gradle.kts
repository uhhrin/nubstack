plugins {
    java
}

allprojects {
    group = providers.gradleProperty("group").get()
    version = providers.gradleProperty("version").get()
}

subprojects {
    // Fabric Loom owns the cairn project model; do not force java-library onto it.
    if (name == "cairn") {
        return@subprojects
    }

    apply(plugin = "java-library")

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.fabricmc.net/")
        maven("https://jitpack.io")
    }

    extensions.configure<JavaPluginExtension> {
        val javaVersion = JavaVersion.toVersion(providers.gradleProperty("javaVersion").get())
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(providers.gradleProperty("javaVersion").get().toInt()))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(providers.gradleProperty("javaVersion").get().toInt())
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:6.1.3"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        // Gradle 9+ test workers need the launcher on the runtime classpath.
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
}
