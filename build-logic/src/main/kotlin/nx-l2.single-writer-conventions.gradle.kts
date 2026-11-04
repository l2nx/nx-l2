plugins {
    pmd
}

val libs = versionCatalogs.named("libs")

pmd {
    toolVersion = libs.findVersion("pmd").get().requiredVersion
    ruleSets = listOf()
    ruleSetFiles = files(rootProject.file("config/pmd/single-writer.xml"))
    isConsoleOutput = true
}

tasks.withType<Pmd>().configureEach {
    enabled = name == "pmdMain"
}
