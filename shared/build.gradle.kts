plugins {
    id("driver.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Core
            api(projects.shared.core.model)
            api(projects.shared.core.util)
            api(projects.shared.core.platform)
            api(projects.shared.core.navigation)
            api(projects.shared.core.preferences)
            api(projects.shared.core.ui)
            api(projects.shared.core.demo)
            api(projects.shared.core.network)
            api(projects.shared.core.queue)
            api(projects.shared.core.auth)

            // Feature impls (transitively expose their api modules)
            api(projects.shared.feature.onboarding.impl)
            api(projects.shared.feature.bidding.impl)
            api(projects.shared.feature.trip.impl)
            api(projects.shared.feature.profile.impl)
            api(projects.shared.feature.notifications.impl)

            implementation(libs.bundles.koin.common)
            implementation(libs.kotlinx.serialization)
            api(libs.ktor.client.logging)
        }

        androidMain.dependencies {
            implementation(libs.koin.android)
        }

        iosMain.dependencies {
            implementation(libs.koin.core)
        }
    }
}
