plugins {
    id("driver.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.shared.core.network)
            implementation(projects.shared.core.model)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization)
            implementation(libs.bundles.ktor.common)
            implementation(libs.bundles.koin.common)
        }

        androidMain.dependencies {
            implementation(libs.koin.android)
        }

        iosMain.dependencies {
            implementation(libs.koin.core)
        }
    }
}
