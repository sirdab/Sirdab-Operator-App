package co.sirdab.driver.shared.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import co.sirdab.driver.shared.core.ui.theme.AppColors
import co.sirdab.driver.shared.core.ui.theme.Radius

/** A finger-draw signature capture. [clearKey] change clears the pad; reports content presence. */
@Composable
fun SignaturePad(
    clearKey: Int,
    onChanged: (hasContent: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strokes = remember(clearKey) { mutableStateListOf<MutableList<Offset>>() }

    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Canvas(
            Modifier.fillMaxWidth().height(180.dp).pointerInput(clearKey) {
                detectDragGestures(
                    onDragStart = { offset ->
                        strokes.add(mutableListOf(offset))
                        onChanged(true)
                    },
                    onDrag = { change, _ ->
                        strokes.lastOrNull()?.add(change.position)
                    },
                )
            },
        ) {
            strokes.forEach { stroke ->
                if (stroke.size >= 2) {
                    val path = Path().apply {
                        moveTo(stroke.first().x, stroke.first().y)
                        stroke.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, color = AppColors.Gray.c900, style = Stroke(width = 5f, cap = StrokeCap.Round))
                }
            }
        }
    }
}
