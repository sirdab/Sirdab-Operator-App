package co.sirdab.driver.buildsrc

import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider

fun Project.deriveNamespace(): String {
    val pathSegment = path
        .removePrefix(":")
        .replace(":", ".")
    return "${BuildConfig.BASE_NAMESPACE}.$pathSegment"
}

/** Access the version catalog named "libs" from convention plugins. */
val Project.libs: VersionCatalog
    get() = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).get()

fun VersionCatalog.bundle(alias: String): Provider<ExternalModuleDependencyBundle> =
    findBundle(alias).get()
