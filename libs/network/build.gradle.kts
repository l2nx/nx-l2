plugins {
    id("nx-l2.java-conventions")
    `java-test-fixtures`
    alias(libs.plugins.jmh)
}

dependencies {
    api(libs.netty.handler)
    api(libs.netty.codec.base)
    api(libs.micrometer.core)
    api(libs.spring.boot)
    implementation(libs.netty.transport.classes.epoll)
    implementation(libs.netty.transport.classes.io.uring)
    runtimeOnly(variantOf(libs.netty.transport.native.epoll) { classifier("linux-x86_64") })
    runtimeOnly(variantOf(libs.netty.transport.native.epoll) { classifier("linux-aarch_64") })
    runtimeOnly(variantOf(libs.netty.transport.native.io.uring) { classifier("linux-x86_64") })
    runtimeOnly(variantOf(libs.netty.transport.native.io.uring) { classifier("linux-aarch_64") })
    implementation(libs.jctools.core)
    implementation(libs.bucket4j.core)
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.spring.boot.starter.validation)
    annotationProcessor(platform(libs.spring.boot.dependencies))
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)

    testFixturesImplementation(platform(libs.spring.boot.dependencies))
    testFixturesApi(libs.netty.handler)
    testFixturesApi(platform(libs.junit.bom))
    testFixturesApi(libs.junit.jupiter)
    testFixturesImplementation(libs.junit.platform.launcher)
    testFixturesImplementation(libs.micrometer.core)
}

jmh {
    includes.set(providers.gradleProperty("jmh.includes").map { listOf(it) }.orElse(listOf(".*")))
}
