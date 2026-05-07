package com.talko.app.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.*
import androidx.navigation.compose.*
import com.talko.app.domain.model.CallType
import com.talko.app.feature.auth.*
import com.talko.app.feature.call.CallScreen
import com.talko.app.feature.call.CallViewModel
import com.talko.app.feature.call.IncomingCallScreen
import com.talko.app.feature.call.IncomingCallViewModel
import com.talko.app.feature.calls.CallsTab
import com.talko.app.feature.chat.ChatScreen
import com.talko.app.feature.chat.ChatViewModel
import com.talko.app.feature.group.CreateGroupScreen
import com.talko.app.feature.group.GroupListScreen
import com.talko.app.feature.home.ChatListScreen
import com.talko.app.feature.profile.ProfileScreen
import com.talko.app.feature.settings.SettingsScreen
import com.talko.app.feature.splash.SplashScreen

private sealed class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    object Chats   : BottomTab("tab_chats",   "Chats",   Icons.Default.Chat)
    object Calls   : BottomTab("tab_calls",   "Calls",   Icons.Default.Call)
    object Groups  : BottomTab("tab_groups",  "Groups",  Icons.Default.Group)
    object Profile : BottomTab("tab_profile", "Profile", Icons.Default.Person)
}

private val bottomTabs = listOf(BottomTab.Chats, BottomTab.Calls, BottomTab.Groups, BottomTab.Profile)

@Composable
fun TalkoNavHost() {
    val nav = rememberNavController()
    val authVm: AuthViewModel = hiltViewModel()
    val auth by authVm.uiState.collectAsState()

    // Incoming call ViewModel — singleton, lives as long as the NavHost
    val incomingVm: IncomingCallViewModel = hiltViewModel()
    val incomingCall by incomingVm.incomingCall.collectAsState()

    // Start listening once the user is authenticated
    LaunchedEffect(auth.userId) {
        if (auth.userId != null) incomingVm.startListening()
    }

    // Show incoming call screen on top of everything
    if (incomingCall != null) {
        val call = incomingCall!!
        IncomingCallScreen(
            callerName = call.callerName,
            callType   = call.callType,
            onAccept   = {
                incomingVm.accept(call.callId, call.callType)
                val type = if (call.callType == CallType.VIDEO) "video" else "audio"
                nav.navigate("call/$type/${call.callerName}")
            },
            onDecline  = { incomingVm.decline(call.callId) },
        )
        return
    }

    NavHost(navController = nav, startDestination = "splash") {

        // ── Splash ────────────────────────────────────────────────────────────
        composable("splash") {
            SplashScreen(
                onFinished = {
                    val dest = when {
                        auth.userId == null    -> "auth_login"
                        !auth.profileCompleted -> "profile_setup"
                        else                   -> "home"
                    }
                    nav.navigate(dest) { popUpTo("splash") { inclusive = true } }
                },
            )
        }

        // ── Auth ──────────────────────────────────────────────────────────────
        composable("auth_login") {
            val state by authVm.uiState.collectAsState()
            LaunchedEffect(state.userId, state.profileCompleted) {
                if (state.userId != null) {
                    val dest = if (state.profileCompleted) "home" else "profile_setup"
                    nav.navigate(dest) { popUpTo("auth_login") { inclusive = true } }
                }
            }
            AuthLoginScreen(
                state = state,
                onEmailChange = authVm::onEmailChange,
                onPasswordChange = authVm::onPasswordChange,
                onConfirmPasswordChange = authVm::onConfirmPasswordChange,
                onTogglePasswordVisibility = authVm::togglePasswordVisibility,
                onToggleConfirmPasswordVisibility = authVm::toggleConfirmPasswordVisibility,
                onToggleMode = authVm::toggleMode,
                onSignIn = authVm::signIn,
                onForgotPassword = authVm::sendPasswordReset,
            )
        }

        composable("profile_setup") {
            val state by authVm.uiState.collectAsState()
            LaunchedEffect(state.profileCompleted) {
                if (state.profileCompleted) nav.navigate("home") { popUpTo("profile_setup") { inclusive = true } }
            }
            ProfileSetupScreen(state = state, onSave = authVm::saveProfile)
        }

        // ── Home ──────────────────────────────────────────────────────────────
        composable("home") {
            HomeShell(
                onOpenChat       = { id, title -> nav.navigate("chat/$id/$title") },
                onStartAudioCall = { peer -> nav.navigate("call/audio/$peer") },
                onStartVideoCall = { peer -> nav.navigate("call/video/$peer") },
                onOpenSettings   = { nav.navigate("settings") },
                onLoggedOut      = { nav.navigate("auth_login") { popUpTo(0) { inclusive = true } } },
            )
        }

        // ── Chat detail ───────────────────────────────────────────────────────
        composable(
            route = "chat/{chatId}/{title}",
            arguments = listOf(navArgument("chatId") { type = NavType.StringType }, navArgument("title") { type = NavType.StringType }),
        ) { back ->
            val chatId = back.arguments?.getString("chatId").orEmpty()
            val title  = back.arguments?.getString("title").orEmpty()
            val vm: ChatViewModel = hiltViewModel()
            val authState by authVm.uiState.collectAsState()
            ChatScreen(
                chatId = chatId, title = title,
                currentUserId = authState.userId.orEmpty(),
                viewModel = vm,
                onBack = { nav.popBackStack() },
                onAudioCall = { nav.navigate("call/audio/$title") },
                onVideoCall = { nav.navigate("call/video/$title") },
            )
        }

        // ── Call ──────────────────────────────────────────────────────────────
        composable(
            route = "call/{type}/{peer}",
            arguments = listOf(navArgument("type") { type = NavType.StringType }, navArgument("peer") { type = NavType.StringType }),
        ) { back ->
            val type     = back.arguments?.getString("type").orEmpty()
            val peer     = back.arguments?.getString("peer").orEmpty()
            val callType = if (type == "video") CallType.VIDEO else CallType.AUDIO
            val vm: CallViewModel = hiltViewModel()
            LaunchedEffect(type, peer) { vm.start(peer, callType) }
            CallScreen(peerName = peer, callType = callType, viewModel = vm, onEnd = { nav.popBackStack() })
        }

        // ── Settings ──────────────────────────────────────────────────────────
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}

// ── Home shell ────────────────────────────────────────────────────────────────

@Composable
private fun HomeShell(
    onOpenChat: (String, String) -> Unit,
    onStartAudioCall: (String) -> Unit,
    onStartVideoCall: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val innerNav = rememberNavController()
    val chatVm: ChatViewModel = hiltViewModel()
    val navBackStack by innerNav.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            innerNav.navigate(tab.route) {
                                popUpTo(innerNav.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon  = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            NavHost(navController = innerNav, startDestination = BottomTab.Chats.route) {

                composable(BottomTab.Chats.route) {
                    val chatListState by chatVm.chatListState.collectAsState()
                    ChatListScreen(
                        chats = chatListState.chats.filter { !it.isGroup },
                        onOpenChat = { chat -> onOpenChat(chat.id, chat.title) },
                        searchQuery = chatListState.searchQuery,
                        onSearchQueryChange = chatVm::onSearchQueryChange,
                        onClearSearch = chatVm::clearSearch,
                        onNewChatCreated = { chatId, title ->
                            chatVm.onNewChat(chatId, title)
                            onOpenChat(chatId, title)
                        },
                    )
                }

                composable(BottomTab.Calls.route) {
                    CallsTab(onAudioCall = onStartAudioCall, onVideoCall = onStartVideoCall)
                }

                composable(BottomTab.Groups.route) {
                    val groupInnerNav = rememberNavController()
                    NavHost(navController = groupInnerNav, startDestination = "group_list") {
                        composable("group_list") {
                            GroupListScreen(
                                onOpenGroup   = { group -> onOpenChat(group.id, group.title) },
                                onCreateGroup = { groupInnerNav.navigate("group_create") },
                            )
                        }
                        composable("group_create") {
                            CreateGroupScreen(onBack = { groupInnerNav.popBackStack() }, onCreated = { groupInnerNav.popBackStack() })
                        }
                    }
                }

                composable(BottomTab.Profile.route) {
                    ProfileScreen(
                        onLoggedOut = onLoggedOut,
                        onOpenSettings = onOpenSettings,
                    )
                }
            }
        }
    }
}
