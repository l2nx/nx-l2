plugins {
    id("nx-l2.java-conventions")
}

dependencies {
    implementation(project(":chronicles:high-five:server"))
    implementation(project(":engine:mail"))
    implementation(libs.commons.lang3)
}
