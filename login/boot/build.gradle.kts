plugins {
    id("nx-l2.spring-boot-conventions")
}

dependencies {
    implementation(project(":login:server"))
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

val loginSql = layout.settingsDirectory.dir("login/server/sql")

tasks.withType<Test>().configureEach {
    inputs.dir(loginSql).withPropertyName("loginSql").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("l2nx.test.login-sql-dir", loginSql.asFile.absolutePath)
}
