plugins {
    id("nx-l2.java-conventions")
    alias(libs.plugins.jmh)
}

dependencies {
    api(project(":libs:boot"))
    api(project(":libs:network"))
    api(project(":protocols:login:c621"))
    implementation(project(":libs:database"))
    implementation(project(":libs:gslink"))
    implementation(libs.spring.boot.starter.grpc.server)
    implementation(libs.caffeine)
    implementation(libs.commons.lang3)
    implementation(libs.micrometer.core)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.security.crypto)
    implementation(libs.uuid.creator)
    runtimeOnly(libs.bcprov)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(testFixtures(project(":libs:database")))
    testImplementation(testFixtures(project(":libs:network")))
    testImplementation(testFixtures(project(":libs:gslink")))
    testImplementation(libs.grpc.inprocess)
    testImplementation(libs.liquibase.core)
    testRuntimeOnly(libs.postgresql)
}

jmh {
    includes.set(providers.gradleProperty("jmh.includes").map { listOf(it) }.orElse(listOf(".*")))
}
