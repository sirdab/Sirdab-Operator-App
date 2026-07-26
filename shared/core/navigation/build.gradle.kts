plugins {
    id("driver.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.core.util)
            api(libs.navigation3)
            implementation(libs.compose.runtime)
            implementation(libs.kotlinx.serialization)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.bundles.koin.common)
        }
    }
}
