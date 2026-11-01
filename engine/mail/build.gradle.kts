plugins {
    id("nx-l2.java-conventions")
}

dependencies {
    api(project(":engine:core"))
    api(libs.fastutil)
    implementation(project(":libs:database"))
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.spring.modulith.api)
    implementation(libs.bucket4j.core)
    implementation(libs.caffeine)

    testImplementation(testFixtures(project(":engine:core")))
    testImplementation(testFixtures(project(":libs:database")))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.jdbc)
    testImplementation(libs.liquibase.core)
    testRuntimeOnly(libs.postgresql)
}
