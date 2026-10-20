plugins {
    `java-library`
    id("com.diffplug.spotless")
}

val libs = versionCatalogs.named("libs")

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat(libs.findVersion("palantir").get().requiredVersion)
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// Module paths repeat their last segment (login:server, chronicles:high-five:server), so jar names carry the path.
base {
    archivesName = path.removePrefix(":").replace(':', '-')
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

dependencies {
    implementation(platform(libs.findLibrary("spring-boot-dependencies").get()))
    implementation(platform(libs.findLibrary("spring-modulith-bom").get()))
    implementation(libs.findLibrary("slf4j-api").get())
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testImplementation(libs.findLibrary("assertj-core").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

// Lombok is compile-time only and processes every source set, including testFixtures and jmh added by other plugins.
val lombok = libs.findLibrary("lombok").get()
sourceSets.configureEach {
    dependencies.add(compileOnlyConfigurationName, lombok)
    dependencies.add(annotationProcessorConfigurationName, lombok)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
    systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
    systemProperty("io.netty.leakDetection.level", "paranoid")
}
