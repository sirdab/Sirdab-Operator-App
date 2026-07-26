import co.sirdab.driver.buildsrc.deriveNamespace

plugins {
    id("driver.kmp.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

compose.resources {
    packageOfResClass = "${project.deriveNamespace()}.generated.resources"
}
