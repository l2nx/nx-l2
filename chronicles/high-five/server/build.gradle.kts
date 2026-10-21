plugins {
    id("nx-l2.java-conventions")
}

dependencies {
    api(project(":libs:boot"))
    api(project(":libs:network"))
    implementation(project(":libs:database"))
    implementation(project(":libs:gslink"))
    api(project(":engine:core"))
    implementation(libs.bucket4j.core)
    implementation(libs.classgraph)
    implementation(libs.commons.lang3)
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
}

// The engine reads ./config and ./data against the working directory.
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    workingDir = layout.projectDirectory.dir("../datapack").asFile
}
