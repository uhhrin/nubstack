dependencies {
    api(project(":spine"))
    api("com.google.code.gson:gson:${property("gsonVersion")}")

    testImplementation("com.h2database:h2:2.5.252")
    testRuntimeOnly("org.slf4j:slf4j-simple:${property("slf4jVersion")}")
}
