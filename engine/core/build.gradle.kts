plugins {
    id("nx-l2.java-conventions")
    `java-test-fixtures`
}

dependencies {
    api(libs.uuid.creator)
    implementation(libs.jctools.core)
}
