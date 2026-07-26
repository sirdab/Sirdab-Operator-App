package co.sirdab.driver.shared.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.sirdab.driver.shared.core.ui.theme.AppColors
import co.sirdab.driver.shared.core.ui.theme.Radius

enum class ChipTone { NEUTRAL, PRIMARY, WARNING, DANGER, SUCCESS }

@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: ChipTone = ChipTone.NEUTRAL,
) {
    val (bg, fg) = when (tone) {
        ChipTone.NEUTRAL -> AppColors.Gray.c100 to AppColors.Gray.c700
        ChipTone.PRIMARY -> AppColors.Primary.c50 to AppColors.Primary.c700
        ChipTone.WARNING -> AppColors.Amber.c50 to AppColors.Amber.c800
        ChipTone.DANGER -> AppColors.Red.c50 to AppColors.Red.c700
        ChipTone.SUCCESS -> AppColors.Green.c50 to AppColors.Green.c700
    }
    Surface(shape = RoundedCornerShape(Radius.pill), color = bg, modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
