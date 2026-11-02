package com.wheelpad.app.data.model

import java.util.UUID

enum class InputType {
    AnalogAxis,
    AnalogTrigger,
    DigitalButton
}

enum class BindingType {
    XboxController,
    KeyboardKey,
    Composite
}

enum class XboxButton {
    None,
    A,
    B,
    X,
    Y,
    LeftBumper,
    RightBumper,
    Back,
    Start,
    Guide,
    LeftThumbClick,
    RightThumbClick,
    DpadUp,
    DpadDown,
    DpadLeft,
    DpadRight,
    LeftThumbX,
    LeftThumbY,
    RightThumbX,
    RightThumbY,
    LeftTrigger,
    RightTrigger
}

enum class InputBehavior {
    Instant,
    Hold,
    Toggle,
    Repeat
}

enum class ControlShape {
    CircleWheel,
    VerticalPedal,
    RoundedButton,
    WideBar,
    PillToggle,
    AnalogStick,
    TouchLookArea
}

data class GameAction(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var category: String = "General",
    var inputType: InputType = InputType.DigitalButton,
    var bindingType: BindingType = BindingType.XboxController,
    var xboxTarget: XboxButton = XboxButton.None,
    var keyboardKeyCode: Int = 0,
    var keyboardKeyName: String = "",
    var behavior: InputBehavior = InputBehavior.Instant,
    var repeatIntervalMs: Int = 50,
    var isCustom: Boolean = false,
    var description: String = ""
)

data class TouchControlElement(
    val elementId: String = UUID.randomUUID().toString(),
    var actionId: String = "",
    var label: String = "",
    var shape: ControlShape = ControlShape.RoundedButton,
    var normalizedX: Float = 0.5f,
    var normalizedY: Float = 0.5f,
    var normalizedWidth: Float = 0.1f,
    var normalizedHeight: Float = 0.1f,
    var opacity: Float = 0.85f,
    var accentColorHex: String = "#CCFF00",
    var hapticFeedback: Boolean = true,
    var isEnabled: Boolean = true
)

data class SteeringConfig(
    var maxRotationDegrees: Int = 540,
    var deadzonePercent: Float = 0.02f,
    var gammaCurve: Float = 1.15f,
    var returnToCenterSpeed: Float = 0.85f,
    var useGyroSteering: Boolean = false
)

data class ControllerProfile(
    val profileId: String = UUID.randomUUID().toString(),
    var profileName: String = "Custom Profile",
    var isDefault: Boolean = false,
    var lastModifiedUtc: String = "",
    var steeringConfig: SteeringConfig = SteeringConfig(),
    var actions: MutableList<GameAction> = mutableListOf(),
    var layout: MutableList<TouchControlElement> = mutableListOf()
)

