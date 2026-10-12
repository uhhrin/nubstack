plugins {
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":spine"))
    implementation("org.mariadb.jdbc:mariadb-java-client:${property("mariaDbVersion")}")
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    testImplementation("com.h2database:h2:2.5.252")
    testRuntimeOnly("org.slf4j:slf4j-simple:${property("slf4jVersion")}")
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
