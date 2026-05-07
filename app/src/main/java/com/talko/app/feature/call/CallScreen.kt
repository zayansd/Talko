package com.talko.app.feature.call

import android.view.SurfaceView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.talko.app.core.permission.rememberCallPermissions
import com.talko.app.domain.model.CallType
import com.talko.app.ui.theme.*

@Composable
fun CallScreen(
    peerName: String,
    callType: CallType,
    viewModel: CallViewModel,
    onEnd: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    // Request mic + camera permissions before anything
    val permissionsGranted = rememberCallPermissions(needCamera = callType == CallType.VIDEO)
    if (!permissionsGranted) {
        PermissionDeniedScreen(onEnd = onEnd)
        return
    }

    val durationStr = remember(state.callDurationSec) {
        val m = state.callDurationSec / 60
        val s = state.callDurationSec % 60
        "%02d:%02d".format(m, s)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D0D1F), Color(0xFF1A1A3E), Color(0xFF0D0D1F))
                )
            ),
    ) {
        // ── Remote video (full screen) ────────────────────────────────────────
        if (callType == CallType.VIDEO) {
            if (state.inCall) {
                // Key on remoteUserJoined so AndroidView re-creates when remote connects
                key(state.remoteUserJoined) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).also { sv ->
                                viewModel.setupRemoteVideo(sv)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A18)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White.copy(alpha = 0.08f), modifier = Modifier.size(120.dp))
                }
            }
        }

        // ── Local video PiP (top-right) ───────────────────────────────────────
        if (callType == CallType.VIDEO && state.videoEnabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 72.dp, end = 16.dp)
                    .size(width = 96.dp, height = 140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E1E40)),
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).also { sv ->
                            viewModel.setupLocalVideo(sv)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // ── Main overlay UI ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top bar
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.15f)) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            if (callType == CallType.VIDEO) "ENCRYPTED VIDEO" else "ENCRYPTED AUDIO",
                            fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                // Camera switch for video calls
                if (callType == CallType.VIDEO) {
                    IconButton(onClick = { viewModel.switchCamera() }) {
                        Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Switch camera", tint = Color.White)
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }

            // Centre: avatar (audio) or name overlay (video)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (callType == CallType.AUDIO) {
                    PulsingAvatar(name = peerName, isActive = state.inCall)
                    Spacer(Modifier.height(20.dp))
                }
                Text(peerName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (!state.inCall) "Connecting…" else durationStr,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape)
                            .background(if (state.inCall) TalkoGreen else Color.White.copy(alpha = 0.4f)),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (state.inCall) "Connected" else "Calling…",
                        color = if (state.inCall) TalkoGreen else Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                    )
                }
            }

            // Controls
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CallControlButton(
                        icon = if (state.muted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (state.muted) "UNMUTE" else "MUTE",
                        isActive = state.muted,
                        onClick = viewModel::toggleMute,
                    )
                    CallControlButton(
                        icon = if (state.speakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                        label = "SPEAKER",
                        isActive = state.speakerOn,
                        onClick = viewModel::toggleSpeaker,
                    )
                    if (callType == CallType.VIDEO) {
                        CallControlButton(
                            icon = if (state.videoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            label = "VIDEO",
                            isActive = !state.videoEnabled,
                            onClick = viewModel::toggleVideo,
                        )
                    } else {
                        CallControlButton(icon = Icons.Default.Dialpad, label = "KEYPAD", onClick = {})
                    }
                }

                // End call button
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFD32F2F),
                    onClick = { viewModel.end(); onEnd() },
                    modifier = Modifier.size(72.dp),
                    shadowElevation = 8.dp,
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }

        // Error dialog
        if (state.errorDialog != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                title = { Text("Call Failed") },
                text = {
                    Column {
                        Text(state.errorDialog!!)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Make sure you have set your Agora App ID in AgoraConfig.kt",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = { Button(onClick = viewModel::dismissDialog) { Text("OK") } },
                dismissButton = { TextButton(onClick = { viewModel.dismissDialog(); onEnd() }) { Text("Cancel") } },
            )
        }
    }
}

// ── Pulsing avatar ────────────────────────────────────────────────────────────

@Composable
private fun PulsingAvatar(name: String, isActive: Boolean) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1f else 1.07f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    Box(
        modifier = Modifier.size(160.dp).scale(scale).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(130.dp).clip(CircleShape).background(
                Brush.linearGradient(colors = listOf(Color(0xFF5C6BC0), Color(0xFF3A3DC8)))
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 52.sp)
        }
    }
}

// ── Control button ────────────────────────────────────────────────────────────

@Composable
private fun CallControlButton(icon: ImageVector, label: String, isActive: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = if (isActive) TalkoPrimary else Color.White.copy(alpha = 0.15f),
            onClick = onClick,
            modifier = Modifier.size(58.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 9.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
    }
}

// ── Permission denied ─────────────────────────────────────────────────────────

@Composable
private fun PermissionDeniedScreen(onEnd: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D1F)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.MicOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("Permissions Required", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Microphone (and camera for video calls) access is needed to make calls.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onEnd, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)), shape = RoundedCornerShape(12.dp)) {
                Text("Go Back")
            }
        }
    }
}
