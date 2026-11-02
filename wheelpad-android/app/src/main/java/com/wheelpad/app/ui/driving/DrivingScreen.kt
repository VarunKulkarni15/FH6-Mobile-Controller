package com.wheelpad.app.ui.driving

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelpad.app.data.model.ControlShape
import com.wheelpad.app.data.model.GameAction
import com.wheelpad.app.data.model.InputType
import com.wheelpad.app.data.model.XboxButton
import com.wheelpad.app.data.repository.ProfileRepository
import com.wheelpad.app.network.ControlSyncClient
import com.wheelpad.app.network.UdpFastStreamClient
import com.wheelpad.app.ui.controls.ControlEditorViewModel
import com.wheelpad.app.ui.theme.ChakraPetch
import kotlin.math.atan2

/**
 * WheelPad Forza Horizon 6 Pure Minimalist Driving HUD
 * True Vector SVG Outlines matching shared-protocol SVG assets with pixel-smooth controls.
 */
@Composable
fun DrivingScreen(
    viewModel: ControlEditorViewModel,
    udpClient: UdpFastStreamClient,
    syncClient: ControlSyncClient? = null,
    onOpenConnectDialog: (() -> Unit)? = null,
    onOpenDiagnosticsDialog: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val view = LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current

    // Live continuous driving inputs
    var steeringAngle by remember { mutableStateOf(0f) }   // -1.0f to +1.0f
    var throttlePressure by remember { mutableStateOf(0f) } // 0.0f to 1.0f
    var brakePressure by remember { mutableStateOf(0f) }    // 0.0f to 1.0f
    var handbrakeState by remember { mutableStateOf(false) }
    var buttonsBitmask by remember { mutableStateOf(0) }

    // Dual Analog Sticks (For TLOU2 / Action Games)
    var leftStickX by remember { mutableStateOf(0f) }
    var leftStickY by remember { mutableStateOf(0f) }
    var rightStickX by remember { mutableStateOf(0f) }
    var rightStickY by remember { mutableStateOf(0f) }

    // HUD Quick Menu & Feature Toggles
    var isMenuOpen by remember { mutableStateOf(false) }
    var isGyroEnabled by remember { mutableStateOf(false) }

    val gyroManager = remember { com.wheelpad.app.sensor.GyroSteeringManager(context) }
    val gyroAngle by gyroManager.steeringAngle.collectAsState()
    var gyroSensitivity by remember { mutableStateOf(gyroManager.sensitivity) }

    // OTA Auto-Update Manager (manual check via 3-dots drawer menu)
    val updateManager = remember { com.wheelpad.app.updater.AutoUpdateManager(context) }
    val updateState by updateManager.updateState.collectAsState()

    // Gyroscope lifecycle
    DisposableEffect(isGyroEnabled) {
        if (isGyroEnabled) {
            gyroManager.start()
        } else {
            gyroManager.stop()
        }
        onDispose { gyroManager.stop() }
    }

    LaunchedEffect(gyroAngle, isGyroEnabled) {
        if (isGyroEnabled) {
            steeringAngle = gyroAngle
        }
    }

    val activity = context as? com.wheelpad.app.MainActivity
    val prefs = remember { context.getSharedPreferences("wheelpad_prefs", android.content.Context.MODE_PRIVATE) }
    var hwVolumeEnabled by remember { mutableStateOf(prefs.getBoolean("hw_volume_enabled", true)) }
    var hwVolumeUpAction by remember { mutableStateOf(prefs.getString("hw_volume_up_action", "camera") ?: "camera") }
    var hwVolumeDownAction by remember { mutableStateOf(prefs.getString("hw_volume_down_action", "btn_lb") ?: "btn_lb") }
    var pickingVolumeKeyTarget by remember { mutableStateOf<String?>(null) } // "UP", "DOWN", or null
    var isMenuNavActive by remember { mutableStateOf(false) }
    var isAutoDriveActive by remember { mutableStateOf(false) }

    val triggerAction: (String, Boolean) -> Unit = { actionId, isDown ->
        val bit = when (actionId) {
            "btn_a", "handbrake", "jump" -> 1 shl 0
            "rewind", "btn_y", "interact" -> 1 shl 1
            "camera", "btn_rb", "listen_mode" -> 1 shl 2
            "map", "btn_back", "btn_view", "backpack" -> 1 shl 3
            "pause", "btn_start", "btn_menu" -> 1 shl 4
            "btn_x", "strike", "activate", "shift_down" -> 1 shl 5
            "btn_b", "crouch", "shift_up" -> 1 shl 6
            "btn_lb", "clutch", "dodge" -> 1 shl 7
            "btn_ls", "convertible", "sprint" -> 1 shl 8
            "btn_rs", "horn" -> 1 shl 9
            "dpad_up", "photo_mode" -> 1 shl 10
            "dpad_down", "anna" -> 1 shl 11
            "dpad_left", "forza_link", "radio_prev" -> 1 shl 12
            "dpad_right", "radio_next" -> 1 shl 13
            "guide" -> 1 shl 14
            "auto_drive" -> 1 shl 15
            "look_back" -> 1 shl 16
            "look_left" -> 1 shl 17
            "look_right" -> 1 shl 18
            "look_forward" -> 1 shl 19
            else -> 0
        }

        // Smart Auto-Detection for Pause Menu [≡] and World Map [⧉]
        if ((actionId == "pause" || actionId == "btn_menu" || actionId == "btn_start" || actionId == "map" || actionId == "btn_view") && isDown) {
            isMenuNavActive = !isMenuNavActive
        } else if (actionId == "btn_b" && isDown && isMenuNavActive) {
            isMenuNavActive = false
        } else if (actionId == "auto_drive" && isDown) {
            isAutoDriveActive = !isAutoDriveActive
        }

        if (actionId == "throttle" || actionId == "fire_weapon") {
            throttlePressure = if (isDown) 1.0f else 0.0f
            if (isDown) {
                if (isAutoDriveActive) isAutoDriveActive = false
                if (isMenuNavActive) isMenuNavActive = false
            }
        } else if (actionId == "brake" || actionId == "aim_weapon") {
            brakePressure = if (isDown) 1.0f else 0.0f
            if (isDown) {
                if (isAutoDriveActive) isAutoDriveActive = false
                if (isMenuNavActive) isMenuNavActive = false
            }
        } else if (actionId == "handbrake") {
            handbrakeState = isDown
            if (isDown) buttonsBitmask = buttonsBitmask or (1 shl 0)
            else buttonsBitmask = buttonsBitmask and (1 shl 0).inv()
        } else if (actionId == "look_back") {
            rightStickY = if (isDown) -1.0f else 0.0f
            if (isDown) buttonsBitmask = buttonsBitmask or (1 shl 16)
            else buttonsBitmask = buttonsBitmask and (1 shl 16).inv()
        } else if (actionId == "look_left") {
            rightStickX = if (isDown) -1.0f else 0.0f
            if (isDown) buttonsBitmask = buttonsBitmask or (1 shl 17)
            else buttonsBitmask = buttonsBitmask and (1 shl 17).inv()
        } else if (actionId == "look_right") {
            rightStickX = if (isDown) 1.0f else 0.0f
            if (isDown) buttonsBitmask = buttonsBitmask or (1 shl 18)
            else buttonsBitmask = buttonsBitmask and (1 shl 18).inv()
        } else if (actionId == "look_forward") {
            rightStickY = if (isDown) 1.0f else 0.0f
            if (isDown) buttonsBitmask = buttonsBitmask or (1 shl 19)
            else buttonsBitmask = buttonsBitmask and (1 shl 19).inv()
        } else if (bit != 0) {
            if (isDown) buttonsBitmask = buttonsBitmask or bit
            else buttonsBitmask = buttonsBitmask and bit.inv()
        }
        syncClient?.testActionInput(actionId, isDown)
    }

    DisposableEffect(activity, hwVolumeEnabled, hwVolumeUpAction, hwVolumeDownAction) {
        activity?.onVolumeKeyEvent = { keyCode, isDown ->
            if (!hwVolumeEnabled) {
                false
            } else {
                val actionId = if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) hwVolumeUpAction else hwVolumeDownAction
                if (isDown) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                triggerAction(actionId, isDown)
                true
            }
        }
        onDispose {
            activity?.onVolumeKeyEvent = null
        }
    }

    // Continuously stream inputs to UdpFastStream (120Hz FastStream)
    LaunchedEffect(steeringAngle, throttlePressure, brakePressure, handbrakeState, buttonsBitmask, leftStickX, leftStickY, rightStickX, rightStickY) {
        udpClient.updateInputs(
            steering = steeringAngle,
            throttle = throttlePressure,
            brake = brakePressure,
            handbrake = handbrakeState,
            buttonsBitmask = buttonsBitmask,
            leftThumbY = leftStickY,
            rightThumbX = rightStickX,
            rightThumbY = rightStickY
        )
    }

    // Adaptive Fullscreen Dark Canvas (100% immersive, zero status/navigation bars)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF080B10))
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides androidx.compose.ui.text.TextStyle(fontFamily = ChakraPetch)
        ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val density = androidx.compose.ui.platform.LocalDensity.current
        val screenWidthPx = with(density) { screenWidth.toPx() }
        val screenHeightPx = with(density) { screenHeight.toPx() }

        // 1. Render all HUD controls from active profile
        state.currentProfile.layout.forEach { element ->
            val elementX = screenWidth * element.normalizedX
            val elementY = screenHeight * element.normalizedY
            val elementW = screenWidth * element.normalizedWidth
            val elementH = screenHeight * element.normalizedHeight
            val isSelected = state.isEditMode && state.selectedElementId == element.elementId

            Box(
                modifier = Modifier
                    .offset(x = elementX, y = elementY)
                    .size(width = elementW, height = elementH)
                    .then(
                        if (state.isEditMode) {
                            Modifier
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.2.dp,
                                    color = if (isSelected) Color(0xFFCCFF00) else Color(0x60FFFFFF),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    viewModel.selectElement(element.elementId)
                                }
                                .pointerInput(element.elementId) {
                                    detectDragGestures(
                                        onDragStart = {
                                            viewModel.selectElement(element.elementId)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val deltaNormX = dragAmount.x / screenWidthPx
                                            val deltaNormY = dragAmount.y / screenHeightPx
                                            viewModel.updateElementDeltaPosition(element.elementId, deltaNormX, deltaNormY)
                                        },
                                        onDragEnd = {
                                            viewModel.saveAndSyncProfile()
                                        },
                                        onDragCancel = {
                                            viewModel.saveAndSyncProfile()
                                        }
                                    )
                                }
                        } else {
                            Modifier
                        }
                    )
            ) {
                when (element.shape) {
                    ControlShape.CircleWheel -> {
                        FH6ExactSvgSteeringWheel(
                            isEditMode = state.isEditMode,
                            isGyroActive = isGyroEnabled,
                            currentAngleNormalized = steeringAngle,
                            onSteerChange = {
                                if (!isGyroEnabled) {
                                    steeringAngle = it
                                    if (isAutoDriveActive && kotlin.math.abs(it) > 0.15f) isAutoDriveActive = false
                                    if (isMenuNavActive && kotlin.math.abs(it) > 0.20f) isMenuNavActive = false
                                }
                            }
                        )
                    }
                    ControlShape.VerticalPedal -> {
                        val isThrottle = element.actionId == "throttle"
                        if (isThrottle) {
                            FH6ExactSvgAccelPedal(
                                isEditMode = state.isEditMode,
                                pressure = throttlePressure,
                                onPressureChange = { throttlePressure = it }
                            )
                        } else {
                            FH6ExactSvgBrakePedal(
                                isEditMode = state.isEditMode,
                                pressure = brakePressure,
                                onPressureChange = { brakePressure = it }
                            )
                        }
                    }
                    ControlShape.AnalogStick -> {
                        val isMove = element.actionId == "move_stick" || element.actionId == "left_stick"
                        FH6AnalogJoystick(
                            label = element.label,
                            accentColor = if (isMove) Color(0xFF00E5FF) else Color(0xFFCCFF00),
                            isEditMode = state.isEditMode,
                            onValueChange = { x, y ->
                                if (isMove) {
                                    leftStickX = x
                                    leftStickY = y
                                } else {
                                    rightStickX = x
                                    rightStickY = y
                                }
                            }
                        )
                    }
                    ControlShape.WideBar -> {
                        FH6ExactSvgHandbrake(
                            label = "HANDBRAKE",
                            subLabel = "SPACE / (A)",
                            isEditMode = state.isEditMode,
                            isPressed = handbrakeState,
                            onPressedChange = { pressed ->
                                if (pressed) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                handbrakeState = pressed
                            }
                        )
                    }
                    else -> {
                        val bit = when (element.actionId) {
                            "btn_a", "handbrake", "jump" -> 1 shl 0
                            "rewind", "btn_y", "interact" -> 1 shl 1
                            "camera", "btn_rb", "listen_mode" -> 1 shl 2
                            "map", "btn_back", "btn_view", "backpack" -> 1 shl 3
                            "pause", "btn_start", "btn_menu" -> 1 shl 4
                            "btn_x", "strike", "activate", "shift_down" -> 1 shl 5
                            "btn_b", "crouch", "shift_up" -> 1 shl 6
                            "btn_lb", "clutch", "dodge" -> 1 shl 7
                            "btn_ls", "convertible", "sprint" -> 1 shl 8
                            "btn_rs", "horn" -> 1 shl 9
                            "dpad_up", "photo_mode" -> 1 shl 10
                            "dpad_down", "anna" -> 1 shl 11
                            "dpad_left", "forza_link", "radio_prev" -> 1 shl 12
                            "dpad_right", "radio_next" -> 1 shl 13
                            "guide" -> 1 shl 14
                            "auto_drive" -> 1 shl 15
                            "look_back" -> 1 shl 16
                            "look_left" -> 1 shl 17
                            "look_right" -> 1 shl 18
                            "look_forward" -> 1 shl 19
                            else -> 0
                        }
                        val isTrigger = element.actionId == "aim_weapon" || element.actionId == "fire_weapon" || element.actionId == "throttle" || element.actionId == "brake"
                        val isPressed = if (isTrigger) {
                            if (element.actionId == "aim_weapon" || element.actionId == "brake") brakePressure > 0.5f else throttlePressure > 0.5f
                        } else if (bit != 0) {
                            ((buttonsBitmask and bit) != 0)
                        } else false

                        FH6ExactSvgActionButton(
                            actionId = element.actionId,
                            label = element.label,
                            subLabel = when (element.actionId) {
                                "rewind", "btn_y" -> "(Y)"
                                "btn_a", "handbrake" -> "(A)"
                                "btn_b", "shift_up" -> "(B)"
                                "btn_x", "activate", "shift_down" -> "(X)"
                                "camera", "btn_rb" -> "(RB)"
                                "btn_lb", "clutch" -> "(LB)"
                                "horn", "btn_rs" -> "(RS)"
                                "look_back" -> "(RS DOWN)"
                                "look_left" -> "(RS LEFT)"
                                "look_right" -> "(RS RIGHT)"
                                "look_forward" -> "(RS UP)"
                                "convertible", "btn_ls" -> "(LS)"
                                "photo_mode", "dpad_up" -> "(D-UP)"
                                "radio_next", "dpad_right" -> "(D-RIGHT)"
                                "radio_prev" -> "(D-LEFT)"
                                "dpad_down", "anna" -> "(ANNA)"
                                "dpad_left", "forza_link" -> "(LINK)"
                                "map", "btn_back", "btn_view" -> "(VIEW)"
                                "pause", "btn_start", "btn_menu" -> "(START)"
                                "auto_drive" -> "(AUTO)"
                                "steer" -> "(LS)"
                                "throttle" -> "(RT)"
                                "brake" -> "(LT)"
                                else -> ""
                            },
                            isEditMode = state.isEditMode,
                            isPressed = isPressed,
                            onPressedChange = { pressed ->
                                if (pressed) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                                triggerAction(element.actionId, pressed)
                            }
                        )
                    }
                }
            }
        }

        // 2. Discreet 3-Dots Corner Menu Button (Crisp Vector Canvas)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 16.dp)
                .size(38.dp)
                .clip(CircleShape)
                .border(1.dp, Color(0x60FFFFFF), CircleShape)
                .background(Color(0x35101522))
                .clickable { isMenuOpen = !isMenuOpen },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(16.dp)) {
                val r = 2.dp.toPx()
                drawCircle(Color.White, radius = r, center = Offset(size.width / 2f, 3.dp.toPx()))
                drawCircle(Color.White, radius = r, center = Offset(size.width / 2f, size.height / 2f))
                drawCircle(Color.White, radius = r, center = Offset(size.width / 2f, size.height - 3.dp.toPx()))
            }
        }

        // 2b. Authentic Forza Horizon Live Controller Prompt Bar for OBS / Viewers
        if (!state.isEditMode) {
            FH6LiveViewerPromptBar(
                buttonsBitmask = buttonsBitmask,
                isMenuNavActive = isMenuNavActive,
                isAutoDriveActive = isAutoDriveActive,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            )
        }

        // 3. Edit Mode Floating Bottom Dock (Volt Lime Accents, ZERO OVERLAPS)
        if (state.isEditMode) {
            val selectedElem = state.currentProfile.layout.find { it.elementId == state.selectedElementId }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xF5062220))
                    .border(1.5.dp, Color(0xFFCCFF00), RoundedCornerShape(22.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reset to default layout button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x25FF0055))
                            .border(1.dp, Color(0xFFFF0055), RoundedCornerShape(14.dp))
                            .clickable { viewModel.resetToDefaultLayout() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("RESET DEFAULT", color = Color(0xFFFF0055), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }

                    // Selected element controls (Fine-grained pixel scale & delete)
                    if (selectedElem != null) {
                        Text(
                            text = selectedElem.label,
                            color = Color(0xFFCCFF00),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )

                        Text("SIZE:", color = Color(0x99FFFFFF), fontSize = 9.sp, fontWeight = FontWeight.Bold)

                        // Fine Scale - (1.5% delta)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0x80FFFFFF), RoundedCornerShape(6.dp))
                                .clickable {
                                    viewModel.updateElementDeltaSize(selectedElem.elementId, -0.015f)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("-", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        // Fine Scale + (1.5% delta)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0x80FFFFFF), RoundedCornerShape(6.dp))
                                .clickable {
                                    viewModel.updateElementDeltaSize(selectedElem.elementId, 0.015f)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        // Delete button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0x25FF1744))
                                .border(1.dp, Color(0xFFFF1744), RoundedCornerShape(14.dp))
                                .clickable { viewModel.removeElement(selectedElem.elementId) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("REMOVE", color = Color(0xFFFF5252), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Add Action button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.2.dp, Color(0xFFCCFF00), RoundedCornerShape(14.dp))
                            .clickable { viewModel.setActionCatalogVisible(true) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("+ ADD ACTION", color = Color(0xFFCCFF00), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // Done / Exit Edit Mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFCCFF00))
                            .clickable { viewModel.toggleEditMode() }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("DONE", color = Color(0xFF062220), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        // 3b. Authentic FH6 Motorsport OTA Notification Modal
        FH6UpdateNotificationOverlay(
            updateState = updateState,
            updateManager = updateManager
        )

        // 4. Authentic Forza Horizon Action Catalog Modal Dialog
        if (state.isActionCatalogVisible) {
            FH6ActionCatalogDialog(
                onDismiss = { viewModel.setActionCatalogVisible(false) },
                onSelectAction = { viewModel.addActionToLayout(it) }
            )
        }

        // 4c. Authentic Forza Horizon Menu & Car Selection Navigation Modal
        if (isMenuNavActive) {
            FH6MenuNavDialog(
                onDismiss = { isMenuNavActive = false },
                onTriggerAction = triggerAction
            )
        }

        // 4b. Dedicated Hardware Volume Key Action Picker Dialog
        if (pickingVolumeKeyTarget != null) {
            val targetKey = pickingVolumeKeyTarget!!
            FH6VolumeKeyPickerDialog(
                targetKey = targetKey,
                onDismiss = { pickingVolumeKeyTarget = null },
                onAssign = { actionId ->
                    if (targetKey == "UP") {
                        hwVolumeUpAction = actionId
                        prefs.edit().putString("hw_volume_up_action", actionId).apply()
                    } else {
                        hwVolumeDownAction = actionId
                        prefs.edit().putString("hw_volume_down_action", actionId).apply()
                    }
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    pickingVolumeKeyTarget = null
                }
            )
        }

        // 5. Sleek HUD Quick Menu Drawer (Forza Horizon Styling, Fully Scrollable)
        if (isMenuOpen && !state.isEditMode) {
            FH6QuickSettingsDrawer(
                onDismiss = { isMenuOpen = false },
                onOpenConnectDialog = onOpenConnectDialog,
                onOpenDiagnosticsDialog = onOpenDiagnosticsDialog,
                isGyroEnabled = isGyroEnabled,
                onToggleGyro = { isGyroEnabled = !isGyroEnabled },
                gyroAngle = gyroAngle,
                gyroSensitivity = gyroSensitivity,
                onSensitivityChange = {
                    gyroSensitivity = it
                    gyroManager.sensitivity = it
                },
                hwVolumeEnabled = hwVolumeEnabled,
                onToggleHwVolume = {
                    hwVolumeEnabled = !hwVolumeEnabled
                    prefs.edit().putBoolean("hw_volume_enabled", hwVolumeEnabled).apply()
                },
                hwVolumeUpAction = hwVolumeUpAction,
                hwVolumeDownAction = hwVolumeDownAction,
                onPickVolumeKey = { pickingVolumeKeyTarget = it },
                onSetVolumePreset = { up, down ->
                    hwVolumeUpAction = up
                    hwVolumeDownAction = down
                    prefs.edit().putString("hw_volume_up_action", up).putString("hw_volume_down_action", down).apply()
                },
                onEditControls = { viewModel.toggleEditMode() },
                onResetLayout = { viewModel.resetToDefaultLayout() },
                updateManager = updateManager
            )
        }
    }
}
}

/**
 * EXACT SVG Implementation of fh6_steering_wheel.svg
 * ViewBox: 0 0 500 500
 */
@Composable
fun FH6ExactSvgSteeringWheel(
    isEditMode: Boolean,
    isGyroActive: Boolean,
    currentAngleNormalized: Float,
    onSteerChange: (Float) -> Unit
) {
    val targetDegrees = currentAngleNormalized * 90f

    val visualRotation by animateFloatAsState(
        targetValue = targetDegrees,
        animationSpec = tween(durationMillis = if (isGyroActive) 15 else 30),
        label = "WheelRotation"
    )

    val density = androidx.compose.ui.platform.LocalDensity.current
    var touchActive by remember { mutableStateOf(false) }
    var touchCurrent by remember { mutableStateOf(Offset.Zero) }
    var anchorX by remember { mutableStateOf(0f) }
    val maxTravelPx = with(density) { 56.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isEditMode, isGyroActive) {
                if (!isEditMode && !isGyroActive) {
                    awaitPointerEventScope {
                        var trackedId: androidx.compose.ui.input.pointer.PointerId? = null
                        while (true) {
                            val event = awaitPointerEvent()
                            if (trackedId == null) {
                                val down = event.changes.firstOrNull { it.pressed && !it.isConsumed }
                                if (down != null) {
                                    trackedId = down.id
                                    down.consume()
                                    touchActive = true
                                    touchCurrent = down.position
                                    anchorX = down.position.x
                                    onSteerChange(0f)
                                }
                            } else {
                                val change = event.changes.firstOrNull { it.id == trackedId }
                                if (change != null) {
                                    if (change.pressed) {
                                        change.consume()
                                        touchCurrent = change.position
                                        val touchX = change.position.x
                                        var dx = touchX - anchorX

                                        // Dynamic floating anchor tracking
                                        if (dx > maxTravelPx) {
                                            anchorX = touchX - maxTravelPx
                                            dx = maxTravelPx
                                        } else if (dx < -maxTravelPx) {
                                            anchorX = touchX + maxTravelPx
                                            dx = -maxTravelPx
                                        }

                                        val normSteer = (dx / maxTravelPx).coerceIn(-1.0f, 1.0f)
                                        val deadzone = 0.03f
                                        val absVal = kotlin.math.abs(normSteer)
                                        val signVal = kotlin.math.sign(normSteer)
                                        val finalSteer = if (absVal <= deadzone) 0f else signVal * ((absVal - deadzone) / (1f - deadzone))
                                        onSteerChange(finalSteer)
                                    } else {
                                        change.consume()
                                        trackedId = null
                                        touchActive = false
                                        onSteerChange(0f)
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val s = size.minDimension / 500f
            val center = Offset(size.width / 2f, size.height / 2f)

            rotate(degrees = visualRotation, pivot = center) {
                scale(scale = s, pivot = center) {
                    // 1. Outer Wheel Rim Ring (cx=250, cy=250, r=230, stroke-width=2.2)
                    drawCircle(
                        color = Color.White,
                        radius = 230f,
                        center = center,
                        style = Stroke(width = 2.2f)
                    )

                    // 2. Inner dashed circle (r=202, dash: 10 6)
                    drawCircle(
                        color = Color(0x4DFFFFFF),
                        radius = 202f,
                        center = center,
                        style = Stroke(
                            width = 1.2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                        )
                    )

                    // 3. Left Thumb Grip Arc (stroke-width=5)
                    val leftGrip = Path().apply {
                        val rect = Rect(center.x - 230f, center.y - 230f, center.x + 230f, center.y + 230f)
                        // Arc from 205 deg to 295 deg
                        arcTo(rect, 168f, 24f, false)
                    }
                    drawPath(leftGrip, Color.White, style = Stroke(width = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))

                    // 4. Right Thumb Grip Arc (stroke-width=5)
                    val rightGrip = Path().apply {
                        val rect = Rect(center.x - 230f, center.y - 230f, center.x + 230f, center.y + 230f)
                        arcTo(rect, -12f, 24f, false)
                    }
                    drawPath(rightGrip, Color.White, style = Stroke(width = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))

                    // 5. Top 12 O'Clock Center Alignment Notch (x1=250, y1=12 to 42, dot at y=48)
                    drawLine(
                        color = Color.White,
                        start = Offset(center.x, center.y - 238f),
                        end = Offset(center.x, center.y - 208f),
                        strokeWidth = 4f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    drawCircle(color = Color.White, radius = 3f, center = Offset(center.x, center.y - 202f))

                    // 6. Left Horizontal Spoke (9 o'clock)
                    val spoke9 = Path().apply {
                        moveTo(center.x - 202f, center.y)
                        lineTo(center.x - 55f, center.y - 10f)
                        lineTo(center.x - 55f, center.y + 10f)
                        close()
                    }
                    drawPath(spoke9, Color.White, style = Stroke(width = 1.5f))
                    drawLine(Color(0xB3FFFFFF), Offset(center.x - 175f, center.y), Offset(center.x - 65f, center.y), strokeWidth = 1.2f)

                    // 7. Right Horizontal Spoke (3 o'clock)
                    val spoke3 = Path().apply {
                        moveTo(center.x + 202f, center.y)
                        lineTo(center.x + 55f, center.y - 10f)
                        lineTo(center.x + 55f, center.y + 10f)
                        close()
                    }
                    drawPath(spoke3, Color.White, style = Stroke(width = 1.5f))
                    drawLine(Color(0xB3FFFFFF), Offset(center.x + 175f, center.y), Offset(center.x + 65f, center.y), strokeWidth = 1.2f)

                    // 8. Bottom Vertical Spoke (6 o'clock)
                    val spoke6 = Path().apply {
                        moveTo(center.x, center.y + 202f)
                        lineTo(center.x - 10f, center.y + 55f)
                        lineTo(center.x + 10f, center.y + 55f)
                        close()
                    }
                    drawPath(spoke6, Color.White, style = Stroke(width = 1.5f))
                    drawLine(Color(0xB3FFFFFF), Offset(center.x, center.y + 175f), Offset(center.x, center.y + 65f), strokeWidth = 1.2f)

                    // 9. Center Hub Concentric Rings (r=58, r=44, r=20) with Volt Lime FH Core
                    drawCircle(Color.White, radius = 58f, center = center, style = Stroke(width = 1.8f))
                    drawCircle(Color(0x59FFFFFF), radius = 44f, center = center, style = Stroke(width = 1.0f))
                    drawCircle(Color.White, radius = 20f, center = center, style = Stroke(width = 1.5f))
                    drawCircle(Color(0x40CCFF00), radius = 18f, center = center)
                    drawCircle(Color(0xFFCCFF00), radius = 6f, center = center)
                }
            }
        }

        // Target ring feedback centered on the wheel's touch location
        if (touchActive && !isGyroActive && !isEditMode) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Outer Volt Lime glow ring
                drawCircle(
                    color = Color(0x33CCFF00),
                    radius = with(density) { 36.dp.toPx() },
                    center = touchCurrent,
                    style = Stroke(width = with(density) { 2.dp.toPx() })
                )
                // Inner solid Volt Lime thumb core
                drawCircle(
                    color = Color(0xFFCCFF00),
                    radius = with(density) { 12.dp.toPx() },
                    center = touchCurrent
                )
            }
        }
    }
}

/**
 * EXACT SVG Implementation of fh6_pedal_accel.svg
 * ViewBox: 0 0 160 400
 * Inner fill is strictly clipped inside the pedal contour so it never spills out!
 */
@Composable
fun FH6ExactSvgAccelPedal(
    isEditMode: Boolean,
    pressure: Float,
    onPressureChange: (Float) -> Unit
) {
    val animatedPressure by animateFloatAsState(
        targetValue = pressure,
        animationSpec = tween(durationMillis = 20),
        label = "AccelPressure"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isEditMode) {
                if (!isEditMode) {
                    awaitPointerEventScope {
                        var trackedId: androidx.compose.ui.input.pointer.PointerId? = null
                        while (true) {
                            val event = awaitPointerEvent()
                            if (trackedId == null) {
                                val down = event.changes.firstOrNull { it.pressed && !it.isConsumed }
                                if (down != null) {
                                    trackedId = down.id
                                    down.consume()
                                    val raw = (1.0f - (down.position.y / size.height)).coerceIn(0.0f, 1.0f)
                                    onPressureChange(raw)
                                }
                            } else {
                                val change = event.changes.firstOrNull { it.id == trackedId }
                                if (change != null) {
                                    if (change.pressed) {
                                        change.consume()
                                        val raw = (1.0f - (change.position.y / size.height)).coerceIn(0.0f, 1.0f)
                                        onPressureChange(raw)
                                    } else {
                                        change.consume()
                                        trackedId = null
                                        onPressureChange(0f)
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        val sx = size.width / 160f
        val sy = size.height / 400f

        // Exact SVG Beveled Outer Contour Path
        val outerContour = Path().apply {
            moveTo(38f * sx, 14f * sy)
            lineTo(122f * sx, 14f * sy)
            quadraticBezierTo(148f * sx, 14f * sy, 148f * sx, 38f * sy)
            lineTo(148f * sx, 358f * sy)
            quadraticBezierTo(148f * sx, 386f * sy, 120f * sx, 386f * sy)
            lineTo(40f * sx, 386f * sy)
            quadraticBezierTo(12f * sx, 386f * sy, 12f * sx, 358f * sy)
            lineTo(12f * sx, 38f * sy)
            quadraticBezierTo(12f * sx, 14f * sy, 38f * sx, 14f * sy)
            close()
        }

        // 1. CLIPPED DYNAMIC FILL (Electric Cyan / White fluid rise from bottom to top)
        if (animatedPressure > 0.005f) {
            clipPath(outerContour) {
                val fillTop = size.height * (1.0f - animatedPressure)
                drawRect(
                    color = Color(0x3500F0FF),
                    topLeft = Offset(0f, fillTop),
                    size = Size(size.width, size.height * animatedPressure)
                )
            }
        }

        // 2. Outer Beveled Stroke (Thin crisp white outline)
        drawPath(outerContour, Color.White, style = Stroke(width = 1.8f * sx))

        // 3. Inset Secondary Precision Hairline Outline (dashed: 14 4)
        val insetContour = Path().apply {
            moveTo(40f * sx, 22f * sy)
            lineTo(120f * sx, 22f * sy)
            quadraticBezierTo(140f * sx, 22f * sy, 140f * sx, 40f * sy)
            lineTo(140f * sx, 352f * sy)
            quadraticBezierTo(140f * sx, 378f * sy, 116f * sx, 378f * sy)
            lineTo(44f * sx, 378f * sy)
            quadraticBezierTo(20f * sx, 378f * sy, 20f * sx, 352f * sy)
            lineTo(20f * sx, 40f * sy)
            quadraticBezierTo(20f * sx, 22f * sx, 40f * sx, 22f * sy)
            close()
        }
        drawPath(
            insetContour,
            Color(0x59FFFFFF),
            style = Stroke(
                width = 0.8f * sx,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f * sy, 4f * sy), 0f)
            )
        )

        // 4. Vertical CNC Motorsport Slits
        drawLine(Color(0x73FFFFFF), Offset(50f * sx, 80f * sy), Offset(50f * sx, 240f * sy), strokeWidth = 2f * sx, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(Color.White, Offset(80f * sx, 60f * sy), Offset(80f * sx, 260f * sy), strokeWidth = 2.5f * sx, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(Color(0x73FFFFFF), Offset(110f * sx, 80f * sy), Offset(110f * sx, 240f * sy), strokeWidth = 2f * sx, cap = androidx.compose.ui.graphics.StrokeCap.Round)

        // 5. Left Side Calibration Ticks
        drawLine(Color(0x80FFFFFF), Offset(12f * sx, 100f * sy), Offset(22f * sx, 100f * sy), strokeWidth = 1.2f * sx)
        drawLine(Color.White, Offset(12f * sx, 180f * sy), Offset(26f * sx, 180f * sy), strokeWidth = 1.8f * sx)
        drawLine(Color(0x80FFFFFF), Offset(12f * sx, 260f * sy), Offset(22f * sx, 260f * sy), strokeWidth = 1.2f * sx)
    }

    // Minimalist Typography Labels
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(
            text = "${(pressure * 100).toInt()}%",
            color = Color(0xFF00F0FF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "ACCEL",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp
        )
        Text(
            text = "RT",
            color = Color(0xD9FFFFFF),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
    }
}

/**
 * EXACT SVG Implementation of fh6_pedal_brake.svg
 * ViewBox: 0 0 190 360
 * Red slider fills from TOP to BOTTOM as requested!
 * Strictly clipped inside the pedal contour so it never spills out!
 */
@Composable
fun FH6ExactSvgBrakePedal(
    isEditMode: Boolean,
    pressure: Float,
    onPressureChange: (Float) -> Unit
) {
    val animatedPressure by animateFloatAsState(
        targetValue = pressure,
        animationSpec = tween(durationMillis = 60),
        label = "BrakePressure"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isEditMode) {
                if (!isEditMode) {
                    awaitPointerEventScope {
                        var trackedId: androidx.compose.ui.input.pointer.PointerId? = null
                        while (true) {
                            val event = awaitPointerEvent()
                            if (trackedId == null) {
                                val down = event.changes.firstOrNull { it.pressed && !it.isConsumed }
                                if (down != null) {
                                    trackedId = down.id
                                    down.consume()
                                    onPressureChange(1.0f)
                                }
                            } else {
                                val change = event.changes.firstOrNull { it.id == trackedId }
                                if (change != null) {
                                    if (change.pressed) {
                                        change.consume()
                                        onPressureChange(1.0f)
                                    } else {
                                        change.consume()
                                        trackedId = null
                                        onPressureChange(0f)
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        val sx = size.width / 190f
        val sy = size.height / 360f

        // Exact SVG Wide Brake Contour Path
        val outerContour = Path().apply {
            moveTo(36f * sx, 14f * sy)
            lineTo(154f * sx, 14f * sy)
            quadraticBezierTo(176f * sx, 14f * sy, 176f * sx, 36f * sy)
            lineTo(176f * sx, 324f * sy)
            quadraticBezierTo(176f * sx, 346f * sy, 154f * sx, 346f * sy)
            lineTo(36f * sx, 346f * sy)
            quadraticBezierTo(14f * sx, 346f * sy, 14f * sx, 324f * sy)
            lineTo(14f * sx, 36f * sy)
            quadraticBezierTo(14f * sx, 14f * sy, 36f * sx, 14f * sy)
            close()
        }

        // 1. CLIPPED DYNAMIC RED FILL (Fills from TOP to BOTTOM as requested!)
        if (animatedPressure > 0.005f) {
            clipPath(outerContour) {
                val fillHeight = size.height * animatedPressure
                drawRect(
                    color = Color(0x35FF0055),
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, fillHeight)
                )
            }
        }

        // 2. Outer Contour Stroke
        drawPath(outerContour, Color.White, style = Stroke(width = 2.2f * sx))

        // 3. Inset Secondary Precision Outline with Chamfer Accents
        val insetContour = Path().apply {
            moveTo(38f * sx, 22f * sy)
            lineTo(152f * sx, 22f * sy)
            quadraticBezierTo(166f * sx, 22f * sy, 166f * sx, 38f * sy)
            lineTo(166f * sx, 322f * sy)
            quadraticBezierTo(166f * sx, 338f * sy, 152f * sx, 338f * sy)
            lineTo(38f * sx, 338f * sy)
            quadraticBezierTo(22f * sx, 338f * sy, 22f * sx, 322f * sy)
            lineTo(22f * sx, 36f * sy)
            quadraticBezierTo(22f * sx, 22f * sy, 38f * sx, 22f * sy)
            close()
        }
        drawPath(insetContour, Color(0x59FFFFFF), style = Stroke(width = 1.0f * sx))

        // 4. 6 Rows of Horizontal Dual-Block Grip Plates
        val rows = listOf(56f, 84f, 112f, 140f, 168f, 196f)
        rows.forEachIndexed { i, yVal ->
            val alpha = if (i % 2 == 0) 1.0f else 0.55f
            // Left block (x=36, w=54, h=6.5, rx=3)
            val leftBlock = Path().apply {
                addRoundRect(RoundRect(Rect(36f * sx, yVal * sy, (36f + 54f) * sx, (yVal + 6.5f) * sy), CornerRadius(3f * sx, 3f * sy)))
            }
            drawPath(leftBlock, Color.White.copy(alpha = alpha), style = Stroke(width = 1.2f * sx))

            // Right block (x=100, w=54, h=6.5, rx=3)
            val rightBlock = Path().apply {
                addRoundRect(RoundRect(Rect(100f * sx, yVal * sy, (100f + 54f) * sx, (yVal + 6.5f) * sy), CornerRadius(3f * sx, 3f * sy)))
            }
            drawPath(rightBlock, Color.White.copy(alpha = alpha), style = Stroke(width = 1.2f * sx))
        }

        // 5. Right Side Gradation Scale
        drawLine(Color(0x8CFFFFFF), Offset(166f * sx, 68f * sy), Offset(176f * sx, 68f * sy), strokeWidth = 1.2f * sx)
        drawLine(Color.White, Offset(162f * sx, 126f * sy), Offset(176f * sx, 126f * sy), strokeWidth = 2f * sx)
        drawLine(Color(0x8CFFFFFF), Offset(166f * sx, 184f * sy), Offset(176f * sx, 184f * sy), strokeWidth = 1.2f * sx)
    }

    // Clean Bold Typography
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(
            text = "${(pressure * 100).toInt()}%",
            color = Color(0xFFFF0055),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "BRAKE",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp
        )
        Text(
            text = "LT / REVERSE",
            color = Color(0xD9FFFFFF),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
    }
}

/**
 * EXACT SVG Implementation of fh6_handbrake.svg
 * ViewBox: 0 0 340 120
 */
@Composable
fun FH6ExactSvgHandbrake(
    label: String,
    subLabel: String,
    isEditMode: Boolean,
    isPressed: Boolean,
    onPressedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isEditMode) {
                if (!isEditMode) {
                    awaitPointerEventScope {
                        var trackedId: androidx.compose.ui.input.pointer.PointerId? = null
                        while (true) {
                            val event = awaitPointerEvent()
                            if (trackedId == null) {
                                val down = event.changes.firstOrNull { it.pressed && !it.isConsumed }
                                if (down != null) {
                                    trackedId = down.id
                                    down.consume()
                                    onPressedChange(true)
                                }
                            } else {
                                val change = event.changes.firstOrNull { it.id == trackedId }
                                if (change != null) {
                                    if (change.pressed) {
                                        change.consume()
                                    } else {
                                        change.consume()
                                        trackedId = null
                                        onPressedChange(false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sx = size.width / 340f
            val sy = size.height / 120f

            // 1. Sleek Outer HUD Capsule Outline (x=6, y=6, w=328, h=108, rx=20)
            val outerCapsule = RoundRect(
                Rect(6f * sx, 6f * sy, (6f + 328f) * sx, (6f + 108f) * sy),
                CornerRadius(20f * sx, 20f * sy)
            )

            if (isPressed) {
                // Flash fill when pulled
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(6f * sx, 6f * sy),
                    size = Size(328f * sx, 108f * sy),
                    cornerRadius = CornerRadius(20f * sx, 20f * sy)
                )
            } else {
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(6f * sx, 6f * sy),
                    size = Size(328f * sx, 108f * sy),
                    cornerRadius = CornerRadius(20f * sx, 20f * sy),
                    style = Stroke(width = 1.8f * sx)
                )

                // 2. Inset Capsule (x=12, y=12, w=316, h=96, rx=16)
                drawRoundRect(
                    color = Color(0x40FFFFFF),
                    topLeft = Offset(12f * sx, 12f * sy),
                    size = Size(316f * sx, 96f * sy),
                    cornerRadius = CornerRadius(16f * sx, 16f * sy),
                    style = Stroke(width = 0.8f * sx)
                )

                // 3. Left & Right Accent Brackets (M 30 35 L 22 60 L 30 85)
                val leftBracket = Path().apply {
                    moveTo(30f * sx, 35f * sy)
                    lineTo(22f * sx, 60f * sy)
                    lineTo(30f * sx, 85f * sy)
                }
                drawPath(leftBracket, Color(0x99FFFFFF), style = Stroke(width = 2f * sx, cap = androidx.compose.ui.graphics.StrokeCap.Round))

                val rightBracket = Path().apply {
                    moveTo(310f * sx, 35f * sy)
                    lineTo(318f * sx, 60f * sy)
                    lineTo(310f * sx, 85f * sy)
                }
                drawPath(rightBracket, Color(0x99FFFFFF), style = Stroke(width = 2f * sx, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
        }

        // Center Typography
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                color = if (isPressed) Color(0xFF080B10) else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subLabel,
                color = if (isPressed) Color(0x99000000) else Color(0xB3FFFFFF),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )
        }
    }
}

/**
 * Clean Motorsport Action Button with Custom SVG Vector Art
 */
@Composable
fun FH6ExactSvgActionButton(
    actionId: String,
    label: String,
    subLabel: String,
    isEditMode: Boolean,
    isPressed: Boolean,
    onPressedChange: (Boolean) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isEditMode) {
                if (!isEditMode) {
                    awaitPointerEventScope {
                        var trackedId: androidx.compose.ui.input.pointer.PointerId? = null
                        while (true) {
                            val event = awaitPointerEvent()
                            if (trackedId == null) {
                                val down = event.changes.firstOrNull { it.pressed && !it.isConsumed }
                                if (down != null) {
                                    trackedId = down.id
                                    down.consume()
                                    onPressedChange(true)
                                }
                            } else {
                                val change = event.changes.firstOrNull { it.id == trackedId }
                                if (change != null) {
                                    if (change.pressed) {
                                        change.consume()
                                    } else {
                                        change.consume()
                                        trackedId = null
                                        onPressedChange(false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        val w = maxWidth
        val h = maxHeight
        val minDim = minOf(w, h)
        val density = androidx.compose.ui.platform.LocalDensity.current
        val minDimPx = with(density) { minDim.toPx() }

        // Scalable Proportional Dimensions (behaving strictly like a scalable vector graphic)
        val strokeWidth = (minDimPx * 0.038f).coerceIn(1.2f, 4.5f)
        val thinStroke = (minDimPx * 0.02f).coerceIn(0.8f, 2.5f)
        val cornerRad = (minDimPx * 0.16f).coerceIn(4f, 22f)

        // Scalable Proportional Typography
        val isSingleLetter = when (actionId) {
            "btn_a", "btn_b", "btn_x", "activate", "rewind", "btn_y" -> true
            else -> false
        }
        val mainFontSize = (minDim.value * (if (isSingleLetter) 0.42f else 0.14f)).coerceIn(5f, 32f).sp
        val subFontSize = (minDim.value * 0.085f).coerceIn(4f, 13f).sp
        val letterSpacingVal = (minDim.value * 0.01f).coerceIn(0.2f, 1.5f).sp

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = (minOf(size.width, size.height) / 2f) * 0.88f

            when (actionId) {
                "steer", "move_stick" -> {
                    // Miniature Motorsport 3-Spoke D-Cut Steering Wheel
                    val voltLime = Color(0xFFCCFF00)
                    val wheelCol = if (isPressed) voltLime else Color.White
                    val rimBg = if (isPressed) voltLime else Color(0xE006121A)
                    drawCircle(rimBg, radius = r * 0.76f, center = Offset(cx, cy))
                    drawCircle(wheelCol, radius = r * 0.76f, center = Offset(cx, cy), style = Stroke(width = strokeWidth * 1.3f))
                    drawCircle(Color(0xFF0F1722), radius = r * 0.26f, center = Offset(cx, cy))
                    drawCircle(wheelCol, radius = r * 0.26f, center = Offset(cx, cy), style = Stroke(width = thinStroke))
                    drawLine(wheelCol, Offset(cx - r * 0.72f, cy), Offset(cx - r * 0.26f, cy), strokeWidth = strokeWidth)
                    drawLine(wheelCol, Offset(cx + r * 0.26f, cy), Offset(cx + r * 0.72f, cy), strokeWidth = strokeWidth)
                    drawLine(wheelCol, Offset(cx, cy + r * 0.26f), Offset(cx, cy + r * 0.72f), strokeWidth = strokeWidth)
                    drawArc(voltLime, -100f, 20f, false, topLeft = Offset(cx - r * 0.76f, cy - r * 0.76f), size = Size(r * 1.52f, r * 1.52f), style = Stroke(width = strokeWidth * 2.2f))
                }

                "throttle", "fire_weapon" -> {
                    val neonGreen = Color(0xFF00E676)
                    if (isPressed) {
                        drawRoundRect(neonGreen, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    } else {
                        drawRoundRect(Color(0xE0061A12), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(neonGreen, cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                    val col = if (isPressed) Color.Black else neonGreen
                    val pedalRect = Rect(cx - r * 0.32f, cy - r * 0.60f, cx + r * 0.32f, cy + r * 0.40f)
                    drawRoundRect(col, pedalRect.topLeft, pedalRect.size, CornerRadius(4.dp.toPx(), 4.dp.toPx()), style = Stroke(width = strokeWidth))
                    drawLine(col, Offset(cx - r * 0.16f, cy - r * 0.45f), Offset(cx - r * 0.16f, cy + r * 0.25f), strokeWidth = thinStroke * 1.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(col, Offset(cx, cy - r * 0.45f), Offset(cx, cy + r * 0.25f), strokeWidth = thinStroke * 1.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(col, Offset(cx + r * 0.16f, cy - r * 0.45f), Offset(cx + r * 0.16f, cy + r * 0.25f), strokeWidth = thinStroke * 1.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }

                "brake", "aim_weapon" -> {
                    val raceRed = Color(0xFFFF1744)
                    if (isPressed) {
                        drawRoundRect(raceRed, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    } else {
                        drawRoundRect(Color(0xE01A060A), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(raceRed, cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                    val col = if (isPressed) Color.Black else raceRed
                    val brakeRect = Rect(cx - r * 0.48f, cy - r * 0.52f, cx + r * 0.48f, cy + r * 0.35f)
                    drawRoundRect(col, brakeRect.topLeft, brakeRect.size, CornerRadius(4.dp.toPx(), 4.dp.toPx()), style = Stroke(width = strokeWidth))
                    drawLine(col, Offset(cx - r * 0.35f, cy - r * 0.28f), Offset(cx + r * 0.35f, cy - r * 0.28f), strokeWidth = thinStroke * 1.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(col, Offset(cx - r * 0.35f, cy - r * 0.08f), Offset(cx + r * 0.35f, cy - r * 0.08f), strokeWidth = thinStroke * 1.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(col, Offset(cx - r * 0.35f, cy + r * 0.12f), Offset(cx + r * 0.35f, cy + r * 0.12f), strokeWidth = thinStroke * 1.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }

                "handbrake" -> {
                    val amber = Color(0xFFFFB300)
                    if (isPressed) {
                        drawRoundRect(amber, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    } else {
                        drawRoundRect(Color(0xE01A1406), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(amber, cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                    val col = if (isPressed) Color.Black else amber
                    drawCircle(col, radius = r * 0.16f, center = Offset(cx - r * 0.26f, cy + r * 0.28f), style = Stroke(width = strokeWidth))
                    drawLine(col, Offset(cx - r * 0.26f, cy + r * 0.28f), Offset(cx + r * 0.26f, cy - r * 0.42f), strokeWidth = strokeWidth * 1.4f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(col, Offset(cx + r * 0.06f, cy - r * 0.18f), Offset(cx + r * 0.26f, cy - r * 0.42f), strokeWidth = strokeWidth * 2.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawCircle(col, radius = r * 0.06f, center = Offset(cx + r * 0.30f, cy - r * 0.46f))
                }

                "shift_up" -> {
                    val red = Color(0xFFFF1744)
                    if (isPressed) {
                        drawRoundRect(red, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    } else {
                        drawRoundRect(Color(0xE01A060A), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(red, cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                    val col = if (isPressed) Color.Black else red
                    drawLine(col, Offset(cx - r * 0.26f, cy - r * 0.35f), Offset(cx + r * 0.26f, cy - r * 0.35f), strokeWidth = strokeWidth * 1.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(col, Offset(cx, cy - r * 0.60f), Offset(cx, cy - r * 0.10f), strokeWidth = strokeWidth * 1.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    val chevron = Path().apply {
                        moveTo(cx - r * 0.26f, cy + r * 0.15f)
                        lineTo(cx, cy)
                        lineTo(cx + r * 0.26f, cy + r * 0.15f)
                        moveTo(cx - r * 0.26f, cy + r * 0.32f)
                        lineTo(cx, cy + r * 0.17f)
                        lineTo(cx + r * 0.26f, cy + r * 0.32f)
                    }
                    drawPath(chevron, col, style = Stroke(width = strokeWidth * 0.9f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }

                "shift_down" -> {
                    val blue = Color(0xFFCCFF00)
                    if (isPressed) {
                        drawRoundRect(blue, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    } else {
                        drawRoundRect(Color(0xE006121A), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(blue, cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                    val col = if (isPressed) Color.Black else blue
                    drawLine(col, Offset(cx - r * 0.26f, cy - r * 0.35f), Offset(cx + r * 0.26f, cy - r * 0.35f), strokeWidth = strokeWidth * 1.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    val chevron = Path().apply {
                        moveTo(cx - r * 0.26f, cy + r * 0.05f)
                        lineTo(cx, cy + r * 0.20f)
                        lineTo(cx + r * 0.26f, cy + r * 0.05f)
                        moveTo(cx - r * 0.26f, cy + r * 0.22f)
                        lineTo(cx, cy + r * 0.37f)
                        lineTo(cx + r * 0.26f, cy + r * 0.22f)
                    }
                    drawPath(chevron, col, style = Stroke(width = strokeWidth * 0.9f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }

                "look_left" -> {
                    val cyan = Color(0xFF00E5FF)
                    if (isPressed) drawRoundRect(cyan, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    else {
                        drawRoundRect(Color(0xE0051720), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(cyan, cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                    val col = if (isPressed) Color.Black else cyan
                    val arrow = Path().apply {
                        moveTo(cx - r * 0.32f, cy - r * 0.10f)
                        lineTo(cx + r * 0.06f, cy - r * 0.36f)
                        lineTo(cx + r * 0.06f, cy + r * 0.16f)
                        close()
                    }
                    drawPath(arrow, col)
                }

                "look_right" -> {
                    val cyan = Color(0xFF00E5FF)
                    if (isPressed) drawRoundRect(cyan, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    else {
                        drawRoundRect(Color(0xE0051720), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(cyan, cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                    val col = if (isPressed) Color.Black else cyan
                    val arrow = Path().apply {
                        moveTo(cx + r * 0.32f, cy - r * 0.10f)
                        lineTo(cx - r * 0.06f, cy - r * 0.36f)
                        lineTo(cx - r * 0.06f, cy + r * 0.16f)
                        close()
                    }
                    drawPath(arrow, col)
                }

                "look_forward" -> {
                    val cyan = Color(0xFF00E5FF)
                    if (isPressed) drawRoundRect(cyan, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    else {
                        drawRoundRect(Color(0xE0051720), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(cyan, cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                    val col = if (isPressed) Color.Black else cyan
                    val arrow = Path().apply {
                        moveTo(cx, cy - r * 0.40f)
                        lineTo(cx + r * 0.28f, cy - r * 0.05f)
                        lineTo(cx - r * 0.28f, cy - r * 0.05f)
                        close()
                    }
                    drawPath(arrow, col)
                }

                "radio_prev" -> {
                    val pink = Color(0xFFFF4081)
                    if (isPressed) drawRoundRect(pink, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    else {
                        drawRoundRect(Color(0xE0200612), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(pink, cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                    val col = if (isPressed) Color.Black else pink
                    val t1 = Path().apply {
                        moveTo(cx + r * 0.25f, cy - r * 0.25f)
                        lineTo(cx, cy - r * 0.05f)
                        lineTo(cx + r * 0.25f, cy + r * 0.15f)
                        close()
                    }
                    val t2 = Path().apply {
                        moveTo(cx, cy - r * 0.25f)
                        lineTo(cx - r * 0.25f, cy - r * 0.05f)
                        lineTo(cx + r * 0.15f, cy)
                        close()
                    }
                    drawPath(t1, col)
                    drawPath(t2, col)
                }

                "aim_stick" -> {
                    val voltLime = Color(0xFFCCFF00)
                    val col = if (isPressed) Color.Black else voltLime
                    if (isPressed) drawRoundRect(voltLime, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    else {
                        drawRoundRect(Color(0xE0081418), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(voltLime, cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                    drawCircle(col, radius = r * 0.35f, center = Offset(cx, cy - r * 0.05f), style = Stroke(width = strokeWidth))
                    drawCircle(col, radius = r * 0.10f, center = Offset(cx, cy - r * 0.05f))
                }

                "auto_drive" -> {
                    // Autonomous Speedometer Arc & AI Guidance Vector
                    val voltLime = Color(0xFFCCFF00)
                    if (isPressed) {
                        drawRoundRect(
                            color = voltLime,
                            cornerRadius = CornerRadius(cornerRad, cornerRad)
                        )
                    } else {
                        drawRoundRect(
                            color = Color(0xF2041A18),
                            cornerRadius = CornerRadius(cornerRad, cornerRad)
                        )
                        drawRoundRect(
                            color = voltLime,
                            cornerRadius = CornerRadius(cornerRad, cornerRad),
                            style = Stroke(width = strokeWidth)
                        )
                    }

                    val arcColor = if (isPressed) Color.Black else voltLime
                    val arcRect = Rect(cx - r * 0.72f, cy - r * 0.72f, cx + r * 0.72f, cy + r * 0.72f)
                    drawArc(
                        color = arcColor,
                        startAngle = 145f,
                        sweepAngle = 250f,
                        useCenter = false,
                        topLeft = arcRect.topLeft,
                        size = arcRect.size,
                        style = Stroke(width = strokeWidth * 1.3f)
                    )

                    // Forward AI Guidance Triangle
                    val arrowPath = Path().apply {
                        moveTo(cx, cy - r * 0.45f)
                        lineTo(cx + r * 0.22f, cy - r * 0.10f)
                        lineTo(cx, cy - r * 0.20f)
                        lineTo(cx - r * 0.22f, cy - r * 0.10f)
                        close()
                    }
                    drawPath(arrowPath, arcColor)
                }

                "btn_a" -> {
                    val green = Color(0xFF00E676)
                    if (isPressed) {
                        drawCircle(green, radius = r, center = Offset(cx, cy))
                    } else {
                        drawCircle(Color(0xE0061A12), radius = r, center = Offset(cx, cy))
                        drawCircle(green, radius = r, center = Offset(cx, cy), style = Stroke(width = strokeWidth))
                        drawCircle(Color(0x6000E676), radius = r * 0.78f, center = Offset(cx, cy), style = Stroke(width = thinStroke))
                    }
                }

                "btn_b" -> {
                    val red = Color(0xFFFF1744)
                    if (isPressed) {
                        drawCircle(red, radius = r, center = Offset(cx, cy))
                    } else {
                        drawCircle(Color(0xE01A060A), radius = r, center = Offset(cx, cy))
                        drawCircle(red, radius = r, center = Offset(cx, cy), style = Stroke(width = strokeWidth))
                        drawCircle(Color(0x60FF1744), radius = r * 0.78f, center = Offset(cx, cy), style = Stroke(width = thinStroke))
                    }
                    // Paddle Shift Up (+) Top Accent
                    val plusColor = if (isPressed) Color.Black else red
                    drawLine(plusColor, Offset(cx - r * 0.22f, cy - r * 0.50f), Offset(cx + r * 0.22f, cy - r * 0.50f), strokeWidth = strokeWidth * 1.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(plusColor, Offset(cx, cy - r * 0.72f), Offset(cx, cy - r * 0.28f), strokeWidth = strokeWidth * 1.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }

                "btn_x", "activate" -> {
                    val blue = Color(0xFFCCFF00)
                    if (isPressed) {
                        drawCircle(blue, radius = r, center = Offset(cx, cy))
                    } else {
                        drawCircle(Color(0xE006121A), radius = r, center = Offset(cx, cy))
                        drawCircle(blue, radius = r, center = Offset(cx, cy), style = Stroke(width = strokeWidth))
                        drawCircle(Color(0x6000B0FF), radius = r * 0.78f, center = Offset(cx, cy), style = Stroke(width = thinStroke))
                    }
                    // Paddle Shift Down (−) Top Accent
                    val minusColor = if (isPressed) Color.Black else blue
                    drawLine(minusColor, Offset(cx - r * 0.24f, cy - r * 0.50f), Offset(cx + r * 0.24f, cy - r * 0.50f), strokeWidth = strokeWidth * 1.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }

                "rewind", "btn_y" -> {
                    val yellow = Color(0xFFCCFF00)
                    if (isPressed) {
                        drawCircle(yellow, radius = r, center = Offset(cx, cy))
                    } else {
                        drawCircle(Color(0xE0161A06), radius = r, center = Offset(cx, cy))
                        drawCircle(yellow, radius = r, center = Offset(cx, cy), style = Stroke(width = strokeWidth))
                        drawCircle(Color(0x60CCFF00), radius = r * 0.78f, center = Offset(cx, cy), style = Stroke(width = thinStroke))
                    }
                    // Signature Forza Horizon Counter-Clockwise Rewind Arc
                    val arcColor = if (isPressed) Color.Black else yellow
                    val rewindRect = Rect(cx - r * 0.55f, cy - r * 0.55f, cx + r * 0.55f, cy + r * 0.55f)
                    drawArc(
                        color = arcColor,
                        startAngle = 210f,
                        sweepAngle = 230f,
                        useCenter = false,
                        topLeft = rewindRect.topLeft,
                        size = rewindRect.size,
                        style = Stroke(width = strokeWidth * 1.1f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                    // Arrowhead pointing back counter-clockwise
                    val arrowHead = Path().apply {
                        moveTo(cx - r * 0.55f, cy - r * 0.10f)
                        lineTo(cx - r * 0.70f, cy - r * 0.35f)
                        lineTo(cx - r * 0.35f, cy - r * 0.30f)
                        close()
                    }
                    drawPath(arrowHead, arcColor)
                }

                "btn_lb", "clutch" -> {
                    // Precision Motorsport Clutch Pedal Plate with CNC Grip Slots
                    val cyan = Color(0xFF00E5FF)
                    if (isPressed) {
                        drawRoundRect(Color.White, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    } else {
                        drawRoundRect(Color(0xE0051720), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(cyan, cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                    val slotColor = if (isPressed) Color.Black else cyan
                    // 3 horizontal motorsport grip slits
                    drawLine(slotColor, Offset(cx - r * 0.38f, cy - r * 0.38f), Offset(cx + r * 0.38f, cy - r * 0.38f), strokeWidth = strokeWidth * 1.1f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(slotColor, Offset(cx - r * 0.38f, cy - r * 0.18f), Offset(cx + r * 0.38f, cy - r * 0.18f), strokeWidth = strokeWidth * 1.1f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(slotColor, Offset(cx - r * 0.38f, cy + r * 0.02f), Offset(cx + r * 0.38f, cy + r * 0.02f), strokeWidth = strokeWidth * 1.1f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }

                "camera", "btn_rb" -> {
                    val white = Color.White
                    if (isPressed) {
                        drawRoundRect(white, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    } else {
                        drawRoundRect(Color(0xE0081418), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(white, cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                    val strokeColor = if (isPressed) Color.Black else Color.White
                    // Camera body & top prism notch
                    val camBody = Path().apply {
                        moveTo(cx - r * 0.42f, cy - r * 0.15f)
                        lineTo(cx - r * 0.22f, cy - r * 0.15f)
                        lineTo(cx - r * 0.14f, cy - r * 0.32f)
                        lineTo(cx + r * 0.14f, cy - r * 0.32f)
                        lineTo(cx + r * 0.22f, cy - r * 0.15f)
                        lineTo(cx + r * 0.42f, cy - r * 0.15f)
                        lineTo(cx + r * 0.42f, cy + r * 0.20f)
                        lineTo(cx - r * 0.42f, cy + r * 0.20f)
                        close()
                    }
                    drawPath(camBody, strokeColor, style = Stroke(width = strokeWidth * 0.9f))
                    drawCircle(strokeColor, radius = r * 0.14f, center = Offset(cx, cy + r * 0.02f), style = Stroke(width = strokeWidth * 0.9f))
                }

                "horn", "btn_rs" -> {
                    // Car Horn / Klaxon Trumpet with Dual Acoustic Waves
                    val gold = Color(0xFFFFD600)
                    if (isPressed) {
                        drawRoundRect(gold, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    } else {
                        drawRoundRect(Color(0xE01C1906), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(gold, cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                    val hornColor = if (isPressed) Color.Black else gold
                    // Horn body
                    val hornPath = Path().apply {
                        moveTo(cx - r * 0.42f, cy - r * 0.08f)
                        lineTo(cx - r * 0.18f, cy - r * 0.08f)
                        lineTo(cx + r * 0.12f, cy - r * 0.32f)
                        lineTo(cx + r * 0.12f, cy + r * 0.16f)
                        lineTo(cx - r * 0.18f, cy - r * 0.02f)
                        lineTo(cx - r * 0.42f, cy - r * 0.02f)
                        close()
                    }
                    drawPath(hornPath, hornColor)
                    // Sound waves
                    val waveRect1 = Rect(cx - r * 0.05f, cy - r * 0.32f, cx + r * 0.38f, cy + r * 0.16f)
                    drawArc(hornColor, -45f, 90f, false, waveRect1.topLeft, waveRect1.size, style = Stroke(width = strokeWidth * 0.9f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    val waveRect2 = Rect(cx + r * 0.12f, cy - r * 0.42f, cx + r * 0.55f, cy + r * 0.26f)
                    drawArc(hornColor, -45f, 90f, false, waveRect2.topLeft, waveRect2.size, style = Stroke(width = strokeWidth * 0.9f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }

                "look_back" -> {
                    // Rearview Mirror Icon (RS Down)
                    val voltLime = Color(0xFFCCFF00)
                    if (isPressed) {
                        drawRoundRect(voltLime, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    } else {
                        drawRoundRect(Color(0xE0081418), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(voltLime, cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                    val strokeColor = if (isPressed) Color.Black else voltLime
                    // Mirror outline
                    val mirrorRect = Rect(cx - r * 0.48f, cy - r * 0.32f, cx + r * 0.48f, cy + r * 0.08f)
                    drawRoundRect(strokeColor, mirrorRect.topLeft, mirrorRect.size, CornerRadius(6.dp.toPx(), 6.dp.toPx()), style = Stroke(width = strokeWidth * 0.9f))
                    // Mirror mount stem
                    drawLine(strokeColor, Offset(cx, cy - r * 0.32f), Offset(cx, cy - r * 0.50f), strokeWidth = strokeWidth * 0.9f)
                    // Perspective road lines inside mirror
                    drawLine(strokeColor.copy(alpha = 0.6f), Offset(cx - r * 0.25f, cy + r * 0.08f), Offset(cx - r * 0.05f, cy - r * 0.12f), strokeWidth = thinStroke)
                    drawLine(strokeColor.copy(alpha = 0.6f), Offset(cx + r * 0.25f, cy + r * 0.08f), Offset(cx + r * 0.05f, cy - r * 0.12f), strokeWidth = thinStroke)
                }

                "convertible", "btn_ls" -> {
                    // Convertible Roof Icon
                    val cyan = Color(0xFF00E5FF)
                    if (isPressed) {
                        drawRoundRect(cyan, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    } else {
                        drawRoundRect(Color(0xE0051720), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(cyan, cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                    val roofColor = if (isPressed) Color.Black else cyan
                    val roofPath = Path().apply {
                        moveTo(cx - r * 0.40f, cy + r * 0.08f)
                        lineTo(cx - r * 0.20f, cy - r * 0.25f)
                        lineTo(cx + r * 0.20f, cy - r * 0.25f)
                        lineTo(cx + r * 0.40f, cy + r * 0.08f)
                    }
                    drawPath(roofPath, roofColor, style = Stroke(width = strokeWidth * 1.1f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }

                "photo_mode" -> {
                    // Photo Mode Aperture
                    val purple = Color(0xFFE040FB)
                    if (isPressed) {
                        drawRoundRect(purple, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    } else {
                        drawRoundRect(Color(0xE01A0620), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(purple, cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                    val col = if (isPressed) Color.Black else purple
                    drawCircle(col, radius = r * 0.28f, center = Offset(cx, cy - r * 0.05f), style = Stroke(width = strokeWidth * 0.9f))
                    drawCircle(col, radius = r * 0.10f, center = Offset(cx, cy - r * 0.05f))
                }

                "radio_next" -> {
                    // Radio Track Skip
                    val pink = Color(0xFFFF4081)
                    if (isPressed) {
                        drawRoundRect(pink, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    } else {
                        drawRoundRect(Color(0xE0200612), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(pink, cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                    val col = if (isPressed) Color.Black else pink
                    val t1 = Path().apply {
                        moveTo(cx - r * 0.25f, cy - r * 0.25f)
                        lineTo(cx, cy - r * 0.05f)
                        lineTo(cx - r * 0.25f, cy + r * 0.15f)
                        close()
                    }
                    val t2 = Path().apply {
                        moveTo(cx, cy - r * 0.25f)
                        lineTo(cx + r * 0.25f, cy - r * 0.05f)
                        lineTo(cx, cy + r * 0.15f)
                        close()
                    }
                    drawPath(t1, col)
                    drawPath(t2, col)
                }



                "map", "btn_back", "btn_view" -> {
                    val white = Color.White
                    if (isPressed) {
                        drawRoundRect(white, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    } else {
                        drawRoundRect(Color(0xE0081418), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(white, cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                    val strokeColor = if (isPressed) Color.Black else Color.White
                    val mapPath = Path().apply {
                        moveTo(cx - r * 0.45f, cy - r * 0.3f)
                        lineTo(cx - r * 0.15f, cy - r * 0.45f)
                        lineTo(cx + r * 0.15f, cy - r * 0.3f)
                        lineTo(cx + r * 0.45f, cy - r * 0.45f)
                        lineTo(cx + r * 0.45f, cy + r * 0.05f)
                        lineTo(cx + r * 0.15f, cy + r * 0.2f)
                        lineTo(cx - r * 0.15f, cy + r * 0.05f)
                        lineTo(cx - r * 0.45f, cy + r * 0.2f)
                        close()
                    }
                    drawPath(mapPath, strokeColor, style = Stroke(width = strokeWidth * 0.9f))
                }

                "pause", "btn_start", "btn_menu" -> {
                    val white = Color.White
                    if (isPressed) {
                        drawRoundRect(white, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    } else {
                        drawRoundRect(Color(0xE0081418), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(white, cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                    val barColor = if (isPressed) Color.Black else Color.White
                    drawRect(barColor, topLeft = Offset(cx - r * 0.24f, cy - r * 0.35f), size = Size(r * 0.15f, r * 0.45f))
                    drawRect(barColor, topLeft = Offset(cx + r * 0.09f, cy - r * 0.35f), size = Size(r * 0.15f, r * 0.45f))
                }

                "dpad_up" -> {
                    val white = Color.White
                    if (isPressed) {
                        drawRoundRect(white, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    } else {
                        drawRoundRect(Color(0xE0081418), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(white, cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                    val arrowColor = if (isPressed) Color.Black else Color.White
                    val arrow = Path().apply {
                        moveTo(cx, cy - r * 0.42f)
                        lineTo(cx + r * 0.32f, cy - r * 0.05f)
                        lineTo(cx - r * 0.32f, cy - r * 0.05f)
                        close()
                    }
                    drawPath(arrow, arrowColor)
                }

                "dpad_down" -> {
                    val white = Color.White
                    if (isPressed) {
                        drawRoundRect(white, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    } else {
                        drawRoundRect(Color(0xE0081418), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(white, cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                    val arrowColor = if (isPressed) Color.Black else Color.White
                    val arrow = Path().apply {
                        moveTo(cx, cy + r * 0.18f)
                        lineTo(cx + r * 0.32f, cy - r * 0.18f)
                        lineTo(cx - r * 0.32f, cy - r * 0.18f)
                        close()
                    }
                    drawPath(arrow, arrowColor)
                }

                "dpad_left" -> {
                    val white = Color.White
                    if (isPressed) {
                        drawRoundRect(white, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    } else {
                        drawRoundRect(Color(0xE0081418), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(white, cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                    val arrowColor = if (isPressed) Color.Black else Color.White
                    val arrow = Path().apply {
                        moveTo(cx - r * 0.32f, cy - r * 0.10f)
                        lineTo(cx + r * 0.06f, cy - r * 0.36f)
                        lineTo(cx + r * 0.06f, cy + r * 0.16f)
                        close()
                    }
                    drawPath(arrow, arrowColor)
                }

                "dpad_right" -> {
                    val white = Color.White
                    if (isPressed) {
                        drawRoundRect(white, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    } else {
                        drawRoundRect(Color(0xE0081418), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(white, cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                    val arrowColor = if (isPressed) Color.Black else Color.White
                    val arrow = Path().apply {
                        moveTo(cx + r * 0.32f, cy - r * 0.10f)
                        lineTo(cx - r * 0.06f, cy - r * 0.36f)
                        lineTo(cx - r * 0.06f, cy + r * 0.16f)
                        close()
                    }
                    drawPath(arrow, arrowColor)
                }

                else -> {
                    if (isPressed) {
                        drawRoundRect(Color.White, cornerRadius = CornerRadius(cornerRad, cornerRad))
                    } else {
                        drawRoundRect(Color(0xE0081418), cornerRadius = CornerRadius(cornerRad, cornerRad))
                        drawRoundRect(Color(0x80FFFFFF), cornerRadius = CornerRadius(cornerRad, cornerRad), style = Stroke(width = strokeWidth))
                    }
                }
            }
        }

        val textColor = if (isPressed) {
            Color(0xFF080B10)
        } else {
            when (actionId) {
                "auto_drive", "rewind", "btn_y", "steer", "move_stick" -> Color(0xFFCCFF00)
                "btn_a", "throttle", "fire_weapon" -> Color(0xFF00E676)
                "btn_b", "shift_up", "brake", "aim_weapon" -> Color(0xFFFF1744)
                "btn_x", "activate", "shift_down" -> Color(0xFFCCFF00)
                "btn_lb", "clutch", "convertible", "btn_ls", "look_left", "look_right", "look_forward" -> Color(0xFF00E5FF)
                "horn", "btn_rs", "handbrake" -> Color(0xFFFFD600)
                "look_back" -> Color(0xFFCCFF00)
                "photo_mode" -> Color(0xFFE040FB)
                "radio_next", "radio_prev" -> Color(0xFFFF4081)
                else -> Color.White
            }
        }

        if (isSingleLetter) {
            // Authentic Xbox Face Button (A, B, X, Y) centered directly in circle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(all = (minDim.value * 0.08f).dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (actionId) {
                        "btn_a" -> "A"
                        "btn_b" -> "B"
                        "btn_x", "activate" -> "X"
                        "rewind", "btn_y" -> "Y"
                        else -> label
                    },
                    color = textColor,
                    fontSize = mainFontSize,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            // Adaptive Icon + Text Layout (fits snug inside SVG bounds at any scale)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = (minDim.value * 0.04f).dp)
                    .padding(bottom = (minDim.value * 0.05f).coerceAtLeast(1f).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = when (actionId) {
                        "steer", "move_stick" -> if (label.isNotBlank()) label else "STEER"
                        "throttle", "fire_weapon" -> if (label.isNotBlank()) label else "GAS"
                        "brake", "aim_weapon" -> if (label.isNotBlank()) label else "BRAKE"
                        "handbrake" -> if (label.isNotBlank()) label else "DRIFT"
                        "auto_drive" -> "AUTO"
                        "btn_lb", "clutch" -> "CLUTCH"
                        "horn", "btn_rs" -> "HORN"
                        "look_back" -> "REAR"
                        "look_left" -> "LEFT"
                        "look_right" -> "RIGHT"
                        "look_forward" -> "FRONT"
                        "convertible", "btn_ls" -> "ROOF"
                        "photo_mode" -> "PHOTO"
                        "radio_next" -> "RADIO"
                        "radio_prev" -> "PREV"
                        "shift_up" -> "SHIFT +"
                        "shift_down" -> "SHIFT -"
                        "camera", "btn_rb" -> "CAM"
                        "map", "btn_back", "btn_view" -> "MAP"
                        "pause", "btn_start", "btn_menu" -> "MENU"
                        else -> label
                    },
                    color = textColor,
                    fontSize = mainFontSize,
                    fontWeight = FontWeight.Black,
                    letterSpacing = letterSpacingVal,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                if (subLabel.isNotEmpty() && minDim.value >= 36f) {
                    Text(
                        text = subLabel,
                        color = if (isPressed) Color(0x99000000) else Color(0xB3FFFFFF),
                        fontSize = subFontSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Miniature Scalable SVG Preview Box for the Action Catalog
 */
@Composable
fun FH6MiniActionPreview(actionId: String) {
    FH6ExactSvgActionButton(
        actionId = actionId,
        label = "",
        subLabel = "",
        isEditMode = true,
        isPressed = false,
        onPressedChange = {}
    )
}

/**
 * High-Precision 360° Analog Thumbstick for Character Movement and Camera Aiming
 */
@Composable
fun FH6AnalogJoystick(
    label: String,
    accentColor: Color = Color(0xFFCCFF00),
    isEditMode: Boolean = false,
    onValueChange: (x: Float, y: Float) -> Unit
) {
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    var baseRadiusPx by remember { mutableStateOf(100f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isEditMode) {
                if (!isEditMode) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val raw = offset - center
                            val dist = kotlin.math.sqrt(raw.x * raw.x + raw.y * raw.y)
                            val clamped = if (dist > baseRadiusPx) Offset(raw.x * (baseRadiusPx / dist), raw.y * (baseRadiusPx / dist)) else raw
                            thumbOffset = clamped
                            onValueChange(clamped.x / baseRadiusPx, -clamped.y / baseRadiusPx)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val next = thumbOffset + dragAmount
                            val dist = kotlin.math.sqrt(next.x * next.x + next.y * next.y)
                            val clamped = if (dist > baseRadiusPx) Offset(next.x * (baseRadiusPx / dist), next.y * (baseRadiusPx / dist)) else next
                            thumbOffset = clamped
                            onValueChange(clamped.x / baseRadiusPx, -clamped.y / baseRadiusPx)
                        },
                        onDragEnd = {
                            thumbOffset = Offset.Zero
                            onValueChange(0f, 0f)
                        },
                        onDragCancel = {
                            thumbOffset = Offset.Zero
                            onValueChange(0f, 0f)
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = (minOf(size.width, size.height) / 2f) * 0.90f
            baseRadiusPx = outerRadius * 0.68f

            // 1. Dark circular base
            drawCircle(
                color = Color(0x60081018),
                radius = outerRadius,
                center = center
            )
            // Outer guideline ring
            drawCircle(
                color = accentColor.copy(alpha = 0.40f),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
            // Inner deadzone ring
            drawCircle(
                color = Color(0x25FFFFFF),
                radius = outerRadius * 0.28f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            // Subtle crosshairs
            drawLine(
                color = Color(0x20FFFFFF),
                start = Offset(center.x - outerRadius * 0.5f, center.y),
                end = Offset(center.x + outerRadius * 0.5f, center.y),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color(0x20FFFFFF),
                start = Offset(center.x, center.y - outerRadius * 0.5f),
                end = Offset(center.x, center.y + outerRadius * 0.5f),
                strokeWidth = 1.dp.toPx()
            )

            // 2. Movable Thumb Pad
            val thumbCenter = center + thumbOffset
            val thumbRadius = outerRadius * 0.36f

            // Glow / Shadow behind thumb
            drawCircle(
                color = accentColor.copy(alpha = 0.22f),
                radius = thumbRadius * 1.30f,
                center = thumbCenter
            )
            // Solid dark thumb pad
            drawCircle(
                color = Color(0xF20D1520),
                radius = thumbRadius,
                center = thumbCenter
            )
            // Thumb metallic accent border
            drawCircle(
                color = accentColor,
                radius = thumbRadius,
                center = thumbCenter,
                style = Stroke(width = 2.dp.toPx())
            )
            // Center tactile dot
            drawCircle(
                color = accentColor,
                radius = 3.dp.toPx(),
                center = thumbCenter
            )
        }

        // Stick label overlay
        Text(
            text = label,
            color = accentColor.copy(alpha = 0.85f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
        )
    }
}
