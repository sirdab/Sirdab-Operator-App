package co.sirdab.driver.buildsrc

import org.gradle.api.JavaVersion

object BuildConfig {
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 37
    const val MIN_SDK = 28
    const val MAJOR_VERSION = 1
    const val MINOR_VERSION = 0
    const val PATCH_VERSION = 0
    const val VERSION_NAME = "$MAJOR_VERSION.$MINOR_VERSION.$PATCH_VERSION"
    const val VERSION_CODE = 1
    val JAVA_VERSION = JavaVersion.VERSION_17
    const val JVM_TARGET = "17"
    const val BASE_NAMESPACE = "co.sirdab.driver"
    const val IOS_FRAMEWORK_NAME = "Shared"
}
