package com.talko.app.feature.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talko.app.data.local.dao.TalkoDao
import com.talko.app.data.local.entity.CallLogEntity
import com.talko.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class CallsViewModel @Inject constructor(
    private val dao: TalkoDao,
) : ViewModel() {

    val callLogs: StateFlow<List<CallLogEntity>> = dao.observeCallLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteLog(id: String) = viewModelScope.launch {
        dao.deleteCallLog(id)
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsTab(
    onAudioCall: (String) -> Unit,
    onVideoCall: (String) -> Unit,
    viewModel: CallsViewModel = hiltViewModel(),
) {
    val logs by viewModel.callLogs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calls", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TalkoTextPrimary) },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = TalkoTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TalkoBackground),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {}, containerColor = TalkoPrimary, shape = CircleShape) {
                Icon(Icons.Default.AddIcCall, contentDescription = "New Call", tint = Color.White)
            }
        },
        containerColor = TalkoBackground,
    ) { padding ->
        if (logs.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📞", fontSize = 64.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("No recent calls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = TalkoTextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text("Your call history will appear here", color = TalkoGrey, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item {
                    Text("RECENT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TalkoGrey, letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
                items(logs, key = { it.id }) { log ->
                    CallLogItem(
                        log = log,
                        onCall = { if (log.callType == "VIDEO") onVideoCall(log.peerName) else onAudioCall(log.peerName) },
                        onDelete = { viewModel.deleteLog(log.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CallLogItem(log: CallLogEntity, onCall: () -> Unit, onDelete: () -> Unit) {
    val isMissed = log.status == "MISSED" || log.status == "DECLINED"
    val timeStr = remember(log.timestamp) {
        val now = System.currentTimeMillis()
        val diff = now - log.timestamp
        when {
            diff < 86_400_000 -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(log.timestamp))
            diff < 604_800_000 -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(log.timestamp))
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(log.timestamp))
        }
    }
    val durationStr = if (log.durationSec > 0) {
        val m = log.durationSec / 60; val s = log.durationSec % 60
        if (m > 0) "${m}m ${s}s" else "${s}s"
    } else ""

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(if (isMissed) Color(0xFFFFEBEE) else TalkoPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(log.peerName.take(1).uppercase(), color = if (isMissed) TalkoError else TalkoPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(log.peerName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TalkoTextPrimary)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        imageVector = when {
                            isMissed -> Icons.Default.CallMissed
                            log.direction == "INCOMING" -> Icons.Default.CallReceived
                            else -> Icons.Default.CallMade
                        },
                        contentDescription = null,
                        tint = if (isMissed) TalkoError else TalkoGreen,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        buildString {
                            append(if (log.callType == "VIDEO") "Video" else "Voice")
                            append(" • ")
                            append(timeStr)
                            if (durationStr.isNotBlank()) append(" • $durationStr")
                        },
                        fontSize = 12.sp,
                        color = if (isMissed) TalkoError else TalkoGrey,
                    )
                }
            }
            IconButton(onClick = onCall) {
                Icon(
                    imageVector = if (log.callType == "VIDEO") Icons.Default.Videocam else Icons.Default.Call,
                    contentDescription = "Call back",
                    tint = TalkoPrimary,
                )
            }
        }
    }
}
