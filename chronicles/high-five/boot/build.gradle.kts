plugins {
    id("nx-l2.launcher-conventions")
}

dependencies {
    implementation(project(":chronicles:high-five:server"))
    runtimeOnly(project(":chronicles:high-five:scripts"))
    runtimeOnly(libs.mysql.connector)
    runtimeOnly(libs.slf4j.simple)
}

application {
    applicationName = "game-server"
    mainClass = "app.l2nx.highfive.GameServer"
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Djava.util.logging.manager=app.l2nx.commons.log.ServerLogManager",
        "-Dorg.slf4j.simpleLogger.log.com.zaxxer.hikari=warn",
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
