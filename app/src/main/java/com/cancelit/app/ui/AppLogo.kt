package com.cancelit.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The CancelIt mark: a ring with a gap (reads as a "C" and as a focus ring) plus a
 * center dot. Same shape as res/drawable/ic_cancelit_mark.xml, redrawn in Compose so the
 * splash screen can animate [sweepFraction] (0f = no ring, 1f = full ~290° arc) and
 * [dotScale] independently.
 *
 * @param sweepFraction 0f..1f, how much of the ring is drawn in.
 * @param dotScale 0f..1f, scale of the center dot.
 */
@Composable
fun CancelItLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    color: Color = Color.White,
    sweepFraction: Float = 1f,
    dotScale: Float = 1f
) {
    Canvas(modifier.size(size)) {
        val strokeWidth = this.size.minDimension * 0.065f
        val inset = strokeWidth / 2f
        val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
        val topLeft = Offset(inset, inset)

        // Full ring sweeps ~290°, starting at -75° (leaves a gap at the bottom-right,
        // matching the vector mark's "C" opening).
        val startAngle = -75f
        val maxSweep = 290f

        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = maxSweep * sweepFraction.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        val dotRadius = this.size.minDimension * 0.11f * dotScale.coerceIn(0f, 1f)
        if (dotRadius > 0f) {
            drawCircle(
                color = color,
                radius = dotRadius,
                center = Offset(this.size.width / 2f, this.size.height * 0.44f)
            )
        }
    }
}
