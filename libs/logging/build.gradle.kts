plugins {
    id("nx-l2.java-conventions")
}

dependencies {
    implementation(libs.bucket4j.core)

    testImplementation(libs.spring.boot.starter.test)
}
