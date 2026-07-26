package co.sirdab.driver.shared.core.util.testing

/**
 * Classes annotated with this are opened (non-final) only in test/build variants via the
 * `allOpen` plugin configured in the `driver.kmp.library` convention plugin, so Mokkery can
 * mock them without every production class being `open`.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class OpenForTesting
