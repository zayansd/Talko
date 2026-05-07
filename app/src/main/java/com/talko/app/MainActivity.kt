package com.talko.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talko.app.core.navigation.TalkoNavHost
import com.talko.app.core.session.SessionDataStore
import com.talko.app.domain.repository.AuthRepository
import com.talko.app.ui.theme.TalkoTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionDataStore: SessionDataStore
    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val darkMode by sessionDataStore.darkModeEnabled
                .collectAsStateWithLifecycle(initialValue = false)

            TalkoTheme(forceDark = darkMode) {
                Surface {
                    TalkoNavHost()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Mark user as online when app comes to foreground
        authRepository.setOnlineStatus(true)
    }

    override fun onStop() {
        // Mark user as offline when app goes to background
        authRepository.setOnlineStatus(false)
        super.onStop()
    }
}
