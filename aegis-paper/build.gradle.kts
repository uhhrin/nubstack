plugins {
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":aegis-common"))
    implementation("org.mariadb.jdbc:mariadb-java-client:${property("mariaDbVersion")}")
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.jar {
    enabled = false
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}
