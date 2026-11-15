plugins {
    id("nx-l2.java-conventions")
    `java-test-fixtures`
}

dependencies {
    implementation(libs.spring.boot.autoconfigure)
    compileOnly(libs.spring.boot.starter.jdbc)
    compileOnly(libs.liquibase.core)

    testImplementation(testFixtures(project))
    testImplementation(libs.spring.boot.starter.jdbc)
    testImplementation(libs.liquibase.core)

    testFixturesImplementation(platform(libs.spring.boot.dependencies))
    testFixturesApi(libs.testcontainers.postgresql)
    testFixturesApi(libs.hikaricp)
    testFixturesImplementation(libs.spring.boot.starter.jdbc)
    testFixturesImplementation(libs.liquibase.core)
    testFixturesImplementation(libs.postgresql)
}
