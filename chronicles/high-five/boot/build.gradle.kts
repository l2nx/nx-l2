plugins {
    id("nx-l2.spring-boot-conventions")
}

dependencies {
    implementation(project(":chronicles:high-five:server"))
    runtimeOnly(project(":chronicles:high-five:scripts"))

    testImplementation(testFixtures(project(":libs:database")))
    testImplementation(libs.spring.modulith.starter.test)
}

application {
    applicationName = "game-server"
    mainClass = "app.l2nx.highfive.boot.HighFiveApplication"
    applicationDefaultJvmArgs = applicationDefaultJvmArgs + listOf(
        "-XX:+UseZGC",
        "-Xms2g",
        "-Xmx4g",
    )
}

distributions {
    main {
        contents {
            from("../datapack")
        }
    }
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "4g"
    // The tests run in build/test-distribution; the frozen legacy violations are committed with the sources.
    val archunitStore = layout.projectDirectory.dir("src/test/archunit-store")
    systemProperty("archunit.freeze.store.default.path", archunitStore.asFile.absolutePath)
    inputs.files(archunitStore.asFileTree).withPropertyName("archunitStore").withPathSensitivity(PathSensitivity.RELATIVE)
    // -Parchunit.freeze.refreeze=true or -Parchunit.freeze.store.default.allowStoreCreation=true rewrites the store
    // on purpose; both reach the test JVM only when given.
    listOf("freeze.refreeze", "freeze.store.default.allowStoreCreation").forEach { key ->
        providers.gradleProperty("archunit.$key").orNull?.let { systemProperty("archunit.$key", it) }
    }
}
