plugins {
    id("nx-l2.java-conventions")
    alias(libs.plugins.jmh)
}

dependencies {
    api(libs.micrometer.core)
}
