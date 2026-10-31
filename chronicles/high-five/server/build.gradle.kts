plugins {
    id("nx-l2.java-conventions")
    pmd
    alias(libs.plugins.jmh)
}

dependencies {
    api(project(":libs:boot"))
    api(project(":libs:network"))
    implementation(project(":libs:database"))
    implementation(project(":libs:gslink"))
    implementation(project(":libs:logging"))
    api(project(":engine:core"))
    implementation(libs.bucket4j.core)
    implementation(libs.classgraph)
    implementation(libs.commons.lang3)
    implementation(libs.datasource.proxy)
    implementation(libs.fastutil)
    implementation(libs.lib.recur)
    implementation(libs.micrometer.core)
    implementation(libs.spring.modulith.api)
    implementation(libs.spring.boot.starter.jdbc)
    testImplementation(testFixtures(project(":engine:core")))
    testImplementation(testFixtures(project(":libs:database")))
    testImplementation(testFixtures(project(":libs:network")))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(testFixtures(project(":libs:gslink")))
    testImplementation(libs.grpc.inprocess)
    testImplementation(libs.liquibase.core)
    testRuntimeOnly(libs.postgresql)

    jmhImplementation(testFixtures(project(":engine:core")))
    jmhImplementation(platform(libs.spring.boot.dependencies))
    jmhImplementation(libs.mockito.core)
}

// The engine reads ./config and ./data against the working directory.
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    workingDir = layout.projectDirectory.dir("../datapack").asFile
}

// Only the single-writer ruleset runs: it covers synchronized blocks, which ArchUnit cannot see. Tests and
// benchmarks may lock freely.
pmd {
    toolVersion = libs.versions.pmd.get()
    ruleSets = listOf()
    ruleSetFiles = files(rootProject.file("config/pmd/single-writer.xml"))
    isConsoleOutput = true
}

tasks.named("pmdTest") {
    enabled = false
}

tasks.named("pmdJmh") {
    enabled = false
}

jmh {
    includes.set(providers.gradleProperty("jmh.includes").map { listOf(it) }.orElse(listOf(".*")))
}
