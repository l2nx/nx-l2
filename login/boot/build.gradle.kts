plugins {
    id("nx-l2.launcher-conventions")
}

dependencies {
    implementation(project(":login:server"))
    runtimeOnly(libs.mysql.connector)
    runtimeOnly(libs.slf4j.simple)
}

application {
    applicationName = "login-server"
    mainClass = "app.l2nx.login.LoginServer"
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dorg.slf4j.simpleLogger.log.com.zaxxer.hikari=warn",
        "-XX:+UseZGC",
        "-Xms128m",
        "-Xmx256m",
    )
}

val tools = mapOf(
    "account-manager" to "app.l2nx.login.tools.AccountManager",
    "game-server-register" to "app.l2nx.login.tools.GameServerRegister",
)

for ((name, main) in tools) {
    val scripts = tasks.register<CreateStartScripts>("${name}StartScripts") {
        applicationName = name
        mainClass = main
        classpath = tasks.startScripts.get().classpath
        outputDir = layout.buildDirectory.dir("tool-scripts/$name").get().asFile
        defaultJvmOpts = listOf("-Djava.util.logging.config.file=console.cfg")
    }
    distributions.main {
        contents {
            from(scripts) {
                into("bin")
            }
        }
    }
}
