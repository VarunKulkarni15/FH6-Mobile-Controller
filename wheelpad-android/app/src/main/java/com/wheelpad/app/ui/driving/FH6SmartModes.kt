package com.wheelpad.app.ui.driving

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelpad.app.ui.theme.ChakraPetch

/**
 * Authentic Forza Horizon 6 Modal Dialog for Pause Menu & Car Selection Navigation
 * Pops up when the player presses [≡] MENU or [⧉] MAP so the steering wheel is never displaced.
 * Styled 100% in Forza Volt Lime (#CCFF00), Dark Slate (#080E14), and Carbon White.
 * ZERO BLUE / ZERO CYAN.
 */
@Composable
fun FH6MenuNavDialog(
    onDismiss: () -> Unit,
    onTriggerAction: (actionId: String, isDown: Boolean) -> Unit
) {
    val view = LocalView.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD903080C))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.68f)
                .widthIn(min = 440.dp, max = 560.dp)
                .heightIn(min = 280.dp, max = 340.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xF80B151C), Color(0xFC050B10))
                    )
                )
                .border(2.dp, Color(0xFFCCFF00), RoundedCornerShape(12.dp))
                .clickable(enabled = false) {}
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // [LB] Prev Tab / Category
                FH6MotorsportBumperTab(
                    label = "◀ TAB [LB]",
                    onPress = { isDown ->
                        if (isDown) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onTriggerAction("btn_lb", isDown)
                    }
                )

                // Tactical Center HUD Badge with 3-horizontal lines menu icon [≡]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFCCFF00)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(10.dp)) {
                            val stroke = 1.6f
                            drawLine(Color.Black, Offset(1f, 2f), Offset(size.width - 1f, 2f), strokeWidth = stroke)
                            drawLine(Color.Black, Offset(1f, size.height / 2f), Offset(size.width - 1f, size.height / 2f), strokeWidth = stroke)
                            drawLine(Color.Black, Offset(1f, size.height - 2f), Offset(size.width - 1f, size.height - 2f), strokeWidth = stroke)
                        }
                    }

                    Text(
                        text = "FORZA // MENU & CAR SELECTION [≡]",
                        color = Color(0xFFCCFF00),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )
                }

                // [RB] Next Tab / Category
                FH6MotorsportBumperTab(
                    label = "[RB] TAB ▶",
                    onPress = { isDown ->
                        if (isDown) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onTriggerAction("camera", isDown)
                    }
                )
            }

            // Center: 4-Way Arrow Key Pad (Tactile chevron arrow keys)
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF04080C))
                    .border(1.5.dp, Color(0x50CCFF00), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Cross guidelines
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    drawLine(Color(0x25CCFF00), Offset(cx, 8f), Offset(cx, size.height - 8f), strokeWidth = 1.2f)
                    drawLine(Color(0x25CCFF00), Offset(8f, cy), Offset(size.width - 8f, cy), strokeWidth = 1.2f)
                }

                // Arrow UP
                Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp)) {
                    FH6DirectionArrowKey(arrow = "▲", sub = "UP", actionId = "dpad_up", onTrigger = onTriggerAction)
                }

                // Arrow DOWN
                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)) {
                    FH6DirectionArrowKey(arrow = "▼", sub = "DOWN", actionId = "dpad_down", onTrigger = onTriggerAction)
                }

                // Arrow LEFT
                Box(modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
                    FH6DirectionArrowKey(arrow = "◀", sub = "LEFT", actionId = "dpad_left", onTrigger = onTriggerAction)
                }

                // Arrow RIGHT
                Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) {
                    FH6DirectionArrowKey(arrow = "▶", sub = "RIGHT", actionId = "dpad_right", onTrigger = onTriggerAction)
                }

                // Center Hub Button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFCCFF00))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "RESUME", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        Text(text = "[≡]", color = Color(0x99000000), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Footer HUD Badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FH6MiniHudBadge(badge = "A", label = "SELECT", color = Color(0xFF00E676))

                // Resume Driving Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFCCFF00))
                        .clickable { onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "RESUME DRIVING [≡]",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                FH6MiniHudBadge(badge = "B", label = "BACK", color = Color(0xFFFF1744))
            }
        }
    }
}

/**
 * Authentic Forza Horizon Live Prompt Bar for OBS / Viewers
 * Automatically displays active controller button states in real-time.
 */
@Composable
fun FH6LiveViewerPromptBar(
    buttonsBitmask: Int,
    isMenuNavActive: Boolean,
    isAutoDriveActive: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xD9040A0F))
            .border(1.dp, Color(0x40CCFF00), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isMenuPressed = (buttonsBitmask and (1 shl 4)) != 0 || isMenuNavActive
        FH6PromptItem(icon = "≡", label = "MENU", isPressed = isMenuPressed)

        val isViewPressed = (buttonsBitmask and (1 shl 3)) != 0
        FH6PromptItem(icon = "⧉", label = "MAP", isPressed = isViewPressed)

        val isLbPressed = (buttonsBitmask and (1 shl 7)) != 0
        FH6PromptItem(
            icon = "LB",
            label = if (isAutoDriveActive) "CINEMATIC CAM" else "TAB ◀",
            isPressed = isLbPressed,
            highlightColor = Color(0xFFCCFF00)
        )

        val isRbPressed = (buttonsBitmask and (1 shl 2)) != 0
        FH6PromptItem(icon = "RB", label = "▶ TAB", isPressed = isRbPressed)

        val isAPressed = (buttonsBitmask and (1 shl 0)) != 0
        FH6PromptItem(icon = "A", label = "SELECT", isPressed = isAPressed, highlightColor = Color(0xFF00E676))

        val isBPressed = (buttonsBitmask and (1 shl 6)) != 0
        FH6PromptItem(icon = "B", label = "BACK", isPressed = isBPressed, highlightColor = Color(0xFFFF1744))

        val isXPressed = (buttonsBitmask and (1 shl 5)) != 0
        FH6PromptItem(icon = "X", label = "ENTER", isPressed = isXPressed, highlightColor = Color(0xFFCCFF00))

        val isYPressed = (buttonsBitmask and (1 shl 1)) != 0
        FH6PromptItem(icon = "Y", label = "REWIND", isPressed = isYPressed, highlightColor = Color(0xFFCCFF00))
    }
}

@Composable
private fun FH6PromptItem(
    icon: String,
    label: String,
    isPressed: Boolean,
    highlightColor: Color = Color(0xFFCCFF00)
) {
    val bg = if (isPressed) highlightColor else Color(0x18FFFFFF)
    val textColor = if (isPressed) Color.Black else Color.White
    val subColor = if (isPressed) Color(0xCC000000) else Color(0x80FFFFFF)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(0.8.dp, if (isPressed) highlightColor else Color(0x30FFFFFF), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(text = icon, color = textColor, fontSize = 9.sp, fontWeight = FontWeight.Black)
        Text(text = label, color = subColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FH6MotorsportBumperTab(
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
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = if (isPressed) Color.Black else Color(0xFFCCFF00),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun FH6DirectionArrowKey(
    arrow: String,
    sub: String,
    actionId: String,
    onTrigger: (actionId: String, isDown: Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val view = LocalView.current

    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPressed) Color(0xFFCCFF00) else Color(0xF50A141A))
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
                fontSize = 15.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = sub,
                color = if (isPressed) Color(0xCC000000) else Color(0xAAFFFFFF),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FH6MiniHudBadge(
    badge: String,
    label: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(text = badge, color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
        Text(text = label, color = Color(0xCCFFFFFF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
