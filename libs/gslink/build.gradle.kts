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

protobuf {
    protoc {
        artifact = libs.protoc.asProvider().get().toString()
    }
    plugins {
        create("grpc") {
            artifact = libs.protoc.gen.grpc.java.get().toString()
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
