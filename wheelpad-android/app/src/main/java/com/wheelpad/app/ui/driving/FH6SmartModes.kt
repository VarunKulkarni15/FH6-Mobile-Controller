package com.wheelpad.app.ui.driving

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelpad.app.ui.theme.ChakraPetch

/**
 * High-performance Controller Modes for Forza Horizon
 */
enum class FHControllerMode {
    DRIVE,       // Full analog steering wheel + pedals + manual racing
    MENU_NAV,    // 4-way D-Pad + Category/Car Flippers [LB/RB] for Pause / Car Selection / Settings
    PHOTO_MODE   // Dual 360° camera thumbsticks + Altitude Ascend/Descend (RT/LT) + Shutter
}

/**
 * Sleek Top HUD Mode Switcher Pill (Forza Horizon Styling)
 */
@Composable
fun FH6ModeSwitcherPill(
    currentMode: FHControllerMode,
    onSelectMode: (FHControllerMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xD9061016))
            .border(1.2.dp, Color(0x60CCFF00), RoundedCornerShape(20.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FH6ModePillItem(
            label = "DRIVE",
            icon = "🏎️",
            isActive = currentMode == FHControllerMode.DRIVE,
            activeColor = Color(0xFFCCFF00),
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onSelectMode(FHControllerMode.DRIVE)
            }
        )

        FH6ModePillItem(
            label = "MENU / GARAGE",
            icon = "📋",
            isActive = currentMode == FHControllerMode.MENU_NAV,
            activeColor = Color(0xFF00E5FF),
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onSelectMode(FHControllerMode.MENU_NAV)
            }
        )

        FH6ModePillItem(
            label = "PHOTO / DRONE",
            icon = "📷",
            isActive = currentMode == FHControllerMode.PHOTO_MODE,
            activeColor = Color(0xFFE040FB),
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onSelectMode(FHControllerMode.PHOTO_MODE)
            }
        )
    }
}

@Composable
private fun FH6ModePillItem(
    label: String,
    icon: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val bg = if (isActive) activeColor else Color.Transparent
    val textColor = if (isActive) Color.Black else Color(0x99FFFFFF)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = icon, fontSize = 10.sp)
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Authentic Forza Horizon Menu & Car Garage Directional Pad
 * Replaces the steering wheel area when navigating Pause / Car Selection / Settings
 */
@Composable
fun FH6MenuNavPad(
    isEditMode: Boolean,
    onTriggerAction: (actionId: String, isDown: Boolean) -> Unit,
    onReturnToDrive: () -> Unit
) {
    val view = LocalView.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xF0081C24), Color(0xF8030B10))
                )
            )
            .border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(20.dp))
            .padding(10.dp)
    ) {
        val w = maxWidth
        val h = maxHeight
        val minDim = minOf(w, h)

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Category / Car Selection Tab Flippers [LB] and [RB]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // [LB] Tab Left
                FH6NavShoulderTab(
                    label = "◀ PREV TAB [LB]",
                    color = Color(0xFF00E5FF),
                    onPress = { isDown ->
                        if (isDown) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onTriggerAction("btn_lb", isDown)
                    }
                )

                // Subtitle
                Text(
                    text = "MENU & GARAGE NAV",
                    color = Color(0xCC00E5FF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                // [RB] Tab Right
                FH6NavShoulderTab(
                    label = "[RB] NEXT TAB ▶",
                    color = Color(0xFF00E5FF),
                    onPress = { isDown ->
                        if (isDown) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onTriggerAction("camera", isDown)
                    }
                )
            }

            // Center: 4-Way Forza D-Pad Diamond
            Box(
                modifier = Modifier
                    .size((minDim.value * 0.72f).dp)
                    .clip(CircleShape)
                    .background(Color(0xFF051218))
                    .border(1.5.dp, Color(0x4000E5FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Cross guides
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    drawLine(Color(0x2000E5FF), Offset(cx, 10f), Offset(cx, size.height - 10f), strokeWidth = 1.5f)
                    drawLine(Color(0x2000E5FF), Offset(10f, cy), Offset(size.width - 10f, cy), strokeWidth = 1.5f)
                }

                // D-Pad Up
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                ) {
                    FH6DPadDirectionButton(
                        label = "▲",
                        sub = "UP",
                        actionId = "dpad_up",
                        onTrigger = onTriggerAction
                    )
                }

                // D-Pad Down
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                ) {
                    FH6DPadDirectionButton(
                        label = "▼",
                        sub = "DOWN",
                        actionId = "dpad_down",
                        onTrigger = onTriggerAction
                    )
                }

                // D-Pad Left
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                ) {
                    FH6DPadDirectionButton(
                        label = "◀",
                        sub = "LEFT",
                        actionId = "dpad_left",
                        onTrigger = onTriggerAction
                    )
                }

                // D-Pad Right
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                ) {
                    FH6DPadDirectionButton(
                        label = "▶",
                        sub = "RIGHT",
                        actionId = "dpad_right",
                        onTrigger = onTriggerAction
                    )
                }

                // Center Return to Drive Button
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFCCFF00))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onReturnToDrive()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "DRIVE",
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "EXIT",
                            color = Color(0x99000000),
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Footer Quick Action Shortcuts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Enter / Select Shortcut
                Text(
                    text = "SELECT: [A] / ENTER",
                    color = Color(0xFF00E676),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )

                // Return to Wheel Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x30CCFF00))
                        .border(1.dp, Color(0xFFCCFF00), RoundedCornerShape(6.dp))
                        .clickable { onReturnToDrive() }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "RESUME DRIVING ➔",
                        color = Color(0xFFCCFF00),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }

                // Back Shortcut
                Text(
                    text = "BACK: [B] / ESC",
                    color = Color(0xFFFF1744),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FH6NavShoulderTab(
    label: String,
    color: Color,
    onPress: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isPressed) color else Color(0x3000E5FF))
            .border(1.dp, color, RoundedCornerShape(6.dp))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val down = event.changes.any { it.pressed }
                        if (down != isPressed) {
                            isPressed = down
                            onPress(down)
                        }
                    }
                }
            }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = if (isPressed) Color.Black else color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun FH6DPadDirectionButton(
    label: String,
    sub: String,
    actionId: String,
    onTrigger: (actionId: String, isDown: Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val view = LocalView.current

    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isPressed) Color(0xFF00E5FF) else Color(0xF00A202A))
            .border(1.2.dp, if (isPressed) Color.White else Color(0x8000E5FF), RoundedCornerShape(10.dp))
            .pointerInput(actionId) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val down = event.changes.any { it.pressed }
                        if (down != isPressed) {
                            isPressed = down
                            if (down) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onTrigger(actionId, down)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = if (isPressed) Color.Black else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = sub,
                color = if (isPressed) Color.Black else Color(0xAA00E5FF),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Authentic Forza Horizon Photo & Drone Mode Cockpit
 * Provides Dual 360° Analog Sticks (Pan + Aim) and Vertical Altitude Controls
 */
@Composable
fun FH6PhotoModeCockpit(
    isEditMode: Boolean,
    onLeftStickChange: (x: Float, y: Float) -> Unit,
    onRightStickChange: (x: Float, y: Float) -> Unit,
    onThrottleChange: (Float) -> Unit, // RT Fly Up / Ascend
    onBrakeChange: (Float) -> Unit,    // LT Fly Down / Descend
    onTriggerAction: (actionId: String, isDown: Boolean) -> Unit,
    onReturnToDrive: () -> Unit
) {
    val view = LocalView.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xF01C0824), Color(0xF80B0312))
                )
            )
            .border(2.dp, Color(0xFFE040FB), RoundedCornerShape(20.dp))
            .padding(8.dp)
    ) {
        val w = maxWidth
        val h = maxHeight
        val minDim = minOf(w, h)

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Bar: Camera Roll [LB] / [RB] and Mode Label
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Roll Left
                FH6NavShoulderTab(
                    label = "◀ ROLL L [LB]",
                    color = Color(0xFFE040FB),
                    onPress = { isDown ->
                        if (isDown) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onTriggerAction("btn_lb", isDown)
                    }
                )

                Text(
                    text = "PHOTO & DRONE COCKPIT",
                    color = Color(0xFFE040FB),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                // Roll Right
                FH6NavShoulderTab(
                    label = "ROLL R [RB] ▶",
                    color = Color(0xFFE040FB),
                    onPress = { isDown ->
                        if (isDown) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onTriggerAction("camera", isDown)
                    }
                )
            }

            // Dual Joysticks & Altitude Center Slider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Stick: Camera Move / Pan
                Box(
                    modifier = Modifier
                        .size((minDim.value * 0.70f).dp)
                        .clip(CircleShape)
                        .background(Color(0x80100418))
                        .border(1.dp, Color(0x60E040FB), CircleShape)
                ) {
                    FH6AnalogJoystick(
                        label = "MOVE CAM (LS)",
                        accentColor = Color(0xFF00E5FF),
                        isEditMode = isEditMode,
                        onValueChange = onLeftStickChange
                    )
                }

                // Center: Altitude Vertical Slider (RT Fly Up / LT Fly Down)
                Column(
                    modifier = Modifier
                        .width(72.dp)
                        .fillMaxHeight(0.9f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Fly Up (RT)
                    FH6AltitudeButton(
                        label = "▲ FLY UP",
                        sub = "RT",
                        color = Color(0xFF00E676),
                        onActive = { active ->
                            onThrottleChange(if (active) 1.0f else 0.0f)
                        }
                    )

                    // Reset Cam (Y)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0x35CCFF00))
                            .border(1.dp, Color(0xFFCCFF00), CircleShape)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onTriggerAction("rewind", true)
                                onTriggerAction("rewind", false)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Y", color = Color(0xFFCCFF00), fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }

                    // Fly Down (LT)
                    FH6AltitudeButton(
                        label = "▼ DESCEND",
                        sub = "LT",
                        color = Color(0xFFFF1744),
                        onActive = { active ->
                            onBrakeChange(if (active) 1.0f else 0.0f)
                        }
                    )
                }

                // Right Stick: Camera Look / 360° Angle Aim
                Box(
                    modifier = Modifier
                        .size((minDim.value * 0.70f).dp)
                        .clip(CircleShape)
                        .background(Color(0x80100418))
                        .border(1.dp, Color(0x60E040FB), CircleShape)
                ) {
                    FH6AnalogJoystick(
                        label = "LOOK 360° (RS)",
                        accentColor = Color(0xFFE040FB),
                        isEditMode = isEditMode,
                        onValueChange = onRightStickChange
                    )
                }
            }

            // Footer Shutter & Exit Shortcuts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FOCUS / HIDE UI: [X]",
                    color = Color(0xFF00B0FF),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )

                // Return to Wheel Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x30CCFF00))
                        .border(1.dp, Color(0xFFCCFF00), RoundedCornerShape(6.dp))
                        .clickable { onReturnToDrive() }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "RESUME DRIVING ➔",
                        color = Color(0xFFCCFF00),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }

                Text(
                    text = "SNAP PHOTO: [A]",
                    color = Color(0xFF00E676),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FH6AltitudeButton(
    label: String,
    sub: String,
    color: Color,
    onActive: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val view = LocalView.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isPressed) color else Color(0x30FFFFFF))
            .border(1.dp, color, RoundedCornerShape(6.dp))
            .pointerInput(label) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val down = event.changes.any { it.pressed }
                        if (down != isPressed) {
                            isPressed = down
                            if (down) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onActive(down)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = if (isPressed) Color.Black else Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = sub,
                color = if (isPressed) Color.Black else color,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Auto-Drive & Cinematic Camera Overlay Banner
 * Illuminates with pulsing Volt Lime aura when Auto-Drive is active.
 * Holding triggers [LB] (Cinematic Camera angle).
 */
@Composable
fun FH6AutoDriveCinematicOverlay(
    isAutoDriveActive: Boolean,
    onCinematicCamPress: (Boolean) -> Unit,
    onDisengage: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isAutoDriveActive) return

    var isPressed by remember { mutableStateOf(false) }
    val view = LocalView.current

    Box(
        modifier = modifier
            .fillMaxWidth(0.6f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xF005161A))
            .border(2.dp, Color(0xFFCCFF00), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // AutoDrive Status
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676))
                    )
                    Text(
                        text = "AUTO-DRIVE AI ENGAGED",
                        color = Color(0xFFCCFF00),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "AI cruising waypoint • Touch steering or pedals to take over",
                    color = Color(0x99FFFFFF),
                    fontSize = 8.sp
                )
            }

            // Cinematic Camera Hold Button [LB]
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isPressed) Color(0xFFCCFF00) else Color(0x35CCFF00))
                    .border(1.2.dp, Color(0xFFCCFF00), RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val down = event.changes.any { it.pressed }
                                if (down != isPressed) {
                                    isPressed = down
                                    if (down) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onCinematicCamPress(down)
                                }
                            }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "🎥 HOLD [LB]",
                        color = if (isPressed) Color.Black else Color(0xFFCCFF00),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "CINEMATIC CAM",
                        color = if (isPressed) Color.Black else Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
