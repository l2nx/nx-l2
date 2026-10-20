plugins {
    id("nx-l2.java-conventions")
    application
    id("org.springframework.boot")
    id("com.gorylenko.gradle-git-properties")
}

val libs = versionCatalogs.named("libs")

configurations.configureEach {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
}

dependencies {
    implementation(libs.findLibrary("spring-boot-starter-actuator").get())
    implementation(libs.findLibrary("spring-boot-starter-jdbc").get())
    implementation(libs.findLibrary("spring-boot-starter-liquibase").get())
    implementation(libs.findLibrary("liquibase-core").get())
    implementation(libs.findLibrary("spring-boot-starter-log4j2").get())
    implementation(libs.findLibrary("spring-boot-starter-webmvc").get())
    runtimeOnly(libs.findLibrary("disruptor").get())
    runtimeOnly(libs.findLibrary("micrometer-registry-prometheus").get())
    runtimeOnly(libs.findLibrary("postgresql").get())

    testImplementation(libs.findLibrary("spring-boot-starter-test").get())
    testImplementation(testFixtures(project(":libs:boot")))
}

springBoot {
    buildInfo()
}

// A source archive without .git must still build; info then has no git section.
gitProperties {
    failOnNoGitDirectory = false
}

val jvmFlags = listOf(
    "-Dfile.encoding=UTF-8",
    "-Djava.awt.headless=true",
)

application {
    applicationDefaultJvmArgs = jvmFlags
}

// The distribution runs the plain jar from lib/; the fat jar and its distribution are not built.
tasks.named("bootJar") {
    enabled = false
}
tasks.named<Jar>("jar") {
    archiveClassifier = ""
}
listOf("bootStartScripts", "bootDistZip", "bootDistTar", "installBootDist", "bootRun").forEach {
    tasks.named(it) {
        enabled = false
    }
}

// `run` starts in the installed distribution (the server resolves ./config, ./data, ./log against the working dir);
// installDist is a Sync, so server-written and operator-added files must be preserved.
val installDist = tasks.named<Sync>("installDist") {
    preserve {
        include("log/**", "data/geodata/**")
    }
}

tasks.named<JavaExec>("run") {
    dependsOn(installDist)
    workingDir = installDist.get().destinationDir
    standardInput = System.`in`
}

// The tests run in a copy, so what a test run writes (logs, config/hexid.txt) never reaches the distribution or the image.
val testDistribution = tasks.register<Sync>("testDistribution") {
    from(installDist) {
        exclude("data/geodata/**", "log/**")
    }
    into(layout.buildDirectory.dir("test-distribution"))
}

tasks.withType<Test>().configureEach {
    dependsOn(testDistribution)
    workingDir = testDistribution.get().destinationDir
    jvmArgs(jvmFlags)
    maxHeapSize = "2g"
    // Pausing a cached test context stops and restarts its lifecycle beans; the servers hold static state and start once per JVM.
    systemProperty("spring.test.context.cache.pause", "never")
}
