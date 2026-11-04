plugins {
    id("nx-l2.java-conventions")
    id("nx-l2.single-writer-conventions")
    `java-test-fixtures`
    alias(libs.plugins.jmh)
}

dependencies {
    api(project(":libs:boot"))
    api(project(":libs:network"))
    api(project(":protocols:game:p267"))
    implementation(project(":libs:database"))
    implementation(project(":libs:gslink"))
    implementation(project(":libs:logging"))
    api(project(":engine:core"))
    implementation(project(":engine:mail"))
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

    testFixturesImplementation(platform(libs.spring.boot.dependencies))

    jmhImplementation(platform(libs.spring.boot.dependencies))
    jmhImplementation(testFixtures(project(":libs:network")))
    jmhImplementation(testFixtures(project(":engine:core")))
    jmhImplementation(libs.mockito.core)
    jmhImplementation(testFixtures(project))
}

// The engine reads ./config and ./data against the working directory.
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    workingDir = layout.projectDirectory.dir("../datapack").asFile
}

jmh {
    includes.set(providers.gradleProperty("jmh.includes").map { listOf(it) }.orElse(listOf(".*")))
    // The benchmark jar bundles the whole server classpath, past the 65535 entries of a plain zip.
    zip64 = true
}
