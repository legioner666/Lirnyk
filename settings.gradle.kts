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

// TASK 003: private Spotify App Remote prototype (app.lirnyk.remote). Separate app; not part of the main Lirnyk APK.
include(":spotify:model", ":spotify:app")
