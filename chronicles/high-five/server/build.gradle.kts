plugins {
    id("nx-l2.java-conventions")
}

dependencies {
    api(project(":libs:boot"))
    api(project(":libs:network"))
    implementation(libs.classgraph)
    implementation(libs.commons.lang3)
    implementation(libs.lib.recur)
    implementation(libs.micrometer.core)
}

// The engine reads ./config and ./data against the working directory.
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    workingDir = layout.projectDirectory.dir("../datapack").asFile
}
