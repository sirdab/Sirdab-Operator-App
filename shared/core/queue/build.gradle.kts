plugins {
    id("driver.kmp.library")
    id("com.google.devtools.ksp")
    alias(libs.plugins.room)
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.shared.core.network)
            api(projects.shared.core.platform)
            implementation(projects.shared.core.model)
            implementation(libs.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization)
            implementation(libs.bundles.koin.common)
        }

        androidMain.dependencies {
            implementation(libs.koin.android)
        }

        iosMain.dependencies {
            implementation(libs.koin.core)
        }

        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.bundles.ktor.common)
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}
