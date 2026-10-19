plugins {
    id("nx-l2.java-conventions")
}

dependencies {
    api(project(":libs:boot"))
    api(project(":libs:network"))
    implementation(libs.commons.lang3)
    implementation(libs.micrometer.core)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.validation)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.mysql)
    testRuntimeOnly(libs.mysql.connector)
}

val loginSql = layout.projectDirectory.dir("sql")

tasks.withType<Test>().configureEach {
    inputs.dir(loginSql).withPropertyName("loginSql").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("l2nx.test.login-sql-dir", loginSql.asFile.absolutePath)
}
