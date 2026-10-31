package com.wheelpad.app

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.wheelpad.app.data.repository.ProfileRepository
import com.wheelpad.app.network.ControlSyncClient
import com.wheelpad.app.network.UdpFastStreamClient
import com.wheelpad.app.sensor.GyroSteeringManager
import com.wheelpad.app.ui.controls.ControlEditorViewModel
import com.wheelpad.app.ui.driving.DrivingScreen

class MainActivity : ComponentActivity() {

    private lateinit var profileRepository: ProfileRepository
    private lateinit var syncClient: ControlSyncClient
    private lateinit var udpClient: UdpFastStreamClient
    private lateinit var gyroManager: GyroSteeringManager
    private lateinit var controlEditorViewModel: ControlEditorViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Keep Screen Awake for gaming
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 2. Hide Android System Bars (No time bar, no clock, no status bar, true immersive sticky)
        setImmersiveSticky()

        // 3. Initialize Repositories and Clients
        profileRepository = ProfileRepository(this)
        syncClient = ControlSyncClient(profileRepository)
        udpClient = UdpFastStreamClient()
        gyroManager = GyroSteeringManager(this)
        controlEditorViewModel = ControlEditorViewModel(profileRepository, syncClient)

        // 4. Retrieve last connected IP or start auto-discovery
        val prefs = getSharedPreferences("wheelpad_prefs", Context.MODE_PRIVATE)
        val savedHost = prefs.getString("last_host_ip", null)

        if (!savedHost.isNullOrEmpty()) {
            syncClient.connect(savedHost)
            udpClient.initialize(savedHost)
        }

        // 5. Automatic UDP LAN Auto-Discovery (Connects phone to PC with zero typing!)
        udpClient.startAutoDiscovery { discoveredIp, tcpPort, udpPort ->
            prefs.edit().putString("last_host_ip", discoveredIp).apply()
            syncClient.connect(discoveredIp, tcpPort)
            udpClient.initialize(discoveredIp, udpPort)
        }

        // 6. Handle QR Code camera scanner / deep link URL if launched via link
        handleIntent(intent)

        setContent {
            var showConnectDialog by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF080B10))
            ) {
                // Pure Immersive Driving Screen HUD (Zero bottom navigation bar)
                DrivingScreen(
                    viewModel = controlEditorViewModel,
                    udpClient = udpClient,
                    syncClient = syncClient,
                    onOpenConnectDialog = { showConnectDialog = true }
                )

                // Quick Connect / Pairing Modal
                if (showConnectDialog) {
                    PairingDialog(
                        currentHost = prefs.getString("last_host_ip", "192.168.1.100") ?: "192.168.1.100",
                        syncClient = syncClient,
                        udpClient = udpClient,
                        onDismiss = { showConnectDialog = false },
                        onSaveHost = { newHost ->
                            prefs.edit().putString("last_host_ip", newHost).apply()
                            syncClient.connect(newHost)
                            udpClient.initialize(newHost)
                            showConnectDialog = false
                        }
                    )
                }
            }
        }
    }

    private fun setImmersiveSticky() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    override fun onResume() {
        super.onResume()
        setImmersiveSticky()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setImmersiveSticky()
        }
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        val ip = uri.getQueryParameter("ip")
        if (!ip.isNullOrBlank()) {
            val tcp = uri.getQueryParameter("tcp")?.toIntOrNull() ?: 4243
            val udp = uri.getQueryParameter("udp")?.toIntOrNull() ?: 4242
            val prefs = getSharedPreferences("wheelpad_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("last_host_ip", ip).apply()
            syncClient.connect(ip, tcp)
            udpClient.initialize(ip, udp)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        gyroManager.stop()
        syncClient.disconnect()
        udpClient.close()
    }
}

@Composable
fun PairingDialog(
    currentHost: String,
    syncClient: ControlSyncClient,
    udpClient: UdpFastStreamClient,
    onDismiss: () -> Unit,
    onSaveHost: (String) -> Unit
) {
    var hostText by remember { mutableStateOf(currentHost) }
    val isConnected by syncClient.isConnected.collectAsState()
    val statusMsg by syncClient.statusMessage.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD0030E0C))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .widthIn(min = 460.dp, max = 540.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xF8051E1C))
                .border(1.5.dp, Color(0xFFCCFF00), RoundedCornerShape(4.dp))
                .clickable(enabled = false) {}
        ) {
            // Full-Bleed Solid Volt Lime Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFCCFF00))
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PC CONNECTION // PAIRING",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "[ESC] CLOSE",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable(onClick = onDismiss)
                    )
                }
            }

            // Body Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Connection Status
                Text(
                    text = if (isConnected) "STATUS: CONNECTED TO WINDOWS RIG" else "STATUS: $statusMsg",
                    color = if (isConnected) Color(0xFFCCFF00) else Color(0xCCFFFFFF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                // Option A: Auto-Discovery Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF0A0A0A))
                        .border(1.2.dp, Color(0xFFCCFF00), RoundedCornerShape(4.dp))
                        .clickable {
                            udpClient.startAutoDiscovery { ip, tcpPort, udpPort ->
                                hostText = ip
                                onSaveHost(ip)
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "AUTO-DISCOVER WINDOWS PC",
                        color = Color(0xFFCCFF00),
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp
                    )
                }

                // Option B: Scan QR Code Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFCCFF00))
                        .clickable {
                            try {
                                val scanIntent = android.content.Intent("com.google.zxing.client.android.SCAN").apply {
                                    putExtra("SCAN_MODE", "QR_CODE_MODE")
                                }
                                context.startActivity(scanIntent)
                            } catch (e: Exception) {
                                try {
                                    val camIntent = android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                                    context.startActivity(camIntent)
                                } catch (ex: Exception) {
                                    // Open default camera
                                }
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "SCAN PC QR CODE (CAMERA)",
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp
                    )
                }

                Text(
                    "// OR ENTER IP MANUALLY",
                    color = Color(0x80FFFFFF),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                OutlinedTextField(
                    value = hostText,
                    onValueChange = { hostText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFCCFF00),
                        unfocusedBorderColor = Color(0x60FFFFFF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                            .clickable { onSaveHost(hostText) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("CONNECT", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x30FFFFFF))
                            .clickable(onClick = onDismiss)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("CANCEL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}
