package com.talko.app.feature.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.talko.app.domain.model.Message
import com.talko.app.domain.model.MessageType
import com.talko.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    chatId: String,
    title: String,
    currentUserId: String,
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
) {
    val state by viewModel.chatDetailState.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendImage(chatId, it) }
    }

    // Mic permission launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) viewModel.showSnack("Microphone permission required for voice messages")
    }

    LaunchedEffect(chatId) { viewModel.bindChat(chatId) }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }
    LaunchedEffect(state.snackMessage) {
        state.snackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // ── KEY FIX: imePadding on Scaffold so keyboard pushes content up ──
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TalkoTextPrimary)
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(38.dp).clip(CircleShape).background(avatarColor(title)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(title.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TalkoTextPrimary)
                            Text(
                                when {
                                    state.isPeerTyping -> "typing..."
                                    state.isPeerOnline -> "Online"
                                    else               -> "Offline"
                                },
                                fontSize = 11.sp,
                                color = when {
                                    state.isPeerTyping -> TalkoPrimary
                                    state.isPeerOnline -> TalkoGreen
                                    else               -> TalkoGrey
                                },
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onVideoCall) { Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = TalkoTextPrimary) }
                    IconButton(onClick = onAudioCall) { Icon(Icons.Default.Call, contentDescription = "Audio Call", tint = TalkoTextPrimary) }
                    IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, contentDescription = "More", tint = TalkoTextPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        containerColor = Color(0xFFF8F9FA),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // Offline banner
            if (state.isOffline) {
                Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFFFFF3E0)) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WifiOff, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("You're offline — messages will send when reconnected.", fontSize = 12.sp, color = Color(0xFFE65100))
                    }
                }
            }

            // AI Summary
            if (state.aiSummary.isNotBlank() && state.aiSummary != "No conversation summary yet.") {
                AiSummaryCard(summary = state.aiSummary)
            }

            // Messages list
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { DateChip(label = "Today") }
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(message = message, isMine = message.senderId == currentUserId)
                }
            }

            // Quick replies
            if (state.quickReplies.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.quickReplies.forEach { reply ->
                        QuickReplyChip(text = reply, onClick = { viewModel.applyQuickReply(reply) })
                    }
                }
            }

            // Input bar
            MessageInputBar(
                value = state.input,
                isRecording = state.isRecording,
                onValueChange = { viewModel.onInputChange(chatId, it) },
                onSend = { viewModel.sendText(chatId) },
                onAttach = { imagePickerLauncher.launch("image/*") },
                onVoiceStart = {
                    val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (hasMic) viewModel.startRecording(context, chatId)
                    else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onVoiceStop = { viewModel.stopRecording(chatId) },
            )
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun AiSummaryCard(summary: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF3E5F5),
        border = BorderStroke(1.dp, Color(0xFFE1BEE7)),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF6200EA), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text("AI Summary", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6200EA))
                Spacer(Modifier.height(2.dp))
                Text(summary, fontSize = 13.sp, fontStyle = FontStyle.Italic, color = Color(0xFF424242))
            }
        }
    }
}

@Composable
private fun DateChip(label: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = Color(0xFFEDE7F6)) {
            Text(label, modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4527A0))
        }
    }
}

@Composable
private fun MessageBubble(message: Message, isMine: Boolean) {
    val timeStr = remember(message.timestamp) {
        if (message.timestamp == 0L) "" else SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp))
    }

    // ── FIX: limit bubble to 75% of screen width ──────────────────────────────
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Box(modifier = Modifier.fillMaxWidth(0.75f)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            ) {
                when (message.messageType) {
                    MessageType.VOICE -> VoiceMessageBubble(isMine = isMine, content = message.content)
                    MessageType.IMAGE -> ImageMessageBubble(isMine = isMine, imageUrl = message.content)
                    MessageType.TEXT  -> TextMessageBubble(isMine = isMine, message = message, timeStr = timeStr)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextMessageBubble(isMine: Boolean, message: Message, timeStr: String) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (isMine) 18.dp else 4.dp,
                bottomEnd   = if (isMine) 4.dp  else 18.dp,
            ),
            color = if (isMine) TalkoBubbleMine else TalkoBubbleOther,
            shadowElevation = if (isMine) 0.dp else 1.dp,
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = { showMenu = true },
            ),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = message.content,
                    color = if (isMine) Color.White else TalkoTextPrimary,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        timeStr,
                        fontSize = 10.sp,
                        color = if (isMine) Color.White.copy(alpha = 0.7f) else TalkoGrey,
                    )
                    if (isMine) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = if (message.isSynced) Icons.Default.DoneAll else Icons.Default.Done,
                            contentDescription = null,
                            tint = if (message.isSynced) Color(0xFF80CBC4) else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        // Long-press context menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("Copy") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                onClick = {
                    showMenu = false
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("message", message.content))
                    android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

@Composable
private fun VoiceMessageBubble(isMine: Boolean, content: String) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            player.value?.release()
            player.value = null
        }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (isMine) 18.dp else 4.dp, bottomEnd = if (isMine) 4.dp else 18.dp),
        color = if (isMine) TalkoBubbleMine else Color.White,
        shadowElevation = 1.dp,
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            // Play/pause button — plays from URL if content is a URL, else shows placeholder
            IconButton(
                onClick = {
                    if (isPlaying) {
                        player.value?.pause()
                        isPlaying = false
                    } else {
                        try {
                            val mp = MediaPlayer().apply {
                                setDataSource(content)
                                prepareAsync()
                                setOnPreparedListener { start() }
                                setOnCompletionListener { isPlaying = false }
                            }
                            player.value?.release()
                            player.value = mp
                            isPlaying = true
                        } catch (e: Exception) {
                            isPlaying = false
                        }
                    }
                },
                modifier = Modifier.size(34.dp).clip(CircleShape).background(if (isMine) Color.White.copy(alpha = 0.2f) else Color(0xFFEDE7F6)),
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = if (isMine) Color.White else TalkoPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            // Waveform bars
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(4, 8, 12, 16, 10, 6, 14, 18, 12, 8, 4).forEach { h ->
                    Box(modifier = Modifier.width(3.dp).height(h.dp).clip(CircleShape).background((if (isMine) Color.White else TalkoPrimary).copy(alpha = if (isPlaying) 1f else 0.6f)))
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                content.substringAfterLast("/").substringBefore(".").let { if (it.length > 8) "Voice" else it }.ifBlank { "Voice" },
                fontSize = 11.sp,
                color = if (isMine) Color.White.copy(alpha = 0.8f) else TalkoGrey,
            )
        }
    }
}

@Composable
private fun ImageMessageBubble(isMine: Boolean, imageUrl: String) {
    Surface(shape = RoundedCornerShape(16.dp), shadowElevation = 1.dp) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(200.dp, 150.dp)
                .clip(RoundedCornerShape(16.dp)),
            loading = {
                Box(
                    modifier = Modifier
                        .size(200.dp, 150.dp)
                        .background(if (isMine) TalkoBubbleMine else TalkoGreyLight),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = if (isMine) Color.White else TalkoPrimary,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.dp,
                    )
                }
            },
            error = {
                Box(
                    modifier = Modifier
                        .size(200.dp, 150.dp)
                        .background(if (isMine) TalkoBubbleMine else TalkoGreyLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.BrokenImage,
                        contentDescription = null,
                        tint = if (isMine) Color.White.copy(alpha = 0.6f) else TalkoGrey,
                        modifier = Modifier.size(48.dp),
                    )
                }
            },
        )
    }
}

@Composable
private fun QuickReplyChip(text: String, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = Color.White, border = BorderStroke(1.dp, Color(0xFFE0E0E0)), onClick = onClick) {
        Text(text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp), fontSize = 13.sp, color = TalkoTextPrimary)
    }
}

@Composable
private fun MessageInputBar(
    value: String,
    isRecording: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceStop: () -> Unit,
) {
    Surface(color = Color.White, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isRecording) {
                // Recording indicator
                Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFFFEBEE), modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = TalkoError, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Recording...", color = TalkoError, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.width(8.dp))
                // Stop recording button
                Box(
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(TalkoError).clickable(onClick = onVoiceStop),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop recording", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            } else {
                IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SentimentSatisfied, contentDescription = "Emoji", tint = TalkoGrey)
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp, max = 120.dp),
                    placeholder = { Text("Type a message...", color = TalkoGrey, fontSize = 14.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = TalkoGreyLight,
                        unfocusedContainerColor = TalkoGreyLight,
                    ),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                )
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = onAttach, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Attach image", tint = TalkoGrey)
                }
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(TalkoPrimary)
                        .clickable(onClick = if (value.isNotBlank()) onSend else onVoiceStart),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (value.isNotBlank()) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                        contentDescription = if (value.isNotBlank()) "Send" else "Record voice",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private fun avatarColor(name: String): Color {
    val colors = listOf(Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350), Color(0xFFAB47BC), Color(0xFF42A5F5), Color(0xFFFF7043))
    return colors[name.hashCode().and(0x7FFFFFFF) % colors.size]
}
