plugins {
    id("nx-l2.spring-boot-conventions")
}

dependencies {
    implementation(project(":login:server"))

    testImplementation(testFixtures(project(":libs:network")))
}

application {
    applicationName = "login-server"
    mainClass = "app.l2nx.login.boot.LoginApplication"
    applicationDefaultJvmArgs = applicationDefaultJvmArgs + listOf(
        "-XX:+UseZGC",
        "-Xms128m",
        "-Xmx256m",
    )
}
