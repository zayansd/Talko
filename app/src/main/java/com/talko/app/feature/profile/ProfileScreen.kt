package com.talko.app.feature.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.talko.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLoggedOut: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var editMode by remember { mutableStateOf(false) }
    var editName by remember(state.fullName) { mutableStateOf(state.fullName) }
    var editBio  by remember(state.bio)      { mutableStateOf(state.bio) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadProfilePhoto(it) }
    }

    LaunchedEffect(state.loggedOut) { if (state.loggedOut) onLoggedOut() }

    LaunchedEffect(state.error) {
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.snackMessage) {
        state.snackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold, color = TalkoTextPrimary) },
                actions = {
                    if (editMode) {
                        TextButton(
                            onClick = { viewModel.saveProfile(editName, editBio); editMode = false },
                            enabled = !state.isSaving,
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = TalkoPrimary)
                            } else {
                                Text("Save", color = TalkoPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        TextButton(onClick = { editMode = false }) {
                            Text("Cancel", color = TalkoGrey)
                        }
                    } else {
                        IconButton(onClick = { editMode = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TalkoPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TalkoBackground),
            )
        },
        containerColor = TalkoBackground,
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TalkoPrimary)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // Offline banner
            if (state.isOffline) {
                Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFFFFF3E0)) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WifiOff, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("You're offline. Showing cached data.", fontSize = 13.sp, color = Color(0xFFE65100))
                    }
                }
            }

            // Profile header card
            Surface(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // ── Avatar with tap-to-change ─────────────────────────────
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .clickable { photoPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.photoUrl.isNotBlank()) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(state.photoUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Profile photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                loading = {
                                    Box(modifier = Modifier.fillMaxSize().background(TalkoPrimary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = TalkoPrimary, strokeWidth = 2.dp)
                                    }
                                },
                                error = {
                                    // Fallback to initials if image fails
                                    Box(modifier = Modifier.fillMaxSize().background(TalkoPrimary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                        Text(state.fullName.take(1).uppercase().ifBlank { "?" }, color = TalkoPrimary, fontWeight = FontWeight.Bold, fontSize = 40.sp)
                                    }
                                },
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(TalkoPrimary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Text(state.fullName.take(1).uppercase().ifBlank { "?" }, color = TalkoPrimary, fontWeight = FontWeight.Bold, fontSize = 40.sp)
                            }
                        }

                        // Camera overlay hint
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(TalkoPrimary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Change photo", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    if (editMode) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Full name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editBio,
                            onValueChange = { editBio = it },
                            label = { Text("Bio / Status") },
                            modifier = Modifier.fillMaxWidth().height(90.dp),
                            shape = RoundedCornerShape(12.dp),
                        )
                    } else {
                        Text(state.fullName.ifBlank { "Your Name" }, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TalkoTextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(state.email.ifBlank { "No email set" }, fontSize = 14.sp, color = TalkoGrey)
                        if (state.bio.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(state.bio, fontSize = 13.sp, color = TalkoTextSecondary)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatChip(label = "Messages", value = "—")
                        StatChip(label = "Calls",    value = "—")
                        StatChip(label = "Groups",   value = "—")
                    }
                }
            }

            // Settings sections
            SettingsSection(title = "Account") {
                SettingsItem(icon = Icons.Default.Person,   label = "Edit Profile",  onClick = { editMode = true })
                SettingsItem(icon = Icons.Default.Lock,     label = "Privacy",       onClick = onOpenSettings)
                SettingsItem(icon = Icons.Default.Security, label = "Security",      onClick = onOpenSettings)
            }
            SettingsSection(title = "Notifications") {
                SettingsItem(icon = Icons.Default.Notifications, label = "Message Notifications", onClick = onOpenSettings)
                SettingsItem(icon = Icons.Default.Call,          label = "Call Notifications",    onClick = onOpenSettings)
            }
            SettingsSection(title = "Appearance") {
                SettingsItem(icon = Icons.Default.DarkMode, label = "Dark Mode", onClick = onOpenSettings)
                SettingsItem(icon = Icons.Default.Language, label = "Language",  onClick = onOpenSettings)
            }
            SettingsSection(title = "Support") {
                SettingsItem(icon = Icons.Default.Help, label = "Help & FAQ",  onClick = {})
                SettingsItem(icon = Icons.Default.Info, label = "About Talko", onClick = {})
            }

            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFFFEBEE),
                onClick = viewModel::logout,
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Logout, contentDescription = "Logout", tint = TalkoError)
                    Spacer(Modifier.width(14.dp))
                    Text("Log Out", color = TalkoError, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = TalkoGreyLight) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TalkoPrimary)
            Text(label, fontSize = 11.sp, color = TalkoGrey)
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TalkoGrey, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 1.dp) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(TalkoPrimary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = TalkoPrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text(label, fontSize = 15.sp, color = TalkoTextPrimary, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TalkoGrey, modifier = Modifier.size(18.dp))
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 66.dp), color = TalkoGreyLight, thickness = 0.5.dp)
}
