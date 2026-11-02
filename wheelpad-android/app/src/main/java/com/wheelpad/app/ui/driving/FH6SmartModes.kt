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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelpad.app.ui.theme.ChakraPetch

/**
 * Authentic Forza Horizon 6 Dedicated Settings & Pause Menu Navigation Screen
 * Displays full tactical Arrow Keys (Up/Down/Left/Right) and Tab Bumpers [LB]/[RB].
 * 100% Volt Lime (#CCFF00), Dark Slate (#080E14), and Matte Carbon.
 * Zero blue / zero cyan.
 */
@Composable
fun FH6SettingsNavScreen(
    onTriggerAction: (actionId: String, isDown: Boolean) -> Unit,
    onReturnToDrive: () -> Unit
) {
    val view = LocalView.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF5050A0E))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Header: Title + Resume Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Xbox 3-horizontal lines Menu icon [≡]
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFCCFF00)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(14.dp)) {
                            val stroke = 2f
                            drawLine(Color.Black, Offset(1f, 3f), Offset(size.width - 1f, 3f), strokeWidth = stroke)
                            drawLine(Color.Black, Offset(1f, size.height / 2f), Offset(size.width - 1f, size.height / 2f), strokeWidth = stroke)
                            drawLine(Color.Black, Offset(1f, size.height - 3f), Offset(size.width - 1f, size.height - 3f), strokeWidth = stroke)
                        }
                    }

                    Column {
                        Text(
                            text = "FORZA HORIZON // SETTINGS & MENU NAV",
                            color = Color(0xFFCCFF00),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "Tactical Arrow Keys & Category Tabs • Press [B] or Resume to drive",
                            color = Color(0x99FFFFFF),
                            fontSize = 9.sp
                        )
                    }
                }

                // Resume Driving Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFCCFF00))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onReturnToDrive()
                        }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "RESUME DRIVING [≡]",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Main Content Area: Left Arrow Pad + Right Action Face Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Side: 4-Way Arrow Key Pad with Shoulder Bumper Tabs [LB] / [RB]
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Top Category Bumpers
                    Row(
                        modifier = Modifier.fillMaxWidth(0.85f),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        FH6NavBumperTab(
                            label = "◀ PREV TAB [LB]",
                            onPress = { isDown ->
                                if (isDown) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onTriggerAction("btn_lb", isDown)
                            }
                        )
                        FH6NavBumperTab(
                            label = "[RB] NEXT TAB ▶",
                            onPress = { isDown ->
                                if (isDown) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onTriggerAction("camera", isDown)
                            }
                        )
                    }

                    // 4-Way Arrow Pad Diamond
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF071016))
                            .border(2.dp, Color(0x60CCFF00), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            drawLine(Color(0x25CCFF00), Offset(cx, 10f), Offset(cx, size.height - 10f), strokeWidth = 1.5f)
                            drawLine(Color(0x25CCFF00), Offset(10f, cy), Offset(size.width - 10f, cy), strokeWidth = 1.5f)
                        }

                        // UP
                        Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp)) {
                            FH6TactileArrowKey(arrow = "▲", sub = "UP", actionId = "dpad_up", onTrigger = onTriggerAction)
                        }
                        // DOWN
                        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)) {
                            FH6TactileArrowKey(arrow = "▼", sub = "DOWN", actionId = "dpad_down", onTrigger = onTriggerAction)
                        }
                        // LEFT
                        Box(modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp)) {
                            FH6TactileArrowKey(arrow = "◀", sub = "LEFT", actionId = "dpad_left", onTrigger = onTriggerAction)
                        }
                        // RIGHT
                        Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp)) {
                            FH6TactileArrowKey(arrow = "▶", sub = "RIGHT", actionId = "dpad_right", onTrigger = onTriggerAction)
                        }

                        // Center Emblem
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFCCFF00))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onReturnToDrive()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "DRIVE", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                // Right Side: Standard Xbox Diamond [Y, X, B, A] with authentic Forza styling
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ACTIONS & SELECTION",
                        color = Color(0x99FFFFFF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    // Face Button Diamond
                    Box(
                        modifier = Modifier.size(190.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Y Button (Top)
                        Box(modifier = Modifier.align(Alignment.TopCenter)) {
                            FH6NavActionButton(label = "Y", sub = "RESET", color = Color(0xFFCCFF00), actionId = "rewind", onTrigger = onTriggerAction)
                        }
                        // X Button (Left)
                        Box(modifier = Modifier.align(Alignment.CenterStart)) {
                            FH6NavActionButton(label = "X", sub = "ENTER", color = Color(0xFFCCFF00), actionId = "btn_x", onTrigger = onTriggerAction)
                        }
                        // B Button (Right)
                        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                            FH6NavActionButton(label = "B", sub = "BACK", color = Color(0xFFFF1744), actionId = "btn_b", onTrigger = { act, down ->
                                onTriggerAction(act, down)
                                if (down) onReturnToDrive()
                            })
                        }
                        // A Button (Bottom)
                        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                            FH6NavActionButton(label = "A", sub = "SELECT", color = Color(0xFF00E676), actionId = "btn_a", onTrigger = onTriggerAction)
                        }
                    }
                }
            }

            // Footer Tips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "[A] SELECT  •  [B] BACK  •  [X] ENTER", color = Color(0x99FFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(text = "[LB] / [RB] FLIP TABS  •  ARROWS TO NAVIGATE", color = Color(0xFFCCFF00), fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

/**
 * Authentic Forza Horizon 6 Dedicated Photo & Drone Camera Mode Screen
 * Left Side: Camera Movement Joystick (Forward/Back/Strafe)
 * Right Side: 360° Smooth Aim & Orbit Look Circle (RS / Arrow Keys)
 * Flanks: Altitude Up [RT], Altitude Down [LT], Roll [LB] / [RB]
 */
@Composable
fun FH6PhotoModeScreen(
    onLeftStickChange: (x: Float, y: Float) -> Unit,
    onRightStickChange: (x: Float, y: Float) -> Unit,
    onThrottleChange: (Float) -> Unit, // RT Fly Up / Ascend
    onBrakeChange: (Float) -> Unit,    // LT Descend / Fly Down
    onTriggerAction: (actionId: String, isDown: Boolean) -> Unit,
    onExitPhoto: () -> Unit
) {
    val view = LocalView.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF5050A0E))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            .background(Color(0xFFCCFF00)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "📷", fontSize = 13.sp)
                    }

                    Column {
                        Text(
                            text = "FORZA HORIZON // PHOTO & DRONE MODE",
                            color = Color(0xFFCCFF00),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "Dual 360° Camera Joysticks + Altitude Triggers • Press [B] to return to car",
                            color = Color(0x99FFFFFF),
                            fontSize = 9.sp
                        )
                    }
                }

                // Exit Photo Mode Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFCCFF00))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onExitPhoto()
                        }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "EXIT PHOTO MODE [B]",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Main Dual-Stick Camera Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT: Camera Movement Joystick (LS)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "MOVE CAMERA (LS)",
                        color = Color(0xFFCCFF00),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )

                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF071016))
                            .border(2.dp, Color(0x60CCFF00), CircleShape)
                    ) {
                        FH6AnalogJoystick(
                            label = "MOVE CAM",
                            accentColor = Color(0xFFCCFF00),
                            isEditMode = false,
                            onValueChange = onLeftStickChange
                        )
                    }
                }

                // CENTER: Altitude & Camera Actions Flank
                Column(
                    modifier = Modifier
                        .width(150.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Fly Up (RT)
                    FH6CameraAltitudePill(
                        label = "▲ FLY UP [RT]",
                        color = Color(0xFF00E676),
                        onActive = { onThrottleChange(if (it) 1.0f else 0.0f) }
                    )

                    // Quick Snap Photo [A]
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF00E676))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onTriggerAction("btn_a", true)
                                onTriggerAction("btn_a", false)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📸 SNAP PHOTO [A]",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    // Autofocus / Hide UI [X]
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x30CCFF00))
                            .border(1.dp, Color(0xFFCCFF00), RoundedCornerShape(8.dp))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onTriggerAction("btn_x", true)
                                onTriggerAction("btn_x", false)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "FOCUS / HIDE UI [X]",
                            color = Color(0xFFCCFF00),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Descend (LT)
                    FH6CameraAltitudePill(
                        label = "▼ DESCEND [LT]",
                        color = Color(0xFFFF1744),
                        onActive = { onBrakeChange(if (it) 1.0f else 0.0f) }
                    )
                }

                // RIGHT: 360° Smooth Look & Aim Orbit Circle (RS / Arrow Keys)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "360° LOOK & AIM (RS)",
                        color = Color(0xFFCCFF00),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )

                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF071016))
                            .border(2.dp, Color(0x60CCFF00), CircleShape)
                    ) {
                        FH6AnalogJoystick(
                            label = "LOOK 360°",
                            accentColor = Color(0xFFCCFF00),
                            isEditMode = false,
                            onValueChange = onRightStickChange
                        )
                    }
                }
            }

            // Footer Navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "[LB] ROLL LEFT  •  [RB] ROLL RIGHT", color = Color(0x99FFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(text = "DRAG JOYSTICKS TO POSITION & AIM CAMERA FREELY", color = Color(0xFFCCFF00), fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun FH6NavBumperTab(
    label: String,
    onPress: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isPressed) Color(0xFFCCFF00) else Color(0x25CCFF00))
            .border(1.2.dp, Color(0xFFCCFF00), RoundedCornerShape(6.dp))
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
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (isPressed) Color.Black else Color(0xFFCCFF00),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun FH6TactileArrowKey(
    arrow: String,
    sub: String,
    actionId: String,
    onTrigger: (actionId: String, isDown: Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val view = LocalView.current

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPressed) Color(0xFFCCFF00) else Color(0xF50E1820))
            .border(1.2.dp, if (isPressed) Color.White else Color(0x60CCFF00), RoundedCornerShape(8.dp))
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
                text = arrow,
                color = if (isPressed) Color.Black else Color(0xFFCCFF00),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = sub,
                color = if (isPressed) Color(0xCC000000) else Color(0xAAFFFFFF),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FH6NavActionButton(
    label: String,
    sub: String,
    color: Color,
    actionId: String,
    onTrigger: (actionId: String, isDown: Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val view = LocalView.current

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (isPressed) color else Color(0xF50E1820))
            .border(1.5.dp, color, CircleShape)
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
                color = if (isPressed) Color.Black else color,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FH6CameraAltitudePill(
    label: String,
    color: Color,
    onActive: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val view = LocalView.current

    Box(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPressed) color else Color(0x25FFFFFF))
            .border(1.2.dp, color, RoundedCornerShape(8.dp))
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
        Text(
            text = label,
            color = if (isPressed) Color.Black else Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black
        )
    }
}
