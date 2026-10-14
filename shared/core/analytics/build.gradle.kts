plugins {
    id("driver.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.core.model)
        }
    }
}
