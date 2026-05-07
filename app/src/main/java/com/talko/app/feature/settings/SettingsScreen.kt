package com.talko.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talko.app.core.session.SessionDataStore
import com.talko.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class SettingsUiState(
    val notificationsEnabled: Boolean = true,
    val callNotificationsEnabled: Boolean = true,
    val darkMode: Boolean = false,
    val readReceipts: Boolean = true,
    val lastSeenVisible: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionDataStore: SessionDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionDataStore.notificationsEnabled.collect { enabled ->
                _uiState.update { it.copy(notificationsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            sessionDataStore.darkModeEnabled.collect { enabled ->
                _uiState.update { it.copy(darkMode = enabled) }
            }
        }
    }

    fun toggleNotifications(enabled: Boolean) = viewModelScope.launch {
        sessionDataStore.setNotificationsEnabled(enabled)
        _uiState.update { it.copy(notificationsEnabled = enabled) }
    }

    fun toggleCallNotifications(enabled: Boolean) {
        _uiState.update { it.copy(callNotificationsEnabled = enabled) }
    }

    fun toggleDarkMode(enabled: Boolean) = viewModelScope.launch {
        sessionDataStore.setDarkModeEnabled(enabled)
        _uiState.update { it.copy(darkMode = enabled) }
    }

    fun toggleReadReceipts(enabled: Boolean) {
        _uiState.update { it.copy(readReceipts = enabled) }
    }

    fun toggleLastSeen(enabled: Boolean) {
        _uiState.update { it.copy(lastSeenVisible = enabled) }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = TalkoTextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TalkoBackground),
            )
        },
        containerColor = TalkoBackground,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSection(title = "Notifications") {
                ToggleItem(
                    icon = Icons.Default.Notifications,
                    label = "Message Notifications",
                    checked = state.notificationsEnabled,
                    onToggle = viewModel::toggleNotifications,
                )
                ToggleItem(
                    icon = Icons.Default.Call,
                    label = "Call Notifications",
                    checked = state.callNotificationsEnabled,
                    onToggle = viewModel::toggleCallNotifications,
                )
            }

            SettingsSection(title = "Privacy") {
                ToggleItem(
                    icon = Icons.Default.DoneAll,
                    label = "Read Receipts",
                    checked = state.readReceipts,
                    onToggle = viewModel::toggleReadReceipts,
                )
                ToggleItem(
                    icon = Icons.Default.AccessTime,
                    label = "Show Last Seen",
                    checked = state.lastSeenVisible,
                    onToggle = viewModel::toggleLastSeen,
                )
            }

            SettingsSection(title = "Appearance") {
                ToggleItem(
                    icon = Icons.Default.DarkMode,
                    label = "Dark Mode",
                    checked = state.darkMode,
                    onToggle = viewModel::toggleDarkMode,
                )
            }

            SettingsSection(title = "About") {
                InfoItem(icon = Icons.Default.Info,        label = "Version",       value = "2.0.4")
                InfoItem(icon = Icons.Default.Description, label = "Terms of Service", value = "")
                InfoItem(icon = Icons.Default.PrivacyTip,  label = "Privacy Policy",   value = "")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TalkoGrey,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 1.dp) {
            Column { content() }
        }
    }
}

@Composable
private fun ToggleItem(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(TalkoPrimary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = TalkoPrimary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 15.sp, color = TalkoTextPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TalkoPrimary),
        )
    }
    HorizontalDivider(modifier = Modifier.padding(start = 66.dp), color = TalkoGreyLight, thickness = 0.5.dp)
}

@Composable
private fun InfoItem(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(TalkoPrimary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = TalkoPrimary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 15.sp, color = TalkoTextPrimary, modifier = Modifier.weight(1f))
        if (value.isNotBlank()) {
            Text(value, fontSize = 13.sp, color = TalkoGrey)
        } else {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TalkoGrey, modifier = Modifier.size(18.dp))
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 66.dp), color = TalkoGreyLight, thickness = 0.5.dp)
}
