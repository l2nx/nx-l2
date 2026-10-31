plugins {
    id("nx-l2.java-conventions")
    application
    id("org.springframework.boot")
}

configurations.configureEach {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
}

dependencies {
    implementation(project(":tools:load:core"))
    implementation(project(":tools:load:protocol-p267"))
    implementation(libs.commons.lang3)
    implementation(libs.postgresql)
    implementation(libs.spring.boot)
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.spring.boot.starter.log4j2)
    implementation(libs.spring.boot.starter.restclient)
    implementation(libs.spring.boot.starter.validation)
    annotationProcessor(platform(libs.spring.boot.dependencies))
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(testFixtures(project(":libs:database")))
    testImplementation(libs.testcontainers)
}

application {
    applicationName = "load"
    mainClass = "app.l2nx.load.app.LoadApplication"
}
