package com.wheelpad.app.ui.driving

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelpad.app.ui.theme.ChakraPetch
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Authentic Forza Horizon Dedicated Settings, Pause & World Map Navigation Screen.
 * Mode-aware: Dynamically switches layout labels, headers, and actions between
 * PAUSE MENU (Campaign/Cars/Settings tabs, Convoy, Reset Car, Select, Back)
 * and WORLD MAP (Regions, Filters, Fast Travel, Reveal Map, Set Route, Close Map).
 */
@Composable
fun FH6SettingsNavScreen(
    currentMode: String = "PAUSE", // "PAUSE" or "MAP"
    onModeChange: (String) -> Unit = {},
    onTriggerAction: (actionId: String, isDown: Boolean) -> Unit,
    onAnalogMove: (x: Float, y: Float) -> Unit,
    onReturnToDrive: () -> Unit
) {
    val view = LocalView.current
    val isMap = currentMode.equals("MAP", ignoreCase = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF5050A0E))
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Header: Title + Mode Switcher Tabs + Resume Driving Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Horizon Logo & Mode Title
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
                        Canvas(modifier = Modifier.size(12.dp)) {
                            val stroke = 2f
                            drawLine(Color.Black, Offset(1f, 2.5f), Offset(size.width - 1f, 2.5f), strokeWidth = stroke)
                            drawLine(Color.Black, Offset(1f, size.height / 2f), Offset(size.width - 1f, size.height / 2f), strokeWidth = stroke)
                            drawLine(Color.Black, Offset(1f, size.height - 2.5f), Offset(size.width - 1f, size.height - 2.5f), strokeWidth = stroke)
                        }
                    }

                    Text(
                        text = if (isMap) "FORZA HORIZON // WORLD MAP" else "FORZA HORIZON // PAUSE MENU",
                        color = Color(0xFFCCFF00),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        fontFamily = ChakraPetch
                    )
                }

                // Middle: Mode Switcher Segmented Tabs
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0C141C))
                        .border(1.dp, Color(0x50CCFF00), RoundedCornerShape(6.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Pause Menu Tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (!isMap) Color(0xFFCCFF00) else Color.Transparent)
                            .clickable {
                                if (isMap) {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onModeChange("PAUSE")
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "PAUSE MENU",
                            color = if (!isMap) Color.Black else Color(0x99FFFFFF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = ChakraPetch
                        )
                    }

                    // World Map Tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isMap) Color(0xFFCCFF00) else Color.Transparent)
                            .clickable {
                                if (!isMap) {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onModeChange("MAP")
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "WORLD MAP",
                            color = if (isMap) Color.Black else Color(0x99FFFFFF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = ChakraPetch
                        )
                    }
                }

                // Right: Resume Driving Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFCCFF00))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onReturnToDrive()
                        }
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = if (isMap) "CLOSE MAP [âœ•]" else "RESUME DRIVING [â‰¡]",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp,
                        fontFamily = ChakraPetch
                    )
                }
            }

            // Main Content Area: Left Arrow / Map Cursor Pad + Right Action Face Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Side: 4-Way Arrow Key Pad + Map Reticle Cursor Glide
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Top Category Bumpers (Redesigned Motorsport Wing Styling)
                    Row(
                        modifier = Modifier.fillMaxWidth(0.88f),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        FH6MotorsportBumper(
                            bumperKey = "LB",
                            title = if (isMap) "REGIONS" else "PREV TAB",
                            isLeft = true,
                            onPress = { isDown -> onTriggerAction("btn_lb", isDown) }
                        )
                        FH6MotorsportBumper(
                            bumperKey = "RB",
                            title = if (isMap) "FILTER" else "NEXT TAB",
                            isLeft = false,
                            onPress = { isDown -> onTriggerAction("btn_rb", isDown) }
                        )
                    }

                    // Combined Tactile 4-Way Arrow Pad + 360Â° Map Cursor Glide
                    FH6MapCursorPad(
                        isMap = isMap,
                        onTriggerAction = onTriggerAction,
                        onAnalogMove = onAnalogMove,
                        onCenterClick = {
                            onTriggerAction("btn_a", true)
                            onTriggerAction("btn_a", false)
                        }
                    )
                }

                // Right Side: Standard Xbox Diamond [Y, X, B, A] with authentic Xbox Colors & Context Labels
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isMap) "MAP CONTROLS" else "MENU ACTIONS",
                        color = Color(0x99FFFFFF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontFamily = ChakraPetch
                    )

                    // Face Button Diamond
                    Box(
                        modifier = Modifier.size(190.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Y Button (Top, Yellow/Volt Lime)
                        // In Map: Reveal Treasure Map | In Pause Menu: Convoy
                        Box(modifier = Modifier.align(Alignment.TopCenter)) {
                            FH6NavActionButton(
                                label = "Y",
                                sub = if (isMap) "REVEAL MAP" else "CONVOY",
                                color = Color(0xFFCCFF00),
                                actionId = "rewind",
                                onTrigger = onTriggerAction
                            )
                        }
                        // X Button (Left, Authentic Xbox Blue!)
                        // In Map: Fast Travel | In Pause Menu: Reset Car Position
                        Box(modifier = Modifier.align(Alignment.CenterStart)) {
                            FH6NavActionButton(
                                label = "X",
                                sub = if (isMap) "FAST TRAVEL" else "RESET CAR",
                                color = Color(0xFF00B0FF),
                                actionId = "btn_x",
                                onTrigger = onTriggerAction
                            )
                        }
                        // B Button (Right, Red)
                        // In Map: Close Map | In Pause Menu: Back -> Returns directly to driving rig!
                        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                            FH6NavActionButton(
                                label = "B",
                                sub = if (isMap) "CLOSE MAP" else "BACK",
                                color = Color(0xFFFF1744),
                                actionId = "btn_b",
                                onTrigger = onTriggerAction
                            )
                        }
                        // A Button (Bottom, Green)
                        // In Map: Set Route | In Pause Menu: Select
                        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                            FH6NavActionButton(
                                label = "A",
                                sub = if (isMap) "SET ROUTE" else "SELECT",
                                color = Color(0xFF00E676),
                                actionId = "btn_a",
                                onTrigger = onTriggerAction
                            )
                        }
                    }
                }
            }

            // Footer Legend matching exact in-game prompts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isMap) {
                    Text(
                        text = "[A] SET ROUTE  â€¢  [B] CLOSE MAP  â€¢  [X] FAST TRAVEL  â€¢  [Y] REVEAL MAP  â€¢  [LB] REGIONS  â€¢  [RB] FILTER",
                        color = Color(0x99FFFFFF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ChakraPetch
                    )
                } else {
                    Text(
                        text = "[A] SELECT  â€¢  [B] BACK  â€¢  [X] RESET CAR POSITION  â€¢  [Y] CONVOY  â€¢  [LB] PREV TAB  â€¢  [RB] NEXT TAB",
                        color = Color(0x99FFFFFF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ChakraPetch
                    )
                }
            }
        }
    }
}

/**
 * Redesigned Ergonomic Motorsport Shoulder Bumper Wing (LB / RB)
 */
@Composable
private fun FH6MotorsportBumper(
    bumperKey: String, // "LB" or "RB"
    title: String,     // "PREV TAB" or "REGIONS", "NEXT TAB" or "FILTER"
    isLeft: Boolean,
    onPress: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val view = LocalView.current

    val shape = RoundedCornerShape(
        topStart = if (isLeft) 16.dp else 4.dp,
        topEnd = if (!isLeft) 16.dp else 4.dp,
        bottomStart = 4.dp,
        bottomEnd = 4.dp
    )

    Box(
        modifier = Modifier
            .width(134.dp)
            .height(46.dp)
            .clip(shape)
            .background(if (isPressed) Color(0xFFCCFF00) else Color(0x350A141E))
            .border(
                width = 1.5.dp,
                color = if (isPressed) Color.White else Color(0xFFCCFF00),
                shape = shape
            )
            .pointerInput(bumperKey) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onPress(true)
                        try {
                            tryAwaitRelease()
                        } finally {
                            isPressed = false
                            onPress(false)
                        }
                    }
                )
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (isLeft) {
                    Text(
                        text = "â—€",
                        color = if (isPressed) Color.Black else Color(0xFFCCFF00),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isPressed) Color.Black else Color(0x35CCFF00))
                        .padding(horizontal = 7.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = bumperKey,
                        color = if (isPressed) Color(0xFFCCFF00) else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = ChakraPetch
                    )
                }
                if (!isLeft) {
                    Text(
                        text = "â–¶",
                        color = if (isPressed) Color.Black else Color(0xFFCCFF00),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Text(
                text = title,
                color = if (isPressed) Color.Black else Color(0xCCFFFFFF),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                fontFamily = ChakraPetch
            )
        }
    }
}

/**
 * Combined 4-Way Arrow Key Pad with 360Â° Map Reticle Pan Cursor
 */
@Composable
private fun FH6MapCursorPad(
    isMap: Boolean,
    onTriggerAction: (actionId: String, isDown: Boolean) -> Unit,
    onAnalogMove: (x: Float, y: Float) -> Unit,
    onCenterClick: () -> Unit
) {
    val view = LocalView.current
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    val maxRadiusPx = 75f

    Box(
        modifier = Modifier
            .size(190.dp)
            .clip(CircleShape)
            .background(Color(0xFF071016))
            .border(2.dp, if (isMap) Color(0xFFCCFF00) else Color(0x60CCFF00), CircleShape)
            .pointerInput(Unit) {
                val deadzonePx = 5f
                fun computeCurvedMove(clamped: Offset, dist: Float) {
                    if (dist > deadzonePx) {
                        val normMag = ((dist.coerceAtMost(maxRadiusPx) - deadzonePx) / (maxRadiusPx - deadzonePx)).coerceIn(0f, 1f)
                        val curvedMag = Math.pow(normMag.toDouble(), 1.8).toFloat() * 0.40f
                        val angleX = clamped.x / dist
                        val angleY = -clamped.y / dist
                        onAnalogMove(angleX * curvedMag, angleY * curvedMag)
                    } else {
                        onAnalogMove(0f, 0f)
                    }
                }

                detectDragGestures(
                    onDragStart = { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val raw = offset - center
                        val dist = kotlin.math.sqrt(raw.x * raw.x + raw.y * raw.y)
                        val clamped = if (dist > maxRadiusPx) Offset(raw.x * (maxRadiusPx / dist), raw.y * (maxRadiusPx / dist)) else raw
                        thumbOffset = clamped
                        computeCurvedMove(clamped, dist)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val next = thumbOffset + dragAmount
                        val dist = kotlin.math.sqrt(next.x * next.x + next.y * next.y)
                        val clamped = if (dist > maxRadiusPx) Offset(next.x * (maxRadiusPx / dist), next.y * (maxRadiusPx / dist)) else next
                        thumbOffset = clamped
                        computeCurvedMove(clamped, dist)
                    },
                    onDragEnd = {
                        thumbOffset = Offset.Zero
                        onAnalogMove(0f, 0f)
                    },
                    onDragCancel = {
                        thumbOffset = Offset.Zero
                        onAnalogMove(0f, 0f)
                    }
                )
            },
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
            FH6TactileArrowKey(arrow = "â–²", sub = "UP", actionId = "dpad_up", onTrigger = onTriggerAction)
        }
        // DOWN
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)) {
            FH6TactileArrowKey(arrow = "â–¼", sub = "DOWN", actionId = "dpad_down", onTrigger = onTriggerAction)
        }
        // LEFT
        Box(modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp)) {
            FH6TactileArrowKey(arrow = "â—€", sub = "LEFT", actionId = "dpad_left", onTrigger = onTriggerAction)
        }
        // RIGHT
        Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp)) {
            FH6TactileArrowKey(arrow = "â–¶", sub = "RIGHT", actionId = "dpad_right", onTrigger = onTriggerAction)
        }

        // Center Reticle Joystick Puck (moves when dragged, renders authentic Forza Map Reticle)
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.x.roundToInt(), thumbOffset.y.roundToInt()) }
                .size(54.dp)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onCenterClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = (minOf(size.width, size.height) / 2f) * 0.92f
                val voltLime = Color(0xFFCCFF00)

                // Background puck
                drawCircle(Color(0xF008121A), radius = r)
                drawCircle(voltLime, radius = r, style = Stroke(width = 2.4f))

                // Inner concentric ring
                drawCircle(
                    Color.White.copy(alpha = 0.5f),
                    radius = r * 0.58f,
                    style = Stroke(width = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
                )

                // Crosshair tick marks at 0, 90, 180, 270 degrees
                val tickLen = r * 0.28f
                drawLine(voltLime, Offset(cx, cy - r), Offset(cx, cy - r + tickLen), strokeWidth = 2.5f)
                drawLine(voltLime, Offset(cx, cy + r - tickLen), Offset(cx, cy + r), strokeWidth = 2.5f)
                drawLine(voltLime, Offset(cx - r, cy), Offset(cx - r + tickLen, cy), strokeWidth = 2.5f)
                drawLine(voltLime, Offset(cx + r - tickLen, cy), Offset(cx + r, cy), strokeWidth = 2.5f)

                // Center navigation pointer arrow / reticle point
                drawCircle(Color.White, radius = 2.5f, center = Offset(cx, cy))
                val pointer = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx, cy - r * 0.35f)
                    lineTo(cx + r * 0.24f, cy + r * 0.24f)
                    lineTo(cx, cy + r * 0.10f)
                    lineTo(cx - r * 0.24f, cy + r * 0.24f)
                    close()
                }
                drawPath(pointer, voltLime)
            }
        }
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
            .size(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPressed) Color(0xFFCCFF00) else Color(0xF50D1822))
            .border(1.5.dp, if (isPressed) Color.White else Color(0x60CCFF00), RoundedCornerShape(8.dp))
            .pointerInput(actionId) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onTrigger(actionId, true)
                        try {
                            tryAwaitRelease()
                        } finally {
                            isPressed = false
                            onTrigger(actionId, false)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = arrow,
                color = if (isPressed) Color.Black else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = sub,
                color = if (isPressed) Color(0x99000000) else Color(0x99CCFF00),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = ChakraPetch
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
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onTrigger(actionId, true)
                        try {
                            tryAwaitRelease()
                        } finally {
                            isPressed = false
                            onTrigger(actionId, false)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                color = if (isPressed) Color.Black else color,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                fontFamily = ChakraPetch
            )
            Text(
                text = sub,
                color = if (isPressed) Color(0x99000000) else Color(0xB3FFFFFF),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = ChakraPetch
            )
        }
    }
}
