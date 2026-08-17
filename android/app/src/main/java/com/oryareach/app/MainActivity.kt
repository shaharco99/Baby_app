package com.oryareach.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oryareach.core.network.auth.AuthRepository
import com.oryareach.core.network.auth.AuthState
import com.oryareach.core.settings.SettingsPreferences
import com.oryareach.core.ui.theme.OrYareachTheme
import org.koin.compose.koinInject

class MainActivity : AppCompatActivity() {
    private var pendingIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingIntent = intent

        setContent {
            OrYareachTheme {
                val auth = koinInject<AuthRepository>()
                val authState by auth.state.collectAsStateWithLifecycle(AuthState.Unknown)

                val settings = koinInject<SettingsPreferences>()
                val screenshotsBlocked by settings.screenshotsBlocked.collectAsStateWithLifecycle(true)
                LaunchedEffect(screenshotsBlocked) {
                    if (screenshotsBlocked) {
                        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }

                var passwordRecoveryPending by rememberSaveable { mutableStateOf(false) }
                // A tapped recovery-email link lands here as `pendingIntent` (cold start via
                // onCreate, or onNewIntent while already running — see below). Consuming it is
                // async (network + session setup), so `passwordRecoveryPending` flips to true
                // immediately to route to `ResetPasswordScreen` without a flash of the normal
                // signed-in app first, then `consumePasswordRecoveryLink` catches up in the
                // background.
                LaunchedEffect(pendingIntent) {
                    val current = pendingIntent ?: return@LaunchedEffect
                    if (auth.isPasswordRecoveryLink(current)) {
                        passwordRecoveryPending = true
                        auth.consumePasswordRecoveryLink(current)
                    }
                }

                SaharApp(
                    authState = authState,
                    passwordRecoveryPending = passwordRecoveryPending,
                    onPasswordRecoveryHandled = { passwordRecoveryPending = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntent = intent
    }
}
