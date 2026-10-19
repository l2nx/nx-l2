plugins {
    id("nx-l2.java-conventions")
    application
}

// `run` starts in the installed distribution (Mobius resolves ./config, ./data, ./log against the working dir);
// installDist is a Sync, so server-written and operator-added files must be preserved.
tasks.named<Sync>("installDist") {
    preserve {
        include("log/**", "backup/**", "data/geodata/**")
    }
}

// A build on Windows has no exec bit to copy, so the restart scripts get it here.
tasks.matching { it.name in setOf("installDist", "distTar", "distZip") }.configureEach {
    (this as AbstractCopyTask).filesMatching("**/*.sh") {
        permissions {
            unix("rwxr-xr-x")
        }
    }
}

tasks.named<JavaExec>("run") {
    val install = tasks.named<Sync>("installDist")
    dependsOn(install)
    workingDir = install.get().destinationDir
    standardInput = System.`in`
}
