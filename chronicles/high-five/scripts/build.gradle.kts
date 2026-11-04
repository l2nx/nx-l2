plugins {
    id("nx-l2.java-conventions")
    id("nx-l2.single-writer-conventions")
}

dependencies {
    implementation(project(":chronicles:high-five:server"))
    implementation(project(":engine:mail"))
    implementation(libs.commons.lang3)
}
