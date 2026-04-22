package com.ringhud.hud.ui

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.*

private val Cyan = Color(0xFF00E5FF)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionScreen(onAllGranted: () -> Unit) {

    val permissions = buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val multiPerms = rememberMultiplePermissionsState(permissions)

    LaunchedEffect(multiPerms.allPermissionsGranted) {
        if (multiPerms.allPermissionsGranted) onAllGranted()
    }

    if (multiPerms.allPermissionsGranted) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "RING HUD",
                color = Cyan, fontSize = 28.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 8.sp
            )
            Text(
                "Grant permissions to activate",
                color = Color(0xFF9CA3AF), fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PermRow("Camera",    Icons.Default.Camera,    multiPerms.permissions.firstOrNull { it.permission == Manifest.permission.CAMERA })
                PermRow("Microphone", Icons.Default.Mic,      multiPerms.permissions.firstOrNull { it.permission == Manifest.permission.RECORD_AUDIO })
                PermRow("Bluetooth", Icons.Default.Bluetooth, multiPerms.permissions.firstOrNull {
                    it.permission == Manifest.permission.BLUETOOTH_SCAN || it.permission == Manifest.permission.BLUETOOTH
                })
            }

            Button(
                onClick = { multiPerms.launchMultiplePermissionRequest() },
                colors = ButtonDefaults.buttonColors(containerColor = Cyan)
            ) {
                Text("GRANT PERMISSIONS", color = Color.Black,
                    fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermRow(label: String, icon: ImageVector, state: PermissionState?) {
    val granted = state?.status?.isGranted == true
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (granted) Color(0xFF4ADE80) else Color(0xFF9CA3AF),
            modifier = Modifier.size(20.dp))
        Text(label, color = if (granted) Color(0xFF4ADE80) else Color(0xFFE5E7EB),
            fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (granted) {
            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4ADE80),
                modifier = Modifier.size(18.dp))
        }
    }
}
