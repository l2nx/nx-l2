plugins {
    id("nx-l2.java-conventions")
    `java-test-fixtures`
}

dependencies {
    api(libs.spring.boot)
    api(libs.micrometer.core)

    testFixturesImplementation(platform(libs.spring.boot.dependencies))
    testFixturesImplementation(libs.spring.boot.starter.test)
    testFixturesImplementation(libs.spring.boot.testcontainers)
    testFixturesImplementation(libs.testcontainers.junit.jupiter)
    testFixturesImplementation(libs.testcontainers.mysql)
}
