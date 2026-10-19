plugins {
    id("nx-l2.spring-boot-conventions")
}

dependencies {
    implementation(project(":chronicles:high-five:server"))
    runtimeOnly(project(":chronicles:high-five:scripts"))
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
            from("../datapack") {
                exclude("sql/**")
            }
        }
    }
}

val loginSql = layout.settingsDirectory.dir("login/server/sql")
val gameSql = layout.projectDirectory.dir("../datapack/sql")

tasks.withType<Test>().configureEach {
    maxHeapSize = "4g"
    inputs.dir(loginSql).withPropertyName("loginSql").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(gameSql).withPropertyName("gameSql").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("l2nx.test.login-sql-dir", loginSql.asFile.absolutePath)
    systemProperty("l2nx.test.game-sql-dir", gameSql.asFile.absolutePath)
}
