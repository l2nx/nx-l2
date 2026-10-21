plugins {
    id("nx-l2.java-conventions")
    alias(libs.plugins.protobuf)
}

dependencies {
    api(libs.netty.handler)
    api(libs.hdrhistogram)
    api(libs.jackson.databind)
    implementation(libs.fastutil)
    implementation(libs.protobuf.java)
}

protobuf {
    protoc {
        artifact = libs.protoc.asProvider().get().toString()
    }
}
