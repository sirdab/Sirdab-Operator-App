package co.sirdab.driver.shared.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppErrorReason
import org.jetbrains.compose.resources.stringResource

/**
 * What to tell the driver about a failure: the app's own words when the data layer named the
 * reason, and the server's message only when it did not.
 */
@Composable
fun errorText(reason: AppErrorReason?, message: String?): String? =
    reason?.let { stringResource(it.labelRes()) } ?: message

/** A failure as one line of error-coloured text. */
@Composable
fun ErrorText(
    reason: AppErrorReason?,
    message: String?,
    modifier: Modifier = Modifier,
) {
    val text = errorText(reason, message) ?: return
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier,
    )
}

@Composable
fun ErrorText(error: AppError, modifier: Modifier = Modifier) =
    ErrorText(error.reason, error.message, modifier)
