plugins {
    id("nx-l2.java-conventions")
}

dependencies {
    api(project(":libs:boot"))
    api(project(":libs:commons"))
    implementation(libs.classgraph)
}

// The engine reads ./config and ./data against the working directory.
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    workingDir = layout.projectDirectory.dir("../datapack").asFile
}
