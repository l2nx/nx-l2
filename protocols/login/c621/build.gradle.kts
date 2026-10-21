plugins {
    id("nx-l2.java-conventions")
}

dependencies {
    api(project(":libs:network"))

    testImplementation(testFixtures(project(":libs:network")))
}
