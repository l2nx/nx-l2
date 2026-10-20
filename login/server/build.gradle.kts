plugins {
    id("nx-l2.java-conventions")
}

dependencies {
    api(project(":libs:boot"))
    api(project(":libs:network"))
    implementation(project(":libs:database"))
    implementation(libs.commons.lang3)
    implementation(libs.micrometer.core)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.uuid.creator)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(testFixtures(project(":libs:database")))
    testImplementation(testFixtures(project(":libs:network")))
    testImplementation(libs.liquibase.core)
    testRuntimeOnly(libs.postgresql)
}

