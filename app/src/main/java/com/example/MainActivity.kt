package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.ui.AuthScreen
import com.example.ui.MainNavigation
import com.example.ui.theme.CipherChatTheme
import com.example.viewmodel.CipherViewModel

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: CipherViewModel

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(this)[CipherViewModel::class.java]

        requestNotificationPermissionIfNeeded()

        // Chat id passed by a tapped notification
        val notificationChatId = intent?.getStringExtra("chatId")

        setContent {
            val settings by viewModel.settings.collectAsState()
            val isLoggedIn by viewModel.isLoggedIn.collectAsState()

            val isDarkTheme = settings.themeMode == "DARK"

            // Keep status/navigation bar icon colors in sync with the APP theme
            // (the default only follows the system theme, which breaks visibility
            // when the app theme differs from the phone's)
            LaunchedEffect(isDarkTheme) {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkTheme) SystemBarStyle.dark(transparent)
                    else SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (isDarkTheme) SystemBarStyle.dark(transparent)
                    else SystemBarStyle.light(transparent, transparent)
                )
            }

            // Open the chat from the notification once we're logged in
            LaunchedEffect(isLoggedIn) {
                if (isLoggedIn && notificationChatId != null) {
                    viewModel.selectChat(notificationChatId)
                }
            }

            val isSessionLoading by viewModel.isSessionLoading.collectAsState()

            CipherChatTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when {
                        isSessionLoading -> SplashContent()
                        !isLoggedIn -> AuthScreen(viewModel = viewModel)
                        else -> MainNavigation(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::viewModel.isInitialized) viewModel.onAppForeground()
    }

    override fun onStop() {
        super.onStop()
        if (::viewModel.isInitialized) viewModel.onAppBackground()
    }

    @Composable
    private fun SplashContent() {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Image(
                painter = painterResource(R.drawable.cipher_logo),
                contentDescription = "CipherChat",
                modifier = Modifier.size(110.dp)
            )
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
