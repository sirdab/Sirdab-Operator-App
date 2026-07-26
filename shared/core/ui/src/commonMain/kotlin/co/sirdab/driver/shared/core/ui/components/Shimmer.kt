package co.sirdab.driver.shared.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.sirdab.driver.shared.core.ui.theme.AppColors
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing

/** A single shimmering placeholder bar. */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, height: Dp = 16.dp) {
    val alpha by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha",
    )
    androidx.compose.foundation.layout.Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(Radius.sm))
            .background(AppColors.Gray.c200.copy(alpha = alpha)),
    )
}

/** A skeleton card mimicking a content block while it loads. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(Spacing.md)) {
        ShimmerBox(Modifier.fillMaxWidth(0.6f), height = 20.dp)
        Spacer(Modifier.height(Spacing.sm))
        ShimmerBox(Modifier.fillMaxWidth(0.4f))
        Spacer(Modifier.height(Spacing.md))
        ShimmerBox(Modifier.fillMaxWidth(0.9f), height = 40.dp)
    }
}
