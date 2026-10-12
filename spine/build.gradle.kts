dependencies {
    api("com.zaxxer:HikariCP:${property("hikariVersion")}")
    api("io.lettuce:lettuce-core:${property("lettuceVersion")}")
    api("net.kyori:adventure-api:${property("adventureVersion")}")
    api("net.kyori:adventure-text-minimessage:${property("adventureVersion")}")
    api("net.kyori:adventure-text-serializer-plain:${property("adventureVersion")}")
    api("org.yaml:snakeyaml:${property("snakeYamlVersion")}")

    implementation("org.slf4j:slf4j-api:${property("slf4jVersion")}")

    compileOnly("org.jetbrains:annotations:26.0.2")

    testImplementation("com.h2database:h2:2.5.252")
    testRuntimeOnly("org.slf4j:slf4j-simple:${property("slf4jVersion")}")
}
