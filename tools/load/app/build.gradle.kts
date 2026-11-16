plugins {
    id("nx-l2.java-conventions")
    application
    id("org.springframework.boot")
}

configurations.configureEach {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
}

dependencies {
    implementation(project(":tools:load:core"))
    implementation(project(":tools:load:protocol-p267"))
    implementation(libs.commons.lang3)
    implementation(libs.postgresql)
    implementation(libs.spring.boot)
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.spring.boot.starter.log4j2)
    implementation(libs.spring.boot.starter.restclient)
    implementation(libs.spring.boot.starter.validation)
    annotationProcessor(platform(libs.spring.boot.dependencies))
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(testFixtures(project(":libs:database")))
    testImplementation(libs.testcontainers)
}

application {
    applicationName = "load"
    mainClass = "app.l2nx.load.app.LoadApplication"
}

// The integration test starts the real servers in its own copies of their distributions: it patches the game config
// and the servers write into their working dirs, while the servers' own tests run in their test-distribution in
// parallel. -Pchronicles may leave the game out.
val scenarioDistributions = mapOf(
    "login" to ":login:boot",
    "game" to ":chronicles:high-five:boot",
).mapNotNull { (name, path) -> findProject(path)?.let { name to it } }
    .associate { (name, server) ->
        val destination = layout.buildDirectory.dir("scenario-distributions/$name")
        val sync = tasks.register<Sync>("${name}ScenarioDistribution") {
            from(server.tasks.named("installDist")) {
                exclude("data/geodata/**", "log/**")
            }
            into(destination)
        }
        name to (sync to destination)
    }

tasks.withType<Test>().configureEach {
    scenarioDistributions.forEach { (name, distribution) ->
        dependsOn(distribution.first)
        systemProperty("l2nx.load.test.$name-distribution", distribution.second.get().asFile.absolutePath)
    }
}
