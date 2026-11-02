package com.example.cpen321application

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.cpen321application.core.notifications.createNotificationChannel
import com.example.cpen321application.core.notifications.invitedGameId
import com.example.cpen321application.navigation.AppNavigation
import com.example.cpen321application.ui.theme.CPEN321ApplicationTheme

class MainActivity : ComponentActivity() {
    // result ignored: if denied, notifications just aren't shown
    private val notificationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // the game of a tapped invite notification, AppNavigation opens it and clears it
    private var gameToOpen by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel(this)
        requestNotificationPermission()
        setContent {
            // when recreated (e.g. the phone was rotated) the game is already open
            if (savedInstanceState == null) gameToOpen = invitedGameId(intent)
            CPEN321ApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavigation(
                        modifier = Modifier.padding(innerPadding),
                        gameToOpen = gameToOpen,
                        onGameOpened = { gameToOpen = null }
                    )
                }
            }
        }
    }

    // Android 13+ asks the user before an app can show notifications
    // a notification tapped while the app is already open can arrive here instead of onCreate
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        gameToOpen = invitedGameId(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
