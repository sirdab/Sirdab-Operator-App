package co.sirdab.driver.locale

/** Apply the chosen language to the platform so compose-resources resolves the right strings. */
expect fun applyPlatformLocale(languageCode: String)

/** True where an in-app language change only fully applies after a restart (iOS). */
expect val languageChangeRequiresRestart: Boolean
