package com.talko.app.feature.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Matches the Figma splash: light background, centered logo + wordmark,
// animated progress bar at the bottom, "Establishing Secure Sync • v2.4.0"

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    // Animate in
    val logoScale  = remember { Animatable(0.6f) }
    val logoAlpha  = remember { Animatable(0f) }
    val textAlpha  = remember { Animatable(0f) }
    val progress   = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Logo pop-in
        logoScale.animateTo(1f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 300f))
        logoAlpha.animateTo(1f, animationSpec = tween(300))
        // Tagline fade
        textAlpha.animateTo(1f, animationSpec = tween(400, easing = FastOutSlowInEasing))
        // Progress bar fills over ~1.8 s
        progress.animateTo(1f, animationSpec = tween(1800, easing = LinearEasing))
        delay(200)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFFF0F0FF), Color(0xFFF7F7FF)),
                    radius = 1200f,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        // ── Centre content ────────────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(logoAlpha.value),
        ) {
            // App icon — rounded square with chat bubble
            Box(
                modifier = Modifier
                    .scale(logoScale.value)
                    .size(96.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF6B6EF9), Color(0xFF4C4FDE)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Chat bubble icon drawn with text emoji (no drawable needed)
                Text("💬", fontSize = 44.sp)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Talko",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A2E),
                letterSpacing = (-0.5).sp,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                "KINETIC CLARITY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF8B8C9B),
                letterSpacing = 3.sp,
                modifier = Modifier.alpha(textAlpha.value),
            )
        }

        // ── Bottom progress + version ─────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LinearProgressIndicator(
                progress = { progress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color(0xFF4C4FDE),
                trackColor = Color(0xFFDDDDEE),
                strokeCap = StrokeCap.Round,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Establishing Secure Sync  •  v2.4.0",
                fontSize = 11.sp,
                color = Color(0xFF8B8C9B),
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(textAlpha.value),
            )
        }
    }
}
