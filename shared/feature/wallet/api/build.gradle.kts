plugins {
    id("driver.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.core.model)
            api(projects.shared.core.navigation)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization)
        }
    }
}
