package com.wheelpad.app.ui.driving

import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelpad.app.data.model.GameAction
import com.wheelpad.app.data.repository.ProfileRepository
import com.wheelpad.app.ui.theme.ChakraPetch
import com.wheelpad.app.updater.AutoUpdateManager
import com.wheelpad.app.updater.UpdateState

/**
 * Authentic Forza Horizon Action Catalog Dialog Modal
 */
@Composable
fun FH6ActionCatalogDialog(
    onDismiss: () -> Unit,
    onSelectAction: (GameAction) -> Unit
) {
    val allActions = ProfileRepository.ALL_FORZA_ACTIONS

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD0030E0C))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .widthIn(min = 460.dp, max = 560.dp)
                .heightIn(max = 330.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xF8051E1C))
                .border(1.5.dp, Color(0xFFCCFF00), RoundedCornerShape(6.dp))
                .clickable(enabled = false) {}
        ) {
            // Full-Bleed Volt Lime Header
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
                        text = "SELECT AN ACTION",
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
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }
            }

            // Content Container with Motion Blur Edge Fades
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allActions.forEach { action ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF071816))
                                .border(1.dp, Color(0x35CCFF00), RoundedCornerShape(6.dp))
                                .clickable { onSelectAction(action) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF030D0C))
                                        .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(6.dp))
                                        .padding(2.dp)
                                ) {
                                    FH6MiniActionPreview(actionId = action.id)
                                }

                                Column {
                                    Text(
                                        text = action.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = action.description,
                                        color = Color(0x99FFFFFF),
                                        fontSize = 9.sp,
                                        maxLines = 1
                                    )
                                }
                            }

                            val tagText = when (action.id) {
                                "rewind", "btn_y" -> "Y"
                                "btn_a", "handbrake" -> "A"
                                "btn_b", "shift_up" -> "B"
                                "btn_x", "activate", "shift_down" -> "X"
                                "camera", "btn_rb" -> "RB"
                                "btn_lb", "clutch" -> "LB"
                                "horn", "btn_rs" -> "RS"
                                "look_back" -> "REAR"
                                "convertible", "btn_ls" -> "LS"
                                "photo_mode", "dpad_up" -> "PHOTO"
                                "radio_next", "dpad_right" -> "RADIO"
                                "dpad_down", "anna" -> "ANNA"
                                "dpad_left", "forza_link" -> "LINK"
                                "map", "btn_back", "btn_view" -> "VIEW"
                                "pause", "btn_start", "btn_menu" -> "MENU"
                                "auto_drive" -> "AUTO"
                                else -> "+ ADD"
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x25CCFF00))
                                    .border(1.dp, Color(0xFFCCFF00), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = tagText,
                                    color = Color(0xFFCCFF00),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // Full-Bleed Cancel Footer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF03100E))
                    .clickable { onDismiss() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CANCEL",
                    color = Color(0xCCFFFFFF),
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

/**
 * Dedicated Hardware Volume Key Action Picker Dialog Modal
 */
@Composable
fun FH6VolumeKeyPickerDialog(
    targetKey: String,
    onDismiss: () -> Unit,
    onAssign: (String) -> Unit
) {
    val allActions = ProfileRepository.ALL_FORZA_ACTIONS

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD0030E0C))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .widthIn(min = 460.dp, max = 560.dp)
                .heightIn(max = 330.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xF8051E1C))
                .border(1.5.dp, Color(0xFFCCFF00), RoundedCornerShape(6.dp))
                .clickable(enabled = false) {}
        ) {
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
                        text = "SET VOLUME ${if (targetKey == "UP") "UP (+)" else "DOWN (−)"} KEY",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "CLOSE",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allActions.forEach { action ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF071816))
                                .border(1.dp, Color(0x35CCFF00), RoundedCornerShape(6.dp))
                                .clickable { onAssign(action.id) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF030D0C))
                                        .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(6.dp))
                                        .padding(2.dp)
                                ) {
                                    FH6MiniActionPreview(actionId = action.id)
                                }

                                Column {
                                    Text(
                                        text = action.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = action.description,
                                        color = Color(0x99FFFFFF),
                                        fontSize = 9.sp,
                                        maxLines = 1
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x25CCFF00))
                                    .border(1.dp, Color(0xFFCCFF00), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "ASSIGN",
                                    color = Color(0xFFCCFF00),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF03100E))
                    .clickable { onDismiss() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CANCEL",
                    color = Color(0xCCFFFFFF),
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

/**
 * Sleek HUD Quick Settings Drawer (Forza Horizon Styling, Fully Scrollable)
 */
@Composable
fun FH6QuickSettingsDrawer(
    onDismiss: () -> Unit,
    onOpenConnectDialog: (() -> Unit)?,
    onOpenDiagnosticsDialog: (() -> Unit)?,
    isGyroEnabled: Boolean,
    onToggleGyro: () -> Unit,
    gyroAngle: Float,
    gyroSensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    hwVolumeEnabled: Boolean,
    onToggleHwVolume: () -> Unit,
    hwVolumeUpAction: String,
    hwVolumeDownAction: String,
    onPickVolumeKey: (String) -> Unit,
    onSetVolumePreset: (String, String) -> Unit,
    onEditControls: () -> Unit,
    onResetLayout: () -> Unit,
    updateManager: AutoUpdateManager
) {
    val view = LocalView.current
    val allForza = ProfileRepository.ALL_FORZA_ACTIONS
    val volUpActionObj = allForza.find { it.id == hwVolumeUpAction }
    val volDownActionObj = allForza.find { it.id == hwVolumeDownAction }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x80000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier
                .padding(top = 54.dp, end = 16.dp, bottom = 16.dp)
                .width(300.dp)
                .heightIn(max = 440.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xF8062220))
                .border(1.5.dp, Color(0xFFCCFF00), RoundedCornerShape(14.dp))
                .verticalScroll(rememberScrollState())
                .clickable(enabled = false) {},
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Header Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFCCFF00))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HORIZON SETTINGS",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "CLOSE",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }
            }

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Option 1: Quick Connect / Pairing Dialog
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White)
                        .clickable {
                            onDismiss()
                            onOpenConnectDialog?.invoke()
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("PC CONNECTION", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        Text("Pair with Windows Laptop", color = Color(0xFF555555), fontSize = 9.sp)
                    }
                    Text("PAIR >", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }

                // Option 1b: App Requirements & Network Health Diagnostics
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White)
                        .clickable {
                            onDismiss()
                            onOpenDiagnosticsDialog?.invoke()
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("REQUIREMENTS & HEALTH", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        Text("Check Wi-Fi, VPN & Windows Firewall", color = Color(0xFF555555), fontSize = 9.sp)
                    }
                    Text("HEALTH >", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }

                // Option 1c: Steering Tuning Preset
                var steeringPresetIndex by remember { mutableStateOf(1) }
                val steeringPresets = listOf("DRIFT (540° Counter-Steer)", "CIRCUIT (360° Linear)", "ARCADE (270° Rapid)")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0F1722))
                        .border(1.5.dp, Color(0xFFCCFF00), RoundedCornerShape(6.dp))
                        .clickable {
                            steeringPresetIndex = (steeringPresetIndex + 1) % steeringPresets.size
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("STEERING TUNING", color = Color(0xFFCCFF00), fontSize = 11.sp, fontWeight = FontWeight.Black)
                        Text(
                            steeringPresets[steeringPresetIndex],
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFCCFF00))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("CHANGE >", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }

                // Option 2: Gyroscope Tilt Steering Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isGyroEnabled) Color(0xFF0A0A0A) else Color.White)
                        .border(if (isGyroEnabled) 2.dp else 0.dp, if (isGyroEnabled) Color(0xFFCCFF00) else Color.Transparent, RoundedCornerShape(6.dp))
                        .clickable { onToggleGyro() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "GYRO STEERING",
                            color = if (isGyroEnabled) Color(0xFFCCFF00) else Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            if (isGyroEnabled) "Live Tilt: ${(gyroAngle * 90).toInt()} deg" else "Tilt phone to steer",
                            color = if (isGyroEnabled) Color(0xCCFFFFFF) else Color(0xFF555555),
                            fontSize = 9.sp
                        )
                    }
                    Text(
                        text = if (isGyroEnabled) "ACTIVE" else "OFF",
                        color = if (isGyroEnabled) Color(0xFFCCFF00) else Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Option 2b: Gyro Sensitivity Stepper & Presets
                if (isGyroEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0A0A0A))
                            .border(1.dp, Color(0x40CCFF00), RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("GYRO SENSITIVITY", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${(gyroSensitivity * 100).toInt()}%",
                                color = Color(0xFFCCFF00),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x30FFFFFF))
                                    .clickable {
                                        onSensitivityChange((gyroSensitivity - 0.1f).coerceAtLeast(0.4f))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("-", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(0.75f to "0.75x", 1.0f to "1.0x", 1.5f to "1.5x", 2.0f to "2.0x").forEach { (preset, label) ->
                                    val isSel = kotlin.math.abs(gyroSensitivity - preset) < 0.06f
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isSel) Color(0xFFCCFF00) else Color(0x20FFFFFF))
                                            .clickable { onSensitivityChange(preset) }
                                            .padding(horizontal = 7.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            label,
                                            color = if (isSel) Color.Black else Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x30FFFFFF))
                                    .clickable {
                                        onSensitivityChange((gyroSensitivity + 0.1f).coerceAtMost(3.0f))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Option 2c: Hardware Volume Buttons Remapping
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0F1722))
                        .border(1.5.dp, if (hwVolumeEnabled) Color(0xFFCCFF00) else Color(0x40FFFFFF), RoundedCornerShape(6.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("HARDWARE VOLUME BUTTONS", color = if (hwVolumeEnabled) Color(0xFFCCFF00) else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            Text("Remap physical phone keys to Forza", color = Color(0xB3FFFFFF), fontSize = 9.sp)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (hwVolumeEnabled) Color(0xFFCCFF00) else Color(0x30FFFFFF))
                                .clickable {
                                    onToggleHwVolume()
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (hwVolumeEnabled) "ACTIVE" else "OFF",
                                color = if (hwVolumeEnabled) Color.Black else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    if (hwVolumeEnabled) {
                        // Volume Up Key Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF162334))
                                .clickable { onPickVolumeKey("UP") }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF080D14)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    FH6MiniActionPreview(actionId = hwVolumeUpAction)
                                }
                                Column {
                                    Text("VOL UP (+)", color = Color(0xFFCCFF00), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(volUpActionObj?.name ?: hwVolumeUpAction, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            Text("CHANGE >", color = Color(0xFFCCFF00), fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }

                        // Volume Down Key Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF162334))
                                .clickable { onPickVolumeKey("DOWN") }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF080D14)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    FH6MiniActionPreview(actionId = hwVolumeDownAction)
                                }
                                Column {
                                    Text("VOL DOWN (−)", color = Color(0xFFCCFF00), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(volDownActionObj?.name ?: hwVolumeDownAction, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            Text("CHANGE >", color = Color(0xFFCCFF00), fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }

                        // Quick Presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x30CCFF00))
                                    .border(1.dp, Color(0x60CCFF00), RoundedCornerShape(4.dp))
                                    .clickable {
                                        onSetVolumePreset("shift_up", "shift_down")
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("PADDLES (+/−)", color = Color(0xFFCCFF00), fontSize = 8.sp, fontWeight = FontWeight.Black)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x30FFFFFF))
                                    .border(1.dp, Color(0x60FFFFFF), RoundedCornerShape(4.dp))
                                    .clickable {
                                        onSetVolumePreset("camera", "btn_lb")
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("CAR/MENU (RB/LB)", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x30CCFF00))
                                    .border(1.dp, Color(0x60CCFF00), RoundedCornerShape(4.dp))
                                    .clickable {
                                        onSetVolumePreset("radio_next", "radio_prev")
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("D-PAD (R/L)", color = Color(0xFFCCFF00), fontSize = 8.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                // Option 3: Edit Controls Layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White)
                        .clickable {
                            onDismiss()
                            onEditControls()
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("EDIT CONTROLS", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Text("REPOSITION >", color = Color(0xFF555555), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                // Option 4: Reset to Default FH6 Layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x20FF0055))
                        .border(1.2.dp, Color(0xFFFF0055), RoundedCornerShape(6.dp))
                        .clickable {
                            onDismiss()
                            onResetLayout()
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("RESET DEFAULT LAYOUT", color = Color(0xFFFF0055), fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Text("RESTORE", color = Color(0xFFFF0055), fontSize = 10.sp, fontWeight = FontWeight.Black)
                }

                // Option 5: Check for Updates (OTA Auto-Updater)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF09101A))
                        .border(1.2.dp, Color(0xFF1C2C40), RoundedCornerShape(6.dp))
                        .clickable {
                            onDismiss()
                            updateManager.checkForUpdates(silent = false)
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(26.dp)
                                .background(Color(0xFFCCFF00))
                        )
                        Column {
                            Text("CHECK FOR UPDATES", color = Color(0xFFCCFF00), fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = ChakraPetch)
                            Text("v${updateManager.getCurrentVersionName()} • Tap to sync telemetry & builds", color = Color(0xB3FFFFFF), fontSize = 9.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x25CCFF00))
                            .border(1.dp, Color(0xFFCCFF00), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("SYNC", color = Color(0xFFCCFF00), fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

/**
 * Authentic FH6 Motorsport OTA Notification Modal Overlay
 */
@Composable
fun FH6UpdateNotificationOverlay(
    updateState: UpdateState,
    updateManager: AutoUpdateManager
) {
    when (val uState = updateState) {
        is UpdateState.Checking -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 18.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xF5060C14))
                        .border(1.5.dp, Color(0xFFCCFF00), RoundedCornerShape(3.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(18.dp)
                                .background(Color(0xFFCCFF00))
                        )
                        Text(
                            "FORZA OTA // CONNECTING TELEMETRY REPO...",
                            color = Color(0xFFCCFF00),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = ChakraPetch
                        )
                    }
                }
            }
        }
        is UpdateState.UpToDate -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 18.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xF8060C14))
                        .border(1.5.dp, Color(0xFFCCFF00), RoundedCornerShape(3.dp))
                        .padding(horizontal = 22.dp, vertical = 11.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(26.dp)
                                .background(Color(0xFFCCFF00))
                        )
                        Column {
                            Text(
                                "HORIZON FESTIVAL // UP TO DATE",
                                color = Color(0xFFCCFF00),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                fontFamily = ChakraPetch
                            )
                            Text(
                                "Build v${uState.versionName} • 120Hz Monotonic UDP Engine Active",
                                color = Color(0xD9FFFFFF),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
        is UpdateState.Available -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(min = 380.dp, max = 520.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xF8060C14))
                        .border(2.dp, Color(0xFFCCFF00), RoundedCornerShape(3.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(5.dp)
                                        .height(24.dp)
                                        .background(Color(0xFFCCFF00))
                                )
                                Text(
                                    "HORIZON UPDATE AVAILABLE",
                                    color = Color(0xFFCCFF00),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.5.sp,
                                    fontFamily = ChakraPetch
                                )
                            }
                            Text(
                                "DISMISS",
                                color = Color(0x99FFFFFF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { updateManager.dismiss() }
                            )
                        }

                        Text(
                            "New Festival Build: v${uState.info.versionName} is ready to install.",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (uState.info.changelog.isNotBlank()) {
                            Text(
                                uState.info.changelog,
                                color = Color(0xCCFFFFFF),
                                fontSize = 9.5.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFFCCFF00))
                                    .clickable { updateManager.downloadAndInstall(uState.info) }
                                    .padding(horizontal = 16.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    "INSTALL UPDATE NOW",
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = ChakraPetch
                                )
                            }
                        }
                    }
                }
            }
        }
        is UpdateState.Downloading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 18.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xF8060C14))
                        .border(1.5.dp, Color(0xFFCCFF00), RoundedCornerShape(3.dp))
                        .padding(horizontal = 22.dp, vertical = 11.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "DOWNLOADING HORIZON UPDATE (${uState.progressPercent}%)...",
                            color = Color(0xFFCCFF00),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = ChakraPetch
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.3f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0x30FFFFFF))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(uState.progressPercent / 100f)
                                    .fillMaxHeight()
                                    .background(Color(0xFFCCFF00))
                            )
                        }
                    }
                }
            }
        }
        is UpdateState.ReadyToInstall -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 18.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFCCFF00))
                        .clickable { updateManager.launchInstaller(uState.apkFile) }
                        .padding(horizontal = 22.dp, vertical = 11.dp)
                ) {
                    Text(
                        "TAP HERE TO INSTALL COMPLETED UPDATE",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = ChakraPetch
                    )
                }
            }
        }
        is UpdateState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 18.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xF81A060A))
                        .border(1.5.dp, Color(0xFFFF1744), RoundedCornerShape(3.dp))
                        .clickable { updateManager.dismiss() }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        "OTA UPDATE ERROR: ${uState.message}",
                        color = Color(0xFFFF1744),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ChakraPetch
                    )
                }
            }
        }
        else -> {}
    }
}
