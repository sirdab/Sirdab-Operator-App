plugins {
    id("driver.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The module has no code of its own: it hands every feature api module the same
            // navigation3, so a route is a NavKey everywhere.
            api(libs.navigation3)
        }
    }
}
