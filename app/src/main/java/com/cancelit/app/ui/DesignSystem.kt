package com.cancelit.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.cancelit.app.data.FocusApp

// ---- Visual identity: true-black canvas, white accent, one neutral gray ramp ----
// State is shown through the fill, outline and opacity of the same white (never hue), which
// keeps every screen in step with the splash screen. Every colour, text style and building block
// lives here, so the whole app can be re-themed from one place.

internal val Ink = Color(0xFF000000)          // canvas, and text on white
internal val Surface1 = Color(0xFF141414)     // cards
internal val Surface2 = Color(0xFF1F1F1F)     // inputs, icon tiles, raised fills
internal val Border = Color(0xFF2B2B2B)       // hairlines and tracks
internal val TextPrimary = Color(0xFFFFFFFF)
internal val TextSecondary = Color(0xFFB4B4B4)
internal val TextTertiary = Color(0xFF8A8A8A)
internal val BarMuted = Color(0xFF3F3F3F)     // past-day chart bars

internal val StyleTitle = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, color = TextPrimary)
internal val StyleHeading = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, color = TextPrimary)
internal val StyleSection = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
internal val StyleBody = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = TextPrimary)
internal val StyleBodyStrong = StyleBody.copy(fontWeight = FontWeight.SemiBold)
internal val StyleCaption = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = TextSecondary)
internal val StyleHint = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, color = TextTertiary)
internal val StyleNumber = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontFeatureSettings = "tnum")

internal val CardShape = RoundedCornerShape(20.dp)
internal val InputShape = RoundedCornerShape(16.dp)
internal val ButtonShape = RoundedCornerShape(16.dp)
internal val PillShape = RoundedCornerShape(50)

/** Material components (dialogs, switches) get the same black and white. */
@Composable
internal fun CancelItTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = TextPrimary, onPrimary = Ink,
        background = Ink, surface = Surface1, onSurface = TextPrimary,
        surfaceVariant = Surface2, onSurfaceVariant = TextSecondary,
        outline = Border
    ),
    content = content
)

/** Semantic states, shown only through fill / outline of the same white, never through hue. */
internal enum class DotStyle { ACTIVE, PAUSED, OFF }

@Composable
internal fun StatusDot(style: DotStyle, size: Dp = 8.dp) {
    when (style) {
        DotStyle.ACTIVE -> Box(Modifier.size(size).clip(CircleShape).background(TextPrimary))
        DotStyle.PAUSED -> Box(Modifier.size(size).clip(CircleShape).border(1.5.dp, TextPrimary, CircleShape))
        DotStyle.OFF -> Box(Modifier.size(size).clip(CircleShape).background(TextTertiary))
    }
}

/** A screen's title row. [leading] and [trailing] sit on either side of the title. */
@Composable
internal fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = StyleTitle)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = StyleCaption)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** A status chip for screen headers ("Protected", "On"). Tappable only when [onClick] is set. */
@Composable
internal fun StatusPill(label: String, on: Boolean, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .clip(PillShape)
            .background(Surface1)
            .border(1.dp, Border, PillShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(if (on) DotStyle.ACTIVE else DotStyle.PAUSED, size = 7.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = StyleSection, modifier = modifier)

/** [title] above its card(s). */
@Composable
internal fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier) {
        SectionTitle(title)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** A single padded card, for forms and charts. */
@Composable
internal fun SectionCard(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Surface1)
            .border(1.dp, Border, CardShape)
            .padding(padding),
        content = content
    )
}

/** An unpadded card for lists of rows separated by [RowDivider]. */
@Composable
internal fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Surface1)
            .border(1.dp, Border, CardShape),
        content = content
    )
}

@Composable
internal fun RowDivider() {
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(Border))
}

/** A rounded square holding an icon, at the start of a row. */
@Composable
internal fun IconTile(icon: ImageVector) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Surface2),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
    }
}

/** Icon tile + title + one-line summary. The building block for "how it works" lists. */
@Composable
internal fun InfoRow(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
        IconTile(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = StyleBodyStrong)
            Spacer(Modifier.height(2.dp))
            Text(body, style = StyleCaption)
        }
    }
}

@Composable
internal fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = TextPrimary,
            contentColor = Ink,
            disabledContainerColor = Surface2,
            disabledContentColor = TextTertiary
        )
    ) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
}

@Composable
internal fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = ButtonShape,
        border = BorderStroke(1.dp, Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
    ) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
}

/** Compact button for actions that live inside a row or a card. */
@Composable
internal fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val fill = when {
        !filled -> Color.Transparent
        enabled -> TextPrimary
        else -> Surface2
    }
    Box(
        modifier
            .height(34.dp)
            .clip(PillShape)
            .background(fill)
            .border(1.dp, if (filled && enabled) TextPrimary else Border, PillShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = when {
                !enabled -> TextTertiary
                filled -> Ink
                else -> TextPrimary
            }
        )
    }
}

@Composable
internal fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked, onCheckedChange = onCheckedChange, enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Ink, checkedTrackColor = TextPrimary, checkedBorderColor = TextPrimary,
            uncheckedThumbColor = TextTertiary, uncheckedTrackColor = Surface2, uncheckedBorderColor = Border
        )
    )
}

/** Single-line or multi-line input with a placeholder instead of a floating label. */
@Composable
internal fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        enabled = enabled,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else maxLines,
        textStyle = StyleBody.copy(color = if (enabled) TextPrimary else TextTertiary),
        cursorBrush = SolidColor(TextPrimary),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(InputShape)
                    .background(Surface2)
                    .border(1.dp, if (focused) TextPrimary else Border, InputShape)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (value.isEmpty()) Text(placeholder, style = StyleBody, color = TextTertiary)
                inner()
            }
        }
    )
}

/** Two-or-more option switcher. */
@Composable
internal fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    val outer = RoundedCornerShape(14.dp)
    val inner = RoundedCornerShape(11.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(outer)
            .background(Surface1)
            .border(1.dp, Border, outer)
            .padding(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(inner)
                    .background(if (selected) TextPrimary else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                    color = if (selected) Ink else TextSecondary
                )
            }
        }
    }
}

/** Thin, flat progress bar (no end-stop dot, unlike the stock indicator). */
@Composable
internal fun ThinProgress(fraction: Float, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(Border)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(TextPrimary))
    }
}

/** Circular progress with room for content in the middle. [fraction] is how much of the ring is filled. */
@Composable
internal fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 10.dp,
    animate: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val shown by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = if (animate) tween(durationMillis = 950, easing = LinearEasing) else tween(durationMillis = 0),
        label = "ring"
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawArc(
                color = Border, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(width = strokePx)
            )
            if (shown > 0.002f) {
                drawArc(
                    color = TextPrimary, startAngle = -90f, sweepAngle = 360f * shown, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

/** The app's own icon when Android lets us see it, otherwise a neutral tile with its initial. */
@Composable
internal fun AppAvatar(app: FocusApp, size: Dp = 40.dp) {
    val context = LocalContext.current
    val icon = remember(app.packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()
    }
    val shape = RoundedCornerShape(size * 0.28f)
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(size).clip(shape))
    } else {
        Box(
            Modifier.size(size).clip(shape).background(Surface2).border(1.dp, Border, shape),
            contentAlignment = Alignment.Center
        ) {
            Text(app.label.firstOrNull()?.uppercase() ?: "?", style = StyleBodyStrong)
        }
    }
}

/** A card that stays collapsed until tapped, for detail nobody needs until they ask for it. */
@Composable
internal fun Disclosure(title: String, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    GroupCard(Modifier.animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = StyleBodyStrong, modifier = Modifier.weight(1f))
            Icon(
                if (open) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                if (open) "Collapse" else "Expand", tint = TextTertiary
            )
        }
        if (open) content()
    }
}

/** A [Disclosure] holding a bulleted list. */
@Composable
internal fun Disclosure(title: String, points: List<String>) = Disclosure(title) {
    points.forEach { point ->
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            Text("•", style = StyleCaption, modifier = Modifier.width(14.dp))
            Text(point, style = StyleCaption, modifier = Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
internal fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Surface2), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = StyleBodyStrong, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, style = StyleCaption, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
internal fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(CardShape)
            .background(Surface1)
            .border(1.dp, Border, CardShape)
            .padding(16.dp)
    ) {
        Text(label, style = StyleHint)
        Spacer(Modifier.height(6.dp))
        Text(value, style = StyleHeading.copy(fontFeatureSettings = "tnum"), maxLines = 1)
    }
}

/**
 * A full-screen page with a header, scrolling [content] and its buttons pinned to the bottom:
 * the privacy policy, the age question and the Accessibility disclosure.
 */
@Composable
internal fun FullScreenNotice(
    title: String,
    subtitle: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
    content: LazyListScope.() -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = Ink) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item { ScreenHeader(title, subtitle) }
                content()
            }
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                PrimaryButton(primaryLabel, Modifier.fillMaxWidth(), onClick = onPrimary)
                if (secondaryLabel != null) {
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton(secondaryLabel, Modifier.fillMaxWidth(), onClick = onSecondary)
                }
            }
        }
    }
}

/** The app's dialog: a title, a [body], a confirm button and "Cancel". */
@Composable
internal fun AppDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    body: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = { Text(title, style = StyleHeading) },
        text = body,
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(confirmLabel, color = if (confirmEnabled) TextPrimary else TextTertiary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}
