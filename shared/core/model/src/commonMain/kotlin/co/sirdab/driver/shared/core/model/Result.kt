package co.sirdab.driver.shared.core.model

/**
 * Demo-mode result type. Mirrors the reference app's `ApiResult<T>` shape so screens and
 * ViewModels model loading/success/error identically to a real backend — the mock repositories
 * simply produce these locally (with artificial latency) instead of over HTTP.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

/**
 * Why something failed, in a form the UI can translate.
 *
 * A repository cannot localise: it has no composition, no locale, and no
 * business knowing what the sentence should say. So it names the reason and the
 * screen picks the words. [AppError.message] stays as the fallback for
 * everything that has no named reason, such as a message from the server.
 */
enum class AppErrorReason {
    /** Signed in, but no dispatcher has set this person up as a driver yet. */
    NOT_PROVISIONED,

    /** Signed in against an account, but not as a driver. */
    NOT_A_DRIVER,

    /** The phone number was lost between requesting the code and verifying it. */
    PHONE_MISSING,

    SIGN_IN_FAILED,

    /** A feature the backend does not offer yet. */
    UNAVAILABLE,

    /** Ops has suspended this driver. Nothing in the app will work until that is lifted. */
    DRIVER_SUSPENDED,

    /** The fleet this driver belongs to has been suspended. */
    WORKSPACE_SUSPENDED,

    /** A driver must keep one truck, so the last one cannot be removed. */
    LAST_TRUCK,

    /** The photograph is bigger than the contract accepts. */
    DOCUMENT_TOO_LARGE,

    /** Not a file type the contract accepts. */
    DOCUMENT_TYPE,

    /** The document row is gone, so there is nothing to confirm against. */
    DOCUMENT_MISSING,

    /** Work recorded for one fleet is still queued, and the driver is trying to leave it. */
    UNSENT_WORK,
}

data class AppError(
    val message: String,
    val cause: Throwable? = null,
    val reason: AppErrorReason? = null,
)

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(block: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) block(error)
    return this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}
