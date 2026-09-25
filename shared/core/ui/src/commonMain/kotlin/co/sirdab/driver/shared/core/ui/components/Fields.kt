package co.sirdab.driver.shared.core.ui.components

import co.sirdab.driver.shared.core.util.saudiMobileDigits
import co.sirdab.driver.shared.core.util.westernDigits
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.common_cancel
import co.sirdab.driver.shared.core.ui.generated.resources.common_ok
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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
        onValueChange = { input -> onValueChange(saudiMobileDigits(input)) },
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
        onValueChange = { onValueChange(it.westernDigits().take(length)) },
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

/**
 * A single choice from a short list, in the shape of a text field.
 *
 * Reads as the fields above it rather than as a row of options, which matters on a form where one
 * answer out of six is typed and the next is picked. [placeholder] shows while nothing is chosen,
 * so a required choice does not look like an answered one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DriverDropdownField(
    label: String,
    selected: T?,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected?.let { optionLabel(it) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(Radius.md),
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * A date, picked rather than typed.
 *
 * [value] is ISO `yyyy-MM-dd`, which is the only form the contract's date fields take, and the
 * only form this writes. A licence that expired is not a licence, so by default the calendar
 * offers today onwards; pass [allowPast] for a date that may legitimately be behind us.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun DriverDateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    allowPast: Boolean = false,
    yearsAhead: Int = 30,
) {
    var picking by remember { mutableStateOf(false) }
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.UTC).date }
    val firstDay = if (allowPast) today.year - yearsAhead else today.year

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            trailingIcon = {
                Icon(Icons.Default.CalendarMonth, contentDescription = null)
            },
            shape = RoundedCornerShape(Radius.md),
            modifier = Modifier.fillMaxWidth(),
        )
        // The field itself swallows taps, readOnly or not, so the whole of it is covered by
        // something that does open the calendar.
        Box(
            Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { picking = true },
        )
    }

    if (!picking) return

    val state = rememberDatePickerState(
        initialSelectedDateMillis = value.toEpochMillisOrNull(),
        yearRange = firstDay..(today.year + yearsAhead),
        selectableDates = remember(allowPast) { FromToday(today, allowPast) },
    )
    DatePickerDialog(
        onDismissRequest = { picking = false },
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onValueChange(it.toIsoDate()) }
                    picking = false
                },
                enabled = state.selectedDateMillis != null,
            ) {
                Text(stringResource(Res.string.common_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = { picking = false }) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
private class FromToday(private val today: LocalDate, private val allowPast: Boolean) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        allowPast || utcTimeMillis >= today.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

    override fun isSelectableYear(year: Int): Boolean = allowPast || year >= today.year
}

/** The picker works in UTC midnights, which is exactly what a bare date is. */
@OptIn(ExperimentalTime::class)
private fun Long.toIsoDate(): String =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date.toString()

@OptIn(ExperimentalTime::class)
private fun String.toEpochMillisOrNull(): Long? = runCatching {
    LocalDate.parse(this).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
}.getOrNull()
