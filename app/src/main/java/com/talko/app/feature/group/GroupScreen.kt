package com.talko.app.feature.group

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.talko.app.domain.model.Chat
import com.talko.app.domain.repository.ChatRepository
import com.talko.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class GroupUiState(
    val groups: List<Chat> = emptyList(),
    val isCreating: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class GroupViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupUiState())
    val uiState: StateFlow<GroupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            chatRepository.observeChats().collect { chats ->
                _uiState.update { it.copy(groups = chats.filter { c -> c.isGroup }) }
            }
        }
    }

    fun createGroup(name: String, memberPhones: List<String>) = viewModelScope.launch {
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "Group name is required") }
            return@launch
        }
        _uiState.update { it.copy(isCreating = true, error = null) }
        runCatching {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not logged in")
            val groupId = UUID.randomUUID().toString()
            firestore.collection("chats").document(groupId).set(
                mapOf(
                    "title" to name.trim(),
                    "isGroup" to true,
                    "lastMessagePreview" to "Group created",
                    "lastMessageTime" to System.currentTimeMillis(),
                    "participants" to FieldValue.arrayUnion(uid),
                    "adminId" to uid,
                ),
            ).await()
        }
            .onSuccess { _uiState.update { it.copy(isCreating = false) } }
            .onFailure { e -> _uiState.update { it.copy(isCreating = false, error = e.message) } }
    }
}

// ── Screens ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(
    onOpenGroup: (Chat) -> Unit,
    onCreateGroup: () -> Unit,
) {
    val vm: GroupViewModel = hiltViewModel()
    val state by vm.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Groups", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TalkoTextPrimary) },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = TalkoTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TalkoBackground),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateGroup,
                containerColor = TalkoPrimary,
                shape = CircleShape,
            ) {
                Icon(Icons.Default.GroupAdd, contentDescription = "New Group", tint = Color.White)
            }
        },
        containerColor = TalkoBackground,
    ) { padding ->
        if (state.groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("👥", fontSize = 64.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("No groups yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = TalkoTextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text("Create a group to chat with multiple people", color = TalkoGrey, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(state.groups, key = { it.id }) { group ->
                    GroupListItem(group = group, onClick = { onOpenGroup(group) })
                }
            }
        }
    }
}

@Composable
private fun GroupListItem(group: Chat, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(TalkoPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Group, contentDescription = null, tint = TalkoPrimary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(group.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TalkoTextPrimary)
            Text(group.lastMessagePreview, fontSize = 13.sp, color = TalkoTextSecondary, maxLines = 1)
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 84.dp), color = TalkoGreyLight, thickness = 0.5.dp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(onBack: () -> Unit, onCreated: () -> Unit) {
    val vm: GroupViewModel = hiltViewModel()
    val state by vm.uiState.collectAsState()
    var groupName by remember { mutableStateOf("") }
    var memberInput by remember { mutableStateOf("") }
    val members = remember { mutableStateListOf<String>() }
    var hasAttemptedCreate by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(state.error) {
        state.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(state.isCreating) {
        if (hasAttemptedCreate && !state.isCreating && state.error == null) onCreated()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("New Group", fontWeight = FontWeight.Bold, color = TalkoTextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TalkoBackground),
            )
        },
        containerColor = TalkoBackground,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Group icon placeholder
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(TalkoPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = TalkoPrimary, modifier = Modifier.size(36.dp))
            }

            Text("GROUP NAME", fontWeight = FontWeight.SemiBold, color = TalkoGrey, fontSize = 11.sp, letterSpacing = 1.sp)
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                placeholder = { Text("Enter group name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
            )

            Text("ADD MEMBERS", fontWeight = FontWeight.SemiBold, color = TalkoGrey, fontSize = 11.sp, letterSpacing = 1.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = memberInput,
                    onValueChange = { memberInput = it },
                    placeholder = { Text("Phone number") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                )
                IconButton(
                    onClick = {
                        if (memberInput.isNotBlank()) {
                            members.add(memberInput.trim())
                            memberInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(TalkoPrimary),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                }
            }

            if (members.isNotEmpty()) {
                members.forEach { phone ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(TalkoGreyLight)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = TalkoPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(phone, modifier = Modifier.weight(1f), color = TalkoTextPrimary)
                        IconButton(onClick = { members.remove(phone) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = TalkoGrey, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    hasAttemptedCreate = true
                    vm.createGroup(groupName, members.toList())
                },
                enabled = !state.isCreating && groupName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TalkoPrimary),
            ) {
                if (state.isCreating) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Create Group", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}
