pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "nx-l2"

include(":libs:commons")
include(":login:server", ":login:boot")

// -Pchronicles=high-five,essence limits the build; the default is every directory under chronicles/.
val chronicles = providers.gradleProperty("chronicles").orNull?.split(',')?.map { it.trim() }
    ?: rootDir.resolve("chronicles").listFiles { file -> file.isDirectory }.orEmpty().map { it.name }.sorted()

for (chronicle in chronicles) {
    require(rootDir.resolve("chronicles/$chronicle").isDirectory) { "Unknown chronicle: $chronicle" }
    include(":chronicles:$chronicle:server", ":chronicles:$chronicle:scripts", ":chronicles:$chronicle:boot")
}
