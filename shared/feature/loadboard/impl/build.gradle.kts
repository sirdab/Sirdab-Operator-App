plugins {
    id("driver.kmp.feature.impl")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.shared.feature.loadboard.api)
            implementation(projects.shared.core.model)
            implementation(projects.shared.core.util)
            implementation(projects.shared.feature.notifications.api)
            implementation(projects.shared.core.ui)
            implementation(projects.shared.core.demo)
            implementation(projects.shared.core.platform)
            implementation(projects.shared.core.preferences)
            implementation(projects.shared.core.navigation)
            implementation(libs.kotlinx.datetime)
        }
    }
}
