import co.sirdab.driver.buildsrc.BuildConfig

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = BuildConfig.BASE_NAMESPACE

    defaultConfig {
        applicationId = "co.sirdab.driver"
        compileSdk = BuildConfig.COMPILE_SDK
        targetSdk = BuildConfig.TARGET_SDK
        minSdk = BuildConfig.MIN_SDK
        versionCode = BuildConfig.VERSION_CODE
        versionName = BuildConfig.VERSION_NAME

        // Set in gradle.properties, overridable per machine in local.properties. Demo mode is the
        // default so a fresh clone runs without a backend.
        buildConfigField("String", "DRIVER_BACKEND", "\"${property("driver.backend")}\"")
        buildConfigField("String", "TMS_API_BASE_URL", "\"${property("driver.apiBaseUrl")}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${property("driver.supabaseUrl")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${property("driver.supabaseAnonKey")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = BuildConfig.JAVA_VERSION
        targetCompatibility = BuildConfig.JAVA_VERSION
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation(projects.composeApp)

    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.android.compose)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
