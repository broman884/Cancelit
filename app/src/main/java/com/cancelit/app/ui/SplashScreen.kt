package com.cancelit.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The tagline's gray, a step darker than the app's text grays. */
private val Dim = Color(0xFF6E6E6E)

/**
 * Full-bleed animated splash, meant to be shown once per cold start, on top of (or in
 * place of) the app's normal content. Sequence:
 *   1. Ring sweeps in (0 -> ~290deg)
 *   2. Center dot pops in
 *   3. Wordmark + tagline fade/slide up
 *   4. A thin progress hairline fills left -> right
 *   5. Whole thing fades out and [onFinished] fires
 *
 * Total runtime is intentionally short (~1.6s) — this is a brand moment, not a loading
 * gate. If real startup work (e.g. reading FocusStore) needs more time, run it
 * concurrently and have [onFinished] wait on both this animation and that work.
 */
@Composable
fun AnimatedSplashScreen(onFinished: () -> Unit) {
    val sweep = remember { Animatable(0f) }
    val dotScale = remember { Animatable(0f) }
    var showText by remember { mutableStateOf(false) }
    var showProgress by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(true) }

    val progress by animateFloatAsState(
        targetValue = if (showProgress) 1f else 0f,
        animationSpec = tween(durationMillis = 550, easing = LinearOutSlowInEasing),
        label = "splash_progress"
    )

    LaunchedEffect(Unit) {
        launch {
            sweep.animateTo(1f, tween(durationMillis = 620, easing = EaseOutCubic))
        }
        delay(420)
        dotScale.animateTo(1f, tween(durationMillis = 260, easing = EaseOutCubic))
        showText = true
        delay(150)
        showProgress = true
        delay(650)
        visible = false
        delay(280) // let the fade-out finish before handing off
        onFinished()
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(tween(260))
    ) {
        Box(
            Modifier.fillMaxSize().background(Ink),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CancelItLogo(
                    size = 88.dp,
                    color = TextPrimary,
                    sweepFraction = sweep.value,
                    dotScale = dotScale.value
                )
                Spacer(Modifier.height(20.dp))
                AnimatedVisibility(
                    visible = showText,
                    enter = fadeIn(tween(320)) + slideInVertically(
                        animationSpec = tween(320, easing = EaseOutCubic),
                        initialOffsetY = { it / 3 }
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "CancelIt",
                            color = TextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 26.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "TAKE BACK YOUR ATTENTION",
                            color = Dim,
                            fontSize = 11.sp,
                            letterSpacing = 1.8.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
                Box(
                    Modifier
                        .width(72.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Border)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(TextPrimary)
                    )
                }
            }
        }
    }
}
