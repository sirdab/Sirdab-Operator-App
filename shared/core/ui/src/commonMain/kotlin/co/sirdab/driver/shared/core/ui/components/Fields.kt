package co.sirdab.driver.shared.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Box(modifier = modifier.fillMaxWidth()) {
        // Invisible actual input driving the boxes.
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.filter { c -> c.isDigit() }.take(length)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            label = null,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        repeat(length) { i ->
            val ch = value.getOrNull(i)?.toString() ?: ""
            Surface(
                shape = RoundedCornerShape(Radius.sm),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(width = 44.dp, height = 52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(ch, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}
