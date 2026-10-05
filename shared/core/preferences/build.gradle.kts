plugins {
    id("driver.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.core.platform)
            implementation(libs.datastore.preferences.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.bundles.koin.common)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
        }

        iosMain.dependencies {
            implementation(libs.koin.core)
        }
    }
}
