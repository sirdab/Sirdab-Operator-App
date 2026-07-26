enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
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

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MyApplication"
include(":androidApp")
include(":composeApp")

// Shared
include(":shared")

// Core
include(":shared:core:model")
include(":shared:core:util")
include(":shared:core:platform")
include(":shared:core:navigation")
include(":shared:core:preferences")
include(":shared:core:ui")
include(":shared:core:demo")

// Onboarding
include(":shared:feature:onboarding:api")
include(":shared:feature:onboarding:impl")

// Loadboard
include(":shared:feature:loadboard:api")
include(":shared:feature:loadboard:impl")

// Bidding
include(":shared:feature:bidding:api")
include(":shared:feature:bidding:impl")

// Trip
include(":shared:feature:trip:api")
include(":shared:feature:trip:impl")

// Wallet
include(":shared:feature:wallet:api")
include(":shared:feature:wallet:impl")

// Profile
include(":shared:feature:profile:api")
include(":shared:feature:profile:impl")

// Notifications
include(":shared:feature:notifications:api")
include(":shared:feature:notifications:impl")
