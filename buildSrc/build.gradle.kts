plugins {
    `kotlin-dsl`
    `kotlin-dsl-precompiled-script-plugins`
}

repositories {
    mavenCentral()
    google {
        mavenContent {
            includeGroupAndSubgroups("androidx")
            includeGroupAndSubgroups("com.android")
            includeGroupAndSubgroups("com.google")
        }
    }
    gradlePluginPortal()
}

dependencies {
    // Kotlin Gradle Plugin (provides kotlin-multiplatform, serialization, allopen, compose-compiler)
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin:kotlin-serialization:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin:kotlin-allopen:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${libs.versions.kotlin.get()}")

    // Android Gradle Plugin (provides com.android.kotlin.multiplatform.library)
    implementation("com.android.tools.build:gradle:${libs.versions.agp.get()}")

    // Compose Multiplatform
    implementation("org.jetbrains.compose:compose-gradle-plugin:${libs.versions.compose.multiplatform.get()}")

    // Mokkery
    implementation("dev.mokkery:mokkery-gradle:${libs.versions.mokkery.get()}")

    // Kover
    implementation("org.jetbrains.kotlinx:kover-gradle-plugin:${libs.versions.kover.get()}")

    // KSP
    implementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:${libs.versions.ksp.get()}")

    // Native Coroutines
    implementation("com.rickclephas.kmp:kmp-nativecoroutines-gradle-plugin:${libs.versions.native.coroutines.get()}")
}
