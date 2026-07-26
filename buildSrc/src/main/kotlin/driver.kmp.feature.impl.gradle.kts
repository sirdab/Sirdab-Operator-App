import co.sirdab.driver.buildsrc.bundle
import co.sirdab.driver.buildsrc.library
import co.sirdab.driver.buildsrc.libs

plugins {
    id("driver.kmp.compose")
}

kotlin {
    sourceSets.getByName("commonMain") {
        dependencies {
            // Compose
            implementation(project.libs.library("compose-runtime"))
            implementation(project.libs.library("compose-foundation"))
            implementation(project.libs.library("compose-material3"))
            implementation(project.libs.library("compose-material-icons-extended"))
            implementation(project.libs.library("compose-ui"))
            implementation(project.libs.library("compose-resources"))
            implementation(project.libs.library("compose-uiToolingPreview"))

            // Navigation 3
            implementation(project.libs.bundle("navigation3"))

            // Lifecycle
            implementation(project.libs.library("androidx-lifecycle-viewmodel-compose"))
            implementation(project.libs.library("androidx-lifecycle-runtime-compose"))

            // Coroutines
            implementation(project.libs.library("kotlinx-coroutines-core"))

            // Kotlinx Serialization
            implementation(project.libs.library("kotlinx-serialization"))

            // Koin
            implementation(project.libs.bundle("koin-common"))

            // Observable ViewModel
            api(project.libs.library("kmp-observableviewmodel"))

            // Coil
            implementation(project.libs.library("coil-compose"))
        }
    }

    sourceSets.getByName("androidMain") {
        dependencies {
            implementation(project.libs.library("kotlinx-coroutines-android"))
            implementation(project.libs.library("koin-android"))
            implementation(project.libs.library("koin-android-compose"))
        }
    }

    sourceSets.findByName("iosMain")?.dependencies {
        implementation(project.libs.library("koin-core"))
    }
}
