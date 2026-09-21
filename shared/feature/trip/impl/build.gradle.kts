plugins {
    id("driver.kmp.feature.impl")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.shared.feature.trip.api)
            implementation(projects.shared.core.util)
            implementation(projects.shared.core.model)
            implementation(projects.shared.core.ui)
            implementation(projects.shared.core.demo)
            implementation(projects.shared.core.platform)
            implementation(projects.shared.core.preferences)
            implementation(projects.shared.core.navigation)
            implementation(projects.shared.core.network)
            implementation(projects.shared.core.queue)
            implementation(projects.shared.core.media)
            implementation(libs.kotlinx.datetime)
        }

        commonTest.dependencies {
            implementation(projects.shared.core.network)
            implementation(libs.ktor.client.mock)
            implementation(libs.bundles.ktor.common)
        }
    }
}
