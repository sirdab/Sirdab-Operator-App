import co.sirdab.driver.buildsrc.BuildConfig
import co.sirdab.driver.buildsrc.bundle
import co.sirdab.driver.buildsrc.deriveNamespace
import co.sirdab.driver.buildsrc.libs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("dev.mokkery")
    id("org.jetbrains.kotlin.plugin.allopen")
    id("org.jetbrains.kotlinx.kover")
}

kotlin {
    android {
        namespace = project.deriveNamespace()
        compileSdk = BuildConfig.COMPILE_SDK
        minSdk = BuildConfig.MIN_SDK

        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(BuildConfig.JVM_TARGET))
            freeCompilerArgs.addAll("-Xjvm-default=all")
        }

        androidResources {
            enable = true
        }

        withHostTestBuilder {}.configure {
            isReturnDefaultValues = true
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets.all {
        languageSettings.optIn("kotlin.experimental.ExperimentalObjCName")
        languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
    }

    sourceSets.getByName("commonTest") {
        dependencies {
            implementation(project.libs.bundle("testing-common"))
        }
    }
}

tasks.register("testClasses")

// allOpen configuration for testing
val isTesting = gradle.startParameter.taskNames.any { taskName ->
    taskName.contains("test", true)
            || taskName.contains("kover", true)
            || taskName.contains("build", true)
}

if (isTesting) {
    allOpen {
        annotation("${BuildConfig.BASE_NAMESPACE}.shared.core.util.testing.OpenForTesting")
    }
}

// Kover configuration
kover {
    reports {
        total {
            filters {
                includes {
                    classes("*UseCase")
                    classes("*ViewModel")
                }
            }
            html {
                onCheck.set(true)
            }
        }
    }
}
