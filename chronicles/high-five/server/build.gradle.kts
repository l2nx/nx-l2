plugins {
    id("nx-l2.java-conventions")
}

dependencies {
    api(project(":libs:commons"))
    implementation(libs.classgraph)
}
