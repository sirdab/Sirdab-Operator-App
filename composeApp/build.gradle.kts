import co.sirdab.driver.buildsrc.BuildConfig
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    android {
        namespace = "${BuildConfig.BASE_NAMESPACE}.app"
        compileSdk = BuildConfig.COMPILE_SDK
        minSdk = BuildConfig.MIN_SDK

        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(BuildConfig.JVM_TARGET))
        }

        androidResources {
            enable = true
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            export(projects.shared)
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
        }

        commonMain.dependencies {
            api(projects.shared)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.ui)
            implementation(libs.compose.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.bundles.navigation3)

            implementation(libs.coil.compose)

            implementation(libs.bundles.koin.common)
        }

        iosMain.dependencies {
            implementation(libs.koin.core)
        }
    }
}

compose.resources {
    packageOfResClass = "${BuildConfig.BASE_NAMESPACE}.composeapp.generated.resources"
}
