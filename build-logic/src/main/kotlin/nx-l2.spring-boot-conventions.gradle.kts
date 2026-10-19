plugins {
    id("nx-l2.java-conventions")
    application
}

// `run` starts in the installed distribution (the server resolves ./config, ./data, ./log against the working dir);
// installDist is a Sync, so server-written and operator-added files must be preserved.
tasks.named<Sync>("installDist") {
    preserve {
        include("log/**", "data/geodata/**")
    }
}

tasks.named<JavaExec>("run") {
    val install = tasks.named<Sync>("installDist")
    dependsOn(install)
    workingDir = install.get().destinationDir
    standardInput = System.`in`
}
