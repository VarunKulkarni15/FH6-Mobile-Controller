package com.wheelpad.app

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.wheelpad.app.data.repository.ProfileRepository
import com.wheelpad.app.network.ControlSyncClient
import com.wheelpad.app.network.UdpFastStreamClient
import com.wheelpad.app.sensor.GyroSteeringManager
import com.wheelpad.app.ui.controls.ControlEditorViewModel
import com.wheelpad.app.ui.driving.DrivingScreen
import com.wheelpad.app.util.NetworkDiagnosticsHelper
import com.wheelpad.app.util.NotificationHelper
import kotlinx.coroutines.launch

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

        // 4. Retrieve auto-detected USB/LAN IP or saved IP
        val prefs = getSharedPreferences("wheelpad_prefs", Context.MODE_PRIVATE)
        val savedHost = prefs.getString("last_host_ip", null)
        val detectedHost = getAutoDetectedHost(this)
        val targetHost = detectedHost ?: savedHost

        if (!targetHost.isNullOrEmpty()) {
            syncClient.connect(targetHost)
            udpClient.initialize(targetHost)
        }

        // 5. Automatic UDP LAN & USB Auto-Discovery (Connects phone to PC with zero typing!)
        udpClient.startAutoDiscovery { discoveredIp, tcpPort, udpPort ->
            prefs.edit().putString("last_host_ip", discoveredIp).apply()
            syncClient.connect(discoveredIp, tcpPort)
            udpClient.initialize(discoveredIp, udpPort)
        }

        // 6. Handle QR Code camera scanner / deep link URL if launched via link
        handleIntent(intent)

        // 7. System Push Notifications Setup & Permission Request (Android 13+)
        NotificationHelper.createNotificationChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        lifecycleScope.launch {
            var wasConnected = false
            syncClient.isConnected.collect { connected ->
                if (connected) {
                    wasConnected = true
                    NotificationHelper.showStatusNotification(
                        this@MainActivity,
                        "FH6 Companion Connected",
                        "Connected to PC • 120Hz Ultra-Low Latency Active"
                    )
                } else if (wasConnected) {
                    NotificationHelper.showStatusNotification(
                        this@MainActivity,
                        "FH6 Companion Disconnected",
                        "Controller connection lost • Standby mode"
                    )
                }
            }
        }

        lifecycleScope.launch {
            var lastGameName = ""
            syncClient.gameStatus.collect { game ->
                if (game.isRunning && game.gameName.isNotBlank() && game.gameName != lastGameName) {
                    lastGameName = game.gameName
                    NotificationHelper.showStatusNotification(
                        this@MainActivity,
                        "${game.gameName} Active",
                        "${game.gameName} detected (PID ${game.processId})! FastStream synchronized."
                    )
                } else if (!game.isRunning && lastGameName.isNotBlank()) {
                    NotificationHelper.showStatusNotification(
                        this@MainActivity,
                        "$lastGameName Exited",
                        "Game closed. Controller in standby."
                    )
                    lastGameName = ""
                }
            }
        }

        setContent {
            var showConnectDialog by remember { mutableStateOf(false) }
            var showDiagnosticsDialog by remember { mutableStateOf(false) }

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
                    onOpenConnectDialog = { showConnectDialog = true },
                    onOpenDiagnosticsDialog = { showDiagnosticsDialog = true }
                )

                // Quick Connect / Pairing Modal
                if (showConnectDialog) {
                    PairingDialog(
                        currentHost = prefs.getString("last_host_ip", "192.168.1.100") ?: "192.168.1.100",
                        syncClient = syncClient,
                        udpClient = udpClient,
                        onDismiss = { showConnectDialog = false },
                        onOpenDiagnostics = { showDiagnosticsDialog = true },
                        onSaveHost = { newHost ->
                            prefs.edit().putString("last_host_ip", newHost).apply()
                            syncClient.connect(newHost)
                            udpClient.initialize(newHost)
                            showConnectDialog = false
                        }
                    )
                }

                // App Requirements & Network Health Diagnostics Modal
                if (showDiagnosticsDialog) {
                    RequirementsDiagnosticsDialog(
                        onDismiss = { showDiagnosticsDialog = false },
                        onConnectToIp = { ip ->
                            prefs.edit().putString("last_host_ip", ip).apply()
                            syncClient.connect(ip)
                            udpClient.initialize(ip)
                            showDiagnosticsDialog = false
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

fun getAutoDetectedHost(context: Context? = null): String? {
    // 1. Try default gateway from ConnectivityManager (100% reliable for USB tethering & Wi-Fi routers)
    if (context != null) {
        val report = NetworkDiagnosticsHelper.runDiagnostics(context)
        if (!report.gatewayIp.isNullOrBlank()) {
            return report.gatewayIp
        }
    }

    // 2. Try /proc/net/arp (fallback on devices with readable /proc)
    try {
        val file = java.io.File("/proc/net/arp")
        if (file.exists()) {
            file.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val tokens = line.trim().split(Regex("\\s+"))
                    if (tokens.size >= 6 && tokens[0] != "IP") {
                        val ip = tokens[0]
                        val flags = tokens[2]
                        val device = tokens[5]
                        if (flags == "0x2" && (device.contains("rndis") || device.contains("usb") || device.contains("wlan") || device.contains("eth"))) {
                            return ip
                        }
                    }
                }
            }
        }
    } catch (e: Exception) { }

    return null
}

@Composable
fun PairingDialog(
    currentHost: String,
    syncClient: ControlSyncClient,
    udpClient: UdpFastStreamClient,
    onDismiss: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onSaveHost: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val diagReport = remember { NetworkDiagnosticsHelper.runDiagnostics(context) }
    val initialHost = remember { getAutoDetectedHost(context) ?: currentHost }
    var hostText by remember { mutableStateOf(initialHost) }
    val isConnected by syncClient.isConnected.collectAsState()
    val statusMsg by syncClient.statusMessage.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD0030E0C))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.68f)
                .widthIn(min = 460.dp, max = 560.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xF8051E1C))
                .border(1.5.dp, Color(0xFFCCFF00), RoundedCornerShape(6.dp))
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
                        text = "CLOSE",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable(onClick = onDismiss)
                    )
                }
            }

            // Body Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
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

                // Live Warning Banners for VPN / Cellular
                if (diagReport.isVpnActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x35FFB300))
                            .border(1.dp, Color(0xFFFFB300), RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "⚠️ VPN DETECTED: Disconnect your VPN or enable Local LAN Bypass in your VPN app so your phone can reach your PC.",
                            color = Color(0xFFFFD54F),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 14.sp
                        )
                    }
                }

                if (diagReport.isCellularOnly) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x35FF5252))
                            .border(1.dp, Color(0xFFFF5252), RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "⚠️ CELLULAR DATA ONLY: Wi-Fi is disconnected. Connect to the same Wi-Fi router as your PC, or enable USB Tethering.",
                            color = Color(0xFFFF8A80),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 14.sp
                        )
                    }
                }

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

                // Option B: In-App Play Services QR Scanner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFCCFF00))
                        .clickable {
                            try {
                                val options = GmsBarcodeScannerOptions.Builder()
                                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                    .enableAutoZoom()
                                    .build()
                                val scanner = GmsBarcodeScanning.getClient(context, options)
                                scanner.startScan()
                                    .addOnSuccessListener { barcode ->
                                        val raw = barcode.rawValue ?: return@addOnSuccessListener
                                        val uri = android.net.Uri.parse(raw)
                                        val ip = uri.getQueryParameter("ip") ?: if (!raw.startsWith("http")) raw.trim() else null
                                        if (!ip.isNullOrBlank()) {
                                            val tcp = uri.getQueryParameter("tcp")?.toIntOrNull() ?: 4243
                                            val udp = uri.getQueryParameter("udp")?.toIntOrNull() ?: 4242
                                            hostText = ip
                                            onSaveHost(ip)
                                            syncClient.connect(ip, tcp)
                                            udpClient.initialize(ip, udp)
                                        }
                                    }
                                    .addOnFailureListener {
                                        // Scan canceled or failed
                                    }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "IN-APP SCAN PC QR CODE",
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp
                    )
                }

                // Detected Host Quick-Chip (if available)
                if (!diagReport.gatewayIp.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x3000E676))
                            .border(1.dp, Color(0xFF00E676), RoundedCornerShape(4.dp))
                            .clickable {
                                hostText = diagReport.gatewayIp
                                onSaveHost(diagReport.gatewayIp)
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DETECTED PC HOST: ${diagReport.gatewayIp}",
                            color = Color(0xFF00E676),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "USE & CONNECT >",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "// OR ENTER IP MANUALLY",
                        color = Color(0x80FFFFFF),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Text(
                        "[?] REQUIREMENTS & HEALTH",
                        color = Color(0xFFCCFF00),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable(onClick = onOpenDiagnostics)
                    )
                }

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

/**
 * App Requirements & Network Health Diagnostics Modal Dialog
 */
@Composable
fun RequirementsDiagnosticsDialog(
    onDismiss: () -> Unit,
    onConnectToIp: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val report = remember { NetworkDiagnosticsHelper.runDiagnostics(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD8020B0A))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .widthIn(min = 480.dp, max = 580.dp)
                .heightIn(max = 380.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xF8061F1D))
                .border(1.5.dp, Color(0xFFCCFF00), RoundedCornerShape(6.dp))
                .clickable(enabled = false) {}
        ) {
            // Header
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
                        text = "APP REQUIREMENTS // NETWORK HEALTH",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "CLOSE",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable(onClick = onDismiss)
                    )
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Warning Banners (if any)
                report.warnings.forEach { warning ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x30FF5252))
                            .border(1.dp, Color(0xFFFF5252), RoundedCornerShape(4.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = warning,
                            color = Color(0xFFFF8A80),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 14.sp
                        )
                    }
                }

                // Live Diagnostic Status Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Card 1: Network Mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x40000000))
                            .border(1.dp, if (report.isWifiConnected || report.isEthernetOrUsbConnected) Color(0xFF00E676) else Color(0xFFFF5252), RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            Text("NETWORK", color = Color(0x99FFFFFF), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = when {
                                    report.isEthernetOrUsbConnected -> "USB / ETH"
                                    report.isWifiConnected -> "WI-FI"
                                    report.isCellularOnly -> "CELLULAR"
                                    else -> "OFFLINE"
                                },
                                color = if (report.isWifiConnected || report.isEthernetOrUsbConnected) Color(0xFF00E676) else Color(0xFFFF5252),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(report.localIp ?: "No local IP", color = Color(0xCCFFFFFF), fontSize = 9.sp)
                        }
                    }

                    // Card 2: VPN Status
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x40000000))
                            .border(1.dp, if (!report.isVpnActive) Color(0xFF00E676) else Color(0xFFFFB300), RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            Text("VPN SHIELD", color = Color(0x99FFFFFF), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (!report.isVpnActive) "CLEAN (OFF)" else "ACTIVE (WARN)",
                                color = if (!report.isVpnActive) Color(0xFF00E676) else Color(0xFFFFB300),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(if (!report.isVpnActive) "LAN unblocked" else "Blocks LAN discovery", color = Color(0xCCFFFFFF), fontSize = 9.sp)
                        }
                    }

                    // Card 3: Detected Host / Gateway
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x40000000))
                            .border(1.dp, if (report.gatewayIp != null) Color(0xFFCCFF00) else Color(0x60FFFFFF), RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            Text("GATEWAY / PC", color = Color(0x99FFFFFF), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = report.gatewayIp ?: "Scanning...",
                                color = if (report.gatewayIp != null) Color(0xFFCCFF00) else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                            if (report.gatewayIp != null) {
                                Text("Click Connect below", color = Color(0xCCFFFFFF), fontSize = 9.sp)
                            }
                        }
                    }
                }

                // App Requirements Checklist
                Text("// 4 CORE CONNECTION REQUIREMENTS", color = Color(0xFFCCFF00), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)

                listOf(
                    "1. WINDOWS COMPANION RUNNING" to "Start 'fh6-controller-windows.exe' on your PC. It will display a green QR code and listen on ports 4243 TCP & 4242 UDP.",
                    "2. SAME LOCAL NETWORK (OR USB CABLE)" to "Both phone and PC must be on the same Wi-Fi. (Recommended: Connect USB cable and turn on 'USB Tethering' in Android Settings for zero-latency, 100% stable connection!)",
                    "3. TURN OFF VPN ON PHONE & PC" to "VPNs (Nord, Express, Proton, WARP) route packets away from your local subnet. Disable VPN while playing.",
                    "4. WINDOWS DEFENDER FIREWALL" to "Ensure Windows Firewall allowed the companion on Private & Public networks."
                ).forEach { (title, desc) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x20000000))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Text(desc, color = Color(0xB3FFFFFF), fontSize = 9.sp, lineHeight = 13.sp)
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (report.gatewayIp != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFCCFF00))
                                .clickable {
                                    onConnectToIp(report.gatewayIp)
                                    onDismiss()
                                }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("CONNECT TO DETECTED PC (${report.gatewayIp})", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x30FFFFFF))
                            .clickable {
                                try {
                                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))
                                } catch (e: Exception) { }
                            }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("OPEN PHONE NETWORK SETTINGS", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
