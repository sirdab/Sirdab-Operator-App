import java.io.File

plugins {
    id("driver.kmp.compose")
}

compose.resources {
    publicResClass = true
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.core.util)
            implementation(projects.shared.core.platform)
            implementation(projects.shared.core.model)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization)
            implementation(libs.kotlinx.datetime)
            implementation(libs.bundles.koin.common)
            implementation(libs.coil.compose)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
        }

        iosMain.dependencies {
            implementation(libs.koin.core)
        }
    }
}

/**
 * Every locale carries every string, with the same placeholders.
 *
 * Compose resources resolve one key at a time and fall back to the default silently, so a locale
 * that is missing half its keys still builds and still runs — it just renders those lines in
 * English. That is not a thing anyone notices in review, and it is exactly how the profile screen
 * ended up in English for a driver who had chosen Urdu.
 */
val checkTranslations by tasks.registering {
    val resources = layout.projectDirectory.dir("src/commonMain/composeResources")
    inputs.dir(resources).withPropertyName("composeResources")

    doLast {
        val strings = { file: File ->
            Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(file.readText())
                .associate { it.groupValues[1] to it.groupValues[2] }
        }
        val placeholders = { value: String -> Regex("""%\d\$[a-z]""").findAll(value).map { it.value }.toSortedSet() }

        val default = strings(resources.file("values/strings.xml").asFile)
        val problems = mutableListOf<String>()

        resources.asFile.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values-") }
            .sortedBy { it.name }
            .forEach { dir ->
                val file = File(dir, "strings.xml")
                if (!file.exists()) return@forEach
                val translated = strings(file)

                (default.keys - translated.keys).sorted().let {
                    if (it.isNotEmpty()) problems += "${dir.name} is missing ${it.size}: ${it.joinToString()}"
                }
                (translated.keys - default.keys).sorted().let {
                    if (it.isNotEmpty()) problems += "${dir.name} has ${it.size} the default no longer has: ${it.joinToString()}"
                }
                translated
                    .filterKeys { it in default }
                    .filter { (key, value) -> placeholders(value) != placeholders(default.getValue(key)) }
                    .keys
                    .sorted()
                    .let {
                        if (it.isNotEmpty()) {
                            problems += "${dir.name} changes the placeholders of: ${it.joinToString()}"
                        }
                    }
            }

        if (problems.isNotEmpty()) {
            error("Translations are out of step with values/strings.xml:\n" + problems.joinToString("\n"))
        }
    }
}

tasks.named("check") { dependsOn(checkTranslations) }
