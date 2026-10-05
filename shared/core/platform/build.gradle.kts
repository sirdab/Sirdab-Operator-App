plugins {
    id("driver.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
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
