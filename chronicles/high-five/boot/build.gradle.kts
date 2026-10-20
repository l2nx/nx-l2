plugins {
    id("nx-l2.spring-boot-conventions")
}

dependencies {
    implementation(project(":chronicles:high-five:server"))
    runtimeOnly(project(":chronicles:high-five:scripts"))

    testImplementation(testFixtures(project(":libs:database")))
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
}
