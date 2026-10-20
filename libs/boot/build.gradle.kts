plugins {
    id("nx-l2.java-conventions")
    `java-test-fixtures`
}

dependencies {
    api(libs.spring.boot)
    implementation(libs.spring.boot.autoconfigure)

    testImplementation(libs.spring.boot.starter.test)

    testFixturesImplementation(platform(libs.spring.boot.dependencies))
    testFixturesImplementation(libs.spring.boot.starter.test)
    testFixturesImplementation(libs.spring.boot.testcontainers)
    testFixturesImplementation(testFixtures(project(":libs:database")))
    testFixturesApi(libs.testcontainers.junit.jupiter)
    testFixturesApi(libs.archunit.junit5)
}
