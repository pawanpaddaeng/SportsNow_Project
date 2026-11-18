package com.example.cpen321application

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.cpen321application.core.notifications.createNotificationChannel
import com.example.cpen321application.navigation.AppNavigation
import com.example.cpen321application.navigation.notificationRoute
import com.example.cpen321application.ui.screens.launch.LAUNCH_FADE_OUT_MS
import com.example.cpen321application.ui.screens.launch.LaunchAnimation
import com.example.cpen321application.ui.theme.CPEN321ApplicationTheme

// Kept in memory only, so it resets whenever the app is closed (e.g. swiped away from recent apps)
// and the launch animation plays again on the next fresh open, but not when coming back from the background
private var launchAnimationPlayed = false

class MainActivity : ComponentActivity() {
    // result ignored: if denied, notifications just aren't shown
    private val notificationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // the screen a tapped notification asks for, AppNavigation opens it and clears it
    private var routeToOpen by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // dark icons even in dark mode, because the app is always light. Same colors as the default light style
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(
                Color.argb(0xe6, 0xFF, 0xFF, 0xFF),
                Color.argb(0x80, 0x1b, 0x1b, 0x1b)
            )
        )
        createNotificationChannel(this)
        requestNotificationPermission()
        // when recreated (e.g. the phone was rotated) the game or chat is already open
        if (savedInstanceState == null) readNotification(intent)
        setContent {
            CPEN321ApplicationTheme {
                // saveable, so rotating the phone doesn't replay the launch animation
                var showLaunchAnimation by rememberSaveable { mutableStateOf(!launchAnimationPlayed) }
                Box(Modifier.fillMaxSize()) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        AppNavigation(
                            modifier = Modifier.padding(innerPadding),
                            routeToOpen = routeToOpen,
                            onRouteOpened = { routeToOpen = null }
                        )
                    }
                    // plays over the app while it loads underneath, then fades away
                    AnimatedVisibility(
                        visible = showLaunchAnimation,
                        enter = EnterTransition.None,
                        exit = fadeOut(tween(durationMillis = LAUNCH_FADE_OUT_MS))
                    ) {
                        LaunchAnimation(onFinished = {
                            showLaunchAnimation = false
                            launchAnimationPlayed = true
                        })
                    }
                }
            }
        }
    }

    // a notification tapped while the app is already open can arrive here instead of onCreate
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readNotification(intent)
    }

    // what a tapped notification should open, if the app was opened by one
    private fun readNotification(intent: Intent) {
        routeToOpen = notificationRoute { key -> intent.getStringExtra(key) }
    }

    // Android 13+ asks the user before an app can show notifications
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
