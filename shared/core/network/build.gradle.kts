plugins {
    id("driver.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.core.model)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization)
            implementation(libs.kotlinx.datetime)
            implementation(libs.bundles.ktor.common)
            implementation(libs.bundles.koin.common)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.koin.core)
        }

        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.bundles.ktor.common)
        }
    }
}
