package com.talko.app.feature.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talko.app.domain.model.Chat
import com.talko.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    chats: List<Chat>,
    onOpenChat: (Chat) -> Unit,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onNewChat: () -> Unit = {},
    onNewChatCreated: (chatId: String, title: String) -> Unit = { _, _ -> },
) {
    var showNewChatDialog by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val displayedChats = if (searchQuery.isNotBlank()) {
        chats.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.lastMessagePreview.contains(searchQuery, ignoreCase = true)
        }
    } else chats

    if (showNewChatDialog) {
        NewChatDialog(
            onDismiss = { showNewChatDialog = false },
            onStart = { chatId, title ->
                showNewChatDialog = false
                onNewChatCreated(chatId, title)
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = searchActive,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "search_toggle",
                    ) { isSearching ->
                        if (isSearching) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                placeholder = { Text("Search chats…", color = TalkoGrey) },
                                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = TalkoGreyLight,
                                    unfocusedContainerColor = TalkoGreyLight,
                                ),
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TalkoGrey) },
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(onClick = onClearSearch) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TalkoGrey)
                                        }
                                    }
                                },
                            )
                            LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        } else {
                            Text("Talko", color = TalkoPrimary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        }
                    }
                },
                navigationIcon = {
                    if (searchActive) {
                        IconButton(onClick = {
                            searchActive = false
                            onClearSearch()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Close search", tint = TalkoTextPrimary)
                        }
                    }
                },
                actions = {
                    if (!searchActive) {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = TalkoTextPrimary)
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = TalkoTextPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TalkoBackground),
            )
        },
        floatingActionButton = {
            if (!searchActive) {
                FloatingActionButton(onClick = { showNewChatDialog = true }, containerColor = TalkoPrimary, shape = CircleShape) {
                    Icon(Icons.Default.Edit, contentDescription = "New Chat", tint = Color.White)
                }
            }
        },
        containerColor = TalkoBackground,
    ) { padding ->
        when {
            // Search active but no results
            searchActive && searchQuery.isNotBlank() && displayedChats.isEmpty() -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔍", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("No results for \"$searchQuery\"", style = MaterialTheme.typography.titleMedium, color = TalkoTextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text("Try a different name or keyword", color = TalkoGrey, fontSize = 13.sp)
                    }
                }
            }
            // Empty state (no chats at all)
            displayedChats.isEmpty() -> EmptyChatList(modifier = Modifier.padding(padding))
            // Normal list
            else -> {
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    if (!searchActive) {
                        // Pinned section — first chat
                        item { SectionHeader("PINNED") }
                        item { ChatListItem(chat = displayedChats.first(), isPinned = true, onClick = { onOpenChat(displayedChats.first()) }) }
                        item { SectionHeader("RECENT") }
                        items(displayedChats.drop(1), key = { it.id }) { chat ->
                            ChatListItem(chat = chat, onClick = { onOpenChat(chat) })
                        }
                    } else {
                        // Search results — flat list, no sections
                        items(displayedChats, key = { it.id }) { chat ->
                            ChatListItem(chat = chat, onClick = { onOpenChat(chat) })
                        }
                    }
                }
            }
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TalkoGrey,
        letterSpacing = 1.2.sp,
    )
}

@Composable
private fun ChatListItem(chat: Chat, isPinned: Boolean = false, onClick: () -> Unit) {
    val timeStr = remember(chat.lastMessageTime) {
        if (chat.lastMessageTime == 0L) ""
        else {
            val now = System.currentTimeMillis()
            val diff = now - chat.lastMessageTime
            when {
                diff < 60_000      -> "now"
                diff < 3_600_000   -> "${diff / 60_000}m"
                diff < 86_400_000  -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(chat.lastMessageTime))
                else               -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(chat.lastMessageTime))
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(54.dp)) {
            Box(
                modifier = Modifier.size(54.dp).clip(CircleShape).background(avatarColor(chat.title)),
                contentAlignment = Alignment.Center,
            ) {
                Text(chat.title.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Box(
                modifier = Modifier.size(14.dp).align(Alignment.BottomEnd).clip(CircleShape).background(TalkoBackground)
                    .padding(2.dp).clip(CircleShape).background(TalkoGreen),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(chat.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TalkoTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isPinned) Icon(Icons.Default.PushPin, contentDescription = null, tint = TalkoPrimary, modifier = Modifier.size(12.dp))
                    Text(timeStr, fontSize = 11.sp, color = if (chat.unreadCount > 0) TalkoPrimary else TalkoGrey)
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(chat.lastMessagePreview, fontSize = 13.sp, color = TalkoTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (chat.unreadCount > 0) {
                    Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(TalkoPrimary), contentAlignment = Alignment.Center) {
                        Text(if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 84.dp), color = TalkoGreyLight, thickness = 0.5.dp)
}

@Composable
private fun EmptyChatList(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("💬", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text("No conversations yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = TalkoTextPrimary)
        Spacer(Modifier.height(8.dp))
        Text("Start a new chat by tapping the edit button", color = TalkoGrey, fontSize = 14.sp)
    }
}

private fun avatarColor(name: String): Color {
    val colors = listOf(Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350), Color(0xFFAB47BC), Color(0xFF42A5F5), Color(0xFFFF7043), Color(0xFF66BB6A), Color(0xFFEC407A))
    return colors[name.hashCode().and(0x7FFFFFFF) % colors.size]
}

@Composable
private fun NewChatDialog(onDismiss: () -> Unit, onStart: (chatId: String, title: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text("New Conversation", fontWeight = FontWeight.Bold, color = TalkoTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the name or email of the person you want to chat with.", fontSize = 13.sp, color = TalkoGrey)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    placeholder = { Text("Name or email") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TalkoPrimary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = TalkoError, fontSize = 12.sp) } },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isBlank()) { error = "Please enter a name or email."; return@Button }
                    val chatId = "chat_${trimmed.lowercase().replace(" ", "_")}_${System.currentTimeMillis()}"
                    onStart(chatId, trimmed)
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TalkoPrimary),
            ) { Text("Start Chat") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TalkoGrey) } },
    )
}
