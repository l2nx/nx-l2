plugins {
    id("nx-l2.java-conventions")
}

dependencies {
    api(libs.netty.handler)
    api(libs.hdrhistogram)
    api(libs.jackson.databind)
    implementation(libs.fastutil)
}
