plugins {
    id("nx-l2.java-conventions")
    `java-test-fixtures`
    alias(libs.plugins.jmh)
}

dependencies {
    api(project(":libs:boot"))
    api(libs.spring.boot)
    api(libs.micrometer.core)
    api(libs.uuid.creator)
    api(project(":libs:network"))
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.bucket4j.core)
    implementation(libs.fastutil)
    implementation(libs.jctools.core)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.jqwik)

    testFixturesImplementation(platform(libs.spring.boot.dependencies))

    jmhImplementation(testFixtures(project))
}

tasks.test {
    // The inbox must never fall back to sun.misc.Unsafe (JEP 471/498): any use fails the tests here.
    jvmArgs("--sun-misc-unsafe-memory-access=deny")
    // jqwik keeps its failure database in the working directory by default; the build directory keeps it out of git.
    systemProperty("jqwik.database", layout.buildDirectory.file("jqwik-database").get().asFile.path)
}

jmh {
    includes.set(providers.gradleProperty("jmh.includes").map { listOf(it) }.orElse(listOf(".*")))
}
