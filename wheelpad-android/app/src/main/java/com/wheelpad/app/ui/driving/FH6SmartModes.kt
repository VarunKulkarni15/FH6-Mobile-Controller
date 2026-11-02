package com.wheelpad.app.ui.driving

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import kotlin.math.roundToInt
import com.wheelpad.app.ui.theme.ChakraPetch

/**
 * Authentic Forza Horizon Dedicated Settings, Pause & World Map Navigation Screen
 * Displays tactical Arrow Keys + 360° Map Cursor Glide on the Left,
 * Tab Bumpers [LB]/[RB] at Top,
 * and Face Buttons [A] Select/Set Route, [B] Back/Close Map, [X] Enter/Fast Travel, [Y] Reset/Reveal.
 * Dispatches both Xbox Controller and PC Keyboard/Mouse so it works with 100% responsiveness.
 */
@Composable
fun FH6SettingsNavScreen(
    onTriggerAction: (actionId: String, isDown: Boolean) -> Unit,
    onAnalogMove: (x: Float, y: Float) -> Unit,
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
                            .size(26.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFCCFF00)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(14.dp)) {
                            val stroke = 2.2f
                            drawLine(Color.Black, Offset(1f, 3f), Offset(size.width - 1f, 3f), strokeWidth = stroke)
                            drawLine(Color.Black, Offset(1f, size.height / 2f), Offset(size.width - 1f, size.height / 2f), strokeWidth = stroke)
                            drawLine(Color.Black, Offset(1f, size.height - 3f), Offset(size.width - 1f, size.height - 3f), strokeWidth = stroke)
                        }
                    }

                    Text(
                        text = "FORZA HORIZON // PAUSE & WORLD MAP",
                        color = Color(0xFFCCFF00),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        fontFamily = ChakraPetch
                    )
                }

                // Resume Driving Button (unpauses game on PC and returns to driving rig)
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
                        letterSpacing = 1.sp,
                        fontFamily = ChakraPetch
                    )
                }
            }

            // Main Content Area: Left Arrow / Map Cursor Pad + Right Action Face Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Side: 4-Way Arrow Key Pad + Map Reticle Cursor Glide
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
                            label = "◀ PREV [LB]",
                            onPress = { isDown ->
                                if (isDown) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onTriggerAction("btn_lb", isDown)
                            }
                        )
                        FH6NavBumperTab(
                            label = "[RB] NEXT ▶",
                            onPress = { isDown ->
                                if (isDown) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onTriggerAction("btn_rb", isDown)
                            }
                        )
                    }

                    // Combined Tactile 4-Way Arrow Pad + 360° Map Cursor Glide
                    FH6MapCursorPad(
                        onTriggerAction = onTriggerAction,
                        onAnalogMove = onAnalogMove,
                        onCenterClick = {
                            onTriggerAction("btn_a", true)
                            onTriggerAction("btn_a", false)
                        }
                    )
                }

                // Right Side: Standard Xbox Diamond [Y, X, B, A] with authentic Xbox Colors
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
                        letterSpacing = 1.sp,
                        fontFamily = ChakraPetch
                    )

                    // Face Button Diamond
                    Box(
                        modifier = Modifier.size(190.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Y Button (Top, Yellow/Volt Lime)
                        Box(modifier = Modifier.align(Alignment.TopCenter)) {
                            FH6NavActionButton(label = "Y", sub = "REVEAL / CONVOY", color = Color(0xFFCCFF00), actionId = "rewind", onTrigger = onTriggerAction)
                        }
                        // X Button (Left, Authentic Xbox Blue!)
                        Box(modifier = Modifier.align(Alignment.CenterStart)) {
                            FH6NavActionButton(label = "X", sub = "FAST TRAVEL", color = Color(0xFF00B0FF), actionId = "btn_x", onTrigger = onTriggerAction)
                        }
                        // B Button (Right, Red)
                        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                            FH6NavActionButton(label = "B", sub = "BACK / CLOSE", color = Color(0xFFFF1744), actionId = "btn_b", onTrigger = onTriggerAction)
                        }
                        // A Button (Bottom, Green)
                        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                            FH6NavActionButton(label = "A", sub = "SELECT / ROUTE", color = Color(0xFF00E676), actionId = "btn_a", onTrigger = onTriggerAction)
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
                Text(text = "[A] SET ROUTE  •  [B] CLOSE MAP  •  [X] FAST TRAVEL  •  [Y] REVEAL", color = Color(0x99FFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = ChakraPetch)
                
            }
        }
    }
}

/**
 * Combined 4-Way Arrow Key Pad with 360° Map Reticle Pan Cursor
 */
@Composable
private fun FH6MapCursorPad(
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
            .border(2.dp, Color(0x60CCFF00), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val raw = offset - center
                        val dist = kotlin.math.sqrt(raw.x * raw.x + raw.y * raw.y)
                        val clamped = if (dist > maxRadiusPx) Offset(raw.x * (maxRadiusPx / dist), raw.y * (maxRadiusPx / dist)) else raw
                        thumbOffset = clamped
                        onAnalogMove(clamped.x / maxRadiusPx, -clamped.y / maxRadiusPx)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val next = thumbOffset + dragAmount
                        val dist = kotlin.math.sqrt(next.x * next.x + next.y * next.y)
                        val clamped = if (dist > maxRadiusPx) Offset(next.x * (maxRadiusPx / dist), next.y * (maxRadiusPx / dist)) else next
                        thumbOffset = clamped
                        onAnalogMove(clamped.x / maxRadiusPx, -clamped.y / maxRadiusPx)
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
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
            fontFamily = ChakraPetch
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
            .size(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPressed) Color(0xFFCCFF00) else Color(0xF50D1822))
            .border(1.5.dp, if (isPressed) Color.White else Color(0x60CCFF00), RoundedCornerShape(8.dp))
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
