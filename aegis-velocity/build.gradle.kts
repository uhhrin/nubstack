plugins {
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":aegis-common"))
    implementation("org.mariadb.jdbc:mariadb-java-client:${property("mariaDbVersion")}")
    compileOnly("com.velocitypowered:velocity-api:${property("velocityApiVersion")}")
    annotationProcessor("com.velocitypowered:velocity-api:${property("velocityApiVersion")}")
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
