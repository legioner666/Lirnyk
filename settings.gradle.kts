pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Lirnyk"

include(":app")
include(":core:model", ":core:media", ":core:ui")
include(":feature:player", ":feature:library", ":feature:settings", ":feature:diagnostics")
