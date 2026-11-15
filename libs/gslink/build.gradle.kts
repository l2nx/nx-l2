plugins {
    id("nx-l2.java-conventions")
    `java-test-fixtures`
    alias(libs.plugins.protobuf)
}

dependencies {
    api(libs.grpc.protobuf)
    api(libs.grpc.stub)
    api(libs.protobuf.java)
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.spring.boot.starter.validation)
    // Each server brings its side through its starter; the auto-configuration activates the side it finds.
    compileOnly(platform(libs.spring.boot.dependencies))
    compileOnly(libs.spring.boot.grpc.server)
    compileOnly(libs.spring.boot.grpc.client)
    annotationProcessor(platform(libs.spring.boot.dependencies))
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.grpc.server)
    testImplementation(libs.spring.boot.starter.grpc.client)
    testImplementation(libs.grpc.inprocess)
    testImplementation(libs.netty.pkitesting)

    testFixturesImplementation(platform(libs.spring.boot.dependencies))
    testFixturesApi(libs.grpc.stub)
}

// protoc and its grpc plugin generate code for the protobuf-java and grpc-java runtime the Spring Boot BOM pins, so
// they take their versions from that BOM: a Boot update moves them, and no version is kept equal by hand.
fun bomVersionOf(library: Provider<MinimalExternalModuleDependency>): String {
    val module = library.get().module
    val graph = configurations.detachedConfiguration(
        dependencies.platform(libs.spring.boot.dependencies.get()),
        dependencies.create(module.toString()),
    )
    return graph.incoming.resolutionResult.allComponents
        .mapNotNull { it.moduleVersion }
        .single { it.group == module.group && it.name == module.name }
        .version
}

protobuf {
    protoc {
        artifact = "${libs.protoc.asProvider().get().module}:${bomVersionOf(libs.protobuf.java)}"
    }
    plugins {
        create("grpc") {
            artifact = "${libs.protoc.gen.grpc.java.get().module}:${bomVersionOf(libs.grpc.stub)}"
        }
    }
    generateProtoTasks {
        all().configureEach {
            plugins {
                // The default @javax.annotation.Generated would need javax.annotation-api for nothing.
                create("grpc") {
                    option("@generated=omit")
                }
            }
        }
    }
}
