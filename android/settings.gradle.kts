pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "or-yareach"

include(":app")

include(":core:model")
include(":core:common")
include(":core:crypto")
include(":core:database")
include(":core:ui")
include(":core:sync")
include(":core:network")
include(":core:security")
include(":core:update")
include(":core:domain")
include(":core:scanner")
include(":core:settings")
include(":core:calendar")

include(":feature:auth")
include(":feature:pairing")
include(":feature:tasks")
include(":feature:cycle")
include(":feature:update")
include(":feature:shopping")
include(":feature:dates")
include(":feature:home")
include(":feature:folders")
include(":feature:settings")
include(":feature:search")
include(":feature:calendar")
include(":feature:conflicts")
