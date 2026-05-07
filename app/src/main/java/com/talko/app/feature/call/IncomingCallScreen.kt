package com.talko.app.feature.call

import android.media.RingtoneManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talko.app.domain.model.CallType
import com.talko.app.ui.theme.TalkoPrimary

@Composable
fun IncomingCallScreen(
    callerName: String,
    callType: CallType,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val context = LocalContext.current
    // Show friendly name: strip email domain if it looks like an email
    val displayName = remember(callerName) {
        if (callerName.contains("@")) callerName.substringBefore("@").replaceFirstChar { it.uppercase() }
        else callerName
    }

    // Play ringtone
    DisposableEffect(Unit) {
        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val ringtone = RingtoneManager.getRingtone(context, ringtoneUri)
        ringtone?.play()
        onDispose { ringtone?.stop() }
    }

    // Pulsing ring animation
    val infiniteTransition = rememberInfiniteTransition(label = "ring")
    val outerScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "outer",
    )
    val innerScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "inner",
    )

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(colors = listOf(Color(0xFF0D0D1F), Color(0xFF1A1A3E), Color(0xFF0D0D1F)))
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Spacer(Modifier.height(48.dp))

            // Call type badge
            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.15f)) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (callType == CallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call,
                        contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (callType == CallType.VIDEO) "Incoming Video Call" else "Incoming Voice Call",
                        fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Pulsing avatar
            Box(contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(200.dp).scale(outerScale).clip(CircleShape).background(Color.White.copy(alpha = 0.06f)))
                Box(modifier = Modifier.size(170.dp).scale(innerScale).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)))
                Box(
                    modifier = Modifier.size(130.dp).clip(CircleShape).background(
                        Brush.linearGradient(colors = listOf(Color(0xFF5C6BC0), Color(0xFF3A3DC8)))
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(displayName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 52.sp)
                }
            }

            Text(displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 30.sp)
            Text("is calling you…", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)

            Spacer(Modifier.weight(1f))

            // Accept / Decline buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Decline
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFD32F2F),
                        onClick = onDecline,
                        modifier = Modifier.size(72.dp),
                        shadowElevation = 8.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.CallEnd, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Decline", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }

                // Accept
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF2E7D32),
                        onClick = onAccept,
                        modifier = Modifier.size(72.dp),
                        shadowElevation = 8.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                if (callType == CallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call,
                                contentDescription = "Accept",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Accept", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}
