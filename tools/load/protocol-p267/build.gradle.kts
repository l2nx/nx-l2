plugins {
    id("nx-l2.java-conventions")
}

dependencies {
    api(project(":tools:load:core"))
    implementation(project(":protocols:login:c621"))
    implementation(project(":libs:network"))

    testImplementation(testFixtures(project(":libs:network")))
}
