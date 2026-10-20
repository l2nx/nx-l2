plugins {
    id("nx-l2.spring-boot-conventions")
}

dependencies {
    implementation(project(":login:server"))

    testImplementation(testFixtures(project(":libs:network")))
    testImplementation(testFixtures(project(":libs:gslink")))
    testImplementation(libs.spring.boot.grpc.test)
    // The health stubs (grpc-services) the GS-link port test calls; the app gets them through :login:server.
    testImplementation(libs.spring.boot.starter.grpc.server)
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
