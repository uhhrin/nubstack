plugins {
    java
}

allprojects {
    group = providers.gradleProperty("group").get()
    version = providers.gradleProperty("version").get()
}

subprojects {
    apply(plugin = "java-library")

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
        "testImplementation"(platform("org.junit:junit-bom:5.11.4"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
    }
}
