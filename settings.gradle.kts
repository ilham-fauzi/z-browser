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

rootProject.name = "browser-z"

include(
    ":app",
    ":core:domain",
    ":core:data",
    ":core:webview",
    ":core:ui",
    ":engine:adblock",
    ":feature:browser",
    ":feature:search",
    ":feature:library",
    ":feature:settings",
    ":feature:filters",
    ":baselineprofile",
)
