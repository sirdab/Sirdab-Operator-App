package co.sirdab.driver.shared.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing

@Composable
fun DriverTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        shape = RoundedCornerShape(Radius.md),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * +966-default phone entry. Input digits are always Western (per plan §9); the +966 prefix is
 * shown on the leading side and the number keyboard is forced.
 */
@Composable
fun PhoneNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    dialCode: String = "+966",
    isError: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() }.take(9)) },
        label = { Text(label) },
        prefix = { Text("$dialCode ", style = MaterialTheme.typography.bodyLarge) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        shape = RoundedCornerShape(Radius.md),
        // Force LTR Latin digits regardless of app RTL.
        textStyle = TextStyle(textAlign = TextAlign.Start),
        modifier = modifier.fillMaxWidth(),
    )
}

/** 6-box OTP entry. Any 6 digits are accepted in the demo. */
@Composable
fun DriverOtpField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
) {
    val focusRequester = remember { FocusRequester() }

    // The code is the only thing on this screen, so the keyboard opens itself
    // rather than asking the driver to tap first.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { c -> c.isDigit() }.take(length)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        // The boxes below are the field. Hiding the real one this way keeps the
        // platform's editing, paste and SMS autofill behaviour, which drawing
        // the boxes as buttons would throw away.
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        interactionSource = remember { MutableInteractionSource() },
        modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                repeat(length) { i ->
                    val ch = value.getOrNull(i)?.toString() ?: ""
                    // The box awaiting the next digit, so the eye knows where
                    // it is without a cursor to follow.
                    val active = i == value.length.coerceAtMost(length - 1)
                    Surface(
                        shape = RoundedCornerShape(Radius.sm),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .then(
                                if (active) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(Radius.sm),
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(ch, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        },
    )
}
