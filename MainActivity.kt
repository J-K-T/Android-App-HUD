package com.ringhud

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.ringhud.hud.ui.HudScreen
import com.ringhud.hud.ui.PermissionScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // Minimal dark theme — HUD paints over everything
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    background = Color(0xFF0A0B0F),
                    surface    = Color(0xFF0A0B0F)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0A0B0F)
                ) {
                    var permissionsGranted by remember { mutableStateOf(false) }

                    if (permissionsGranted) {
                        HudScreen()
                    } else {
                        PermissionScreen(onAllGranted = { permissionsGranted = true })
                    }
                }
            }
        }
    }
}
