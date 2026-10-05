import co.sirdab.driver.buildsrc.BuildConfig
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The Play upload key, kept out of the repository: `keystore.properties` at the project root is
// gitignored, and so is the keystore it points at. See `keystore.properties.example`.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val hasUploadKey = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = BuildConfig.BASE_NAMESPACE

    defaultConfig {
        applicationId = "co.sirdab.driver"
        compileSdk = BuildConfig.COMPILE_SDK
        targetSdk = BuildConfig.TARGET_SDK
        minSdk = BuildConfig.MIN_SDK
        // Every upload to either store needs a higher build number than the last, so CI can pass
        // `-Pdriver.versionCode=<n>` rather than editing BuildConfig for each one.
        versionCode = findProperty("driver.versionCode")?.toString()?.toInt() ?: BuildConfig.VERSION_CODE
        versionName = BuildConfig.VERSION_NAME

        // Set in gradle.properties, overridable per machine in ~/.gradle/gradle.properties or with -P
        // (local.properties is not read by Gradle).
        buildConfigField("String", "TMS_API_BASE_URL", "\"${property("driver.apiBaseUrl")}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${property("driver.supabaseUrl")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${property("driver.supabaseAnonKey")}\"")
    }

    signingConfigs {
        if (hasUploadKey) {
            create("upload") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
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

    // Whether the API conversation goes to logcat. A property rather than `BuildConfig.DEBUG` so a
    // release build handed to someone testing can be read too, but a separate one for release and
    // off unless set: the log carries phone numbers and document metadata, and a store build must
    // not write them where any app with log access could read them.
    buildTypes {
        debug {
            buildConfigField("boolean", "HTTP_LOG", "${property("driver.httpLog")}")
        }
        release {
            buildConfigField("boolean", "HTTP_LOG", "${findProperty("driver.httpLog.release") ?: false}")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Without the upload key a release build still installs for testing, signed with the
            // debug key. Play refuses a debug-signed bundle, so this cannot reach the store by
            // accident.
            signingConfig = if (hasUploadKey) {
                signingConfigs.getByName("upload")
            } else {
                logger.warn("keystore.properties not found: release is signed with the debug key and cannot be uploaded to Play.")
                signingConfigs.getByName("debug")
            }
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
