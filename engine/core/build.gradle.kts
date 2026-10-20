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
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.agrona)
    implementation(libs.fastutil)
    implementation(libs.jctools.core)

    testImplementation(libs.spring.boot.starter.test)

    testFixturesImplementation(platform(libs.spring.boot.dependencies))

    jmhImplementation(testFixtures(project))
}

tasks.test {
    // The inbox must never fall back to sun.misc.Unsafe (JEP 471/498): any use fails the tests here.
    jvmArgs("--sun-misc-unsafe-memory-access=deny")
}
