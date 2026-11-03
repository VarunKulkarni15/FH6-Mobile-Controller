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
    onTriggerAction: (actionId: String, isDown: Boolean) -> Unit,
    onAnalogMove: (x: Float, y: Float) -> Unit,
    onResumeGame: (depth: Int) -> Unit,
    onDirectReturnToDrive: () -> Unit = { onResumeGame(0) }
) {
    val view = LocalView.current
    val isMap = currentMode.equals("MAP", ignoreCase = true)
    var menuDepth by remember { mutableStateOf(0) }


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
            // Top Header: Title + Action Buttons
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

                    val modeTitle = if (isMap) {
                        "FORZA HORIZON // WORLD MAP"
                    } else {
                        if (menuDepth == 0) "FORZA HORIZON // PAUSE MENU"
                        else if (menuDepth == 1) "FORZA HORIZON // SUBMENU [LVL 1]"
                        else if (menuDepth == 2) "FORZA HORIZON // GARAGE [LVL 2]"
                        else "FORZA HORIZON // CAR ACTIONS [LVL $menuDepth]"
                    }

                    Text(
                        text = modeTitle,
                        color = Color(0xFFCCFF00),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        fontFamily = ChakraPetch
                    )
                }

                // Right: Action Buttons (Direct Drive Cockpit [✕] + Resume/Close Map)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Direct Instant Return to Cockpit (Zero PC keystroke - perfect if game is already unpaused on PC)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x350A141E))
                            .border(1.dp, Color(0x90CCFF00), RoundedCornerShape(6.dp))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                menuDepth = 0
                                onDirectReturnToDrive()
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "DRIVE COCKPIT [✕]",
                            color = Color(0xFFCCFF00),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp,
                            fontFamily = ChakraPetch
                        )
                    }

                    // Primary Action: Unpause game in Forza & return to driving
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFCCFF00))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                val currentDepth = menuDepth
                                menuDepth = 0
                                onResumeGame(currentDepth)
                            }
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = if (isMap) "CLOSE MAP" else "RESUME GAME",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp,
                            fontFamily = ChakraPetch
                        )
                    }
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
                            onPress = { isDown ->
                                onTriggerAction("btn_lb", isDown)
                            }
                        )
                        FH6MotorsportBumper(
                            bumperKey = "RB",
                            title = if (isMap) "FILTER" else "NEXT TAB",
                            isLeft = false,
                            onPress = { isDown ->
                                onTriggerAction("btn_rb", isDown)
                            }
                        )
                    }

                    // Precision 4-Way Motorsport Directional D-Pad
                    FH6TactileDpad(
                        isMap = isMap,
                        onTriggerAction = onTriggerAction
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
                        // In Map: Close Map | In Pause Menu: Back -> Returns directly to driving rig when at root!
                        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                            FH6NavActionButton(
                                label = "B",
                                sub = if (isMap) "CLOSE MAP" else "BACK",
                                color = Color(0xFFFF1744),
                                actionId = "btn_b",
                                onTrigger = { id, down ->
                                    if (down) {
                                        if (isMap) {
                                            onResumeGame(0)
                                        } else {
                                            if (menuDepth > 0) {
                                                menuDepth--
                                                onTriggerAction("btn_b", true)
                                                onTriggerAction("btn_b", false)
                                            } else {
                                                // At root menu: B exits pause menu directly to driving!
                                                onTriggerAction("btn_b", true)
                                                onTriggerAction("btn_b", false)
                                                menuDepth = 0
                                                onDirectReturnToDrive()
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        // A Button (Bottom, Green)
                        // In Map: Set Route | In Pause Menu: Select (tracks depth)
                        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                            FH6NavActionButton(
                                label = "A",
                                sub = if (isMap) "SET ROUTE" else "SELECT",
                                color = Color(0xFF00E676),
                                actionId = "btn_a",
                                onTrigger = { id, down ->
                                    if (down && !isMap) {
                                        menuDepth = (menuDepth + 1).coerceAtMost(4)
                                    }
                                    onTriggerAction(id, down)
                                }
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
                        text = "[A] SET ROUTE   |   [B] CLOSE MAP   |   [X] FAST TRAVEL   |   [Y] REVEAL MAP   |   [LB] REGIONS   |   [RB] FILTER",
                        color = Color(0x99FFFFFF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ChakraPetch
                    )
                } else {
                    Text(
                        text = "[A] SELECT   |   [B] BACK   |   [X] RESET CAR   |   [Y] CONVOY   |   [LB] PREV TAB   |   [RB] NEXT TAB",
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
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isPressed) Color.Black else Color(0x35CCFF00))
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = bumperKey,
                        color = if (isPressed) Color(0xFFCCFF00) else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = ChakraPetch
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
 * Precision 4-Way Motorsport Directional D-Pad for Menu & Map Navigation
 * Dedicated touch targets for UP, DOWN, LEFT, RIGHT with zero drag interference.
 */
@Composable
private fun FH6TactileDpad(
    isMap: Boolean,
    onTriggerAction: (actionId: String, isDown: Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .size(190.dp)
            .clip(CircleShape)
            .background(Color(0xFF071016))
            .border(2.dp, if (isMap) Color(0xFFCCFF00) else Color(0x60CCFF00), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawLine(Color(0x25CCFF00), Offset(cx, 10f), Offset(cx, size.height - 10f), strokeWidth = 1.5f)
            drawLine(Color(0x25CCFF00), Offset(10f, cy), Offset(size.width - 10f, cy), strokeWidth = 1.5f)
            drawCircle(Color(0x30CCFF00), radius = 28f, style = Stroke(width = 1.5f))
        }

        // UP
        Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp)) {
            FH6TactileArrowKey(label = "UP", actionId = "dpad_up", onTrigger = onTriggerAction)
        }
        // DOWN
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)) {
            FH6TactileArrowKey(label = "DOWN", actionId = "dpad_down", onTrigger = onTriggerAction)
        }
        // LEFT
        Box(modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp)) {
            FH6TactileArrowKey(label = "LEFT", actionId = "dpad_left", onTrigger = onTriggerAction)
        }
        // RIGHT
        Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp)) {
            FH6TactileArrowKey(label = "RIGHT", actionId = "dpad_right", onTrigger = onTriggerAction)
        }

        // Static Center Motorsport Emblem (No moving cursor, zero interference)
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Color(0xE608121A))
                .border(1.5.dp, Color(0xFFCCFF00), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = size.width / 2f
                drawCircle(Color(0xFFCCFF00), radius = 3f, center = Offset(cx, cy))
                drawLine(Color(0xFFCCFF00), Offset(cx - 8f, cy), Offset(cx + 8f, cy), strokeWidth = 1.5f)
                drawLine(Color(0xFFCCFF00), Offset(cx, cy - 8f), Offset(cx, cy + 8f), strokeWidth = 1.5f)
            }
        }
    }
}

@Composable
private fun FH6TactileArrowKey(
    label: String,
    actionId: String,
    onTrigger: (actionId: String, isDown: Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val view = LocalView.current

    Box(
        modifier = Modifier
            .size(50.dp)
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
        Text(
            text = label,
            color = if (isPressed) Color.Black else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            fontFamily = ChakraPetch
        )
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
