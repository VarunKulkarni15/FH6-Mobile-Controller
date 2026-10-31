package com.wheelpad.app.data.repository

import android.content.Context
import com.wheelpad.app.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ProfileRepository(private val context: Context) {

    private val profilesDir: File by lazy {
        File(context.filesDir, "profiles").apply { mkdirs() }
    }

    private val prefs by lazy {
        context.getSharedPreferences("wheelpad_prefs", Context.MODE_PRIVATE)
    }

    fun getActiveProfileId(): String {
        return prefs.getString("active_profile_id", "profile_standard") ?: "profile_standard"
    }

    fun setActiveProfileId(profileId: String) {
        prefs.edit().putString("active_profile_id", profileId).apply()
    }

    fun loadAllProfiles(): List<ControllerProfile> {
        val files = profilesDir.listFiles { _, name -> name.endsWith(".json") }
        if (files.isNullOrEmpty()) {
            val defaults = createDefaultProfiles()
            defaults.forEach { saveProfile(it) }
            return defaults
        }

        val list = mutableListOf<ControllerProfile>()
        for (file in files) {
            try {
                val json = file.readText()
                val profile = parseProfileFromJson(json)

                // Auto-upgrade: Ensure canonical actions exist in the profile
                val actionsPool = if (profile.profileId == "profile_tlou2") ALL_TLOU_ACTIONS else ALL_FORZA_ACTIONS
                val existingIds = profile.actions.map { it.id }.toSet()
                var modified = false
                for (action in actionsPool) {
                    if (!existingIds.contains(action.id)) {
                        profile.actions.add(action)
                        modified = true
                    }
                }
                if (modified) {
                    saveProfile(profile)
                }
                list.add(profile)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Ensure default TLOU2 profile is always populated
        if (list.none { it.profileId == "profile_tlou2" }) {
            val tlou = createTlouProfile()
            saveProfile(tlou)
            list.add(tlou)
        }

        return if (list.isNotEmpty()) list else createDefaultProfiles()
    }

    fun loadActiveProfile(): ControllerProfile {
        val all = loadAllProfiles()
        val activeId = getActiveProfileId()
        return all.find { it.profileId == activeId } ?: all.first()
    }

    fun resetActiveProfileToDefault(): ControllerProfile {
        val defaultProfile = createDefaultProfiles().first()
        saveProfile(defaultProfile)
        setActiveProfileId(defaultProfile.profileId)
        return defaultProfile
    }

    fun saveProfile(profile: ControllerProfile) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            profile.lastModifiedUtc = java.time.Instant.now().toString()
        } else {
            profile.lastModifiedUtc = System.currentTimeMillis().toString()
        }
        val json = serializeProfileToJson(profile)
        val file = File(profilesDir, "${profile.profileId}.json")
        file.writeText(json)
    }

    fun deleteProfile(profileId: String): Boolean {
        val file = File(profilesDir, "$profileId.json")
        return if (file.exists()) file.delete() else false
    }

    fun serializeProfileToJson(profile: ControllerProfile): String {
        val obj = JSONObject().apply {
            put("profileId", profile.profileId)
            put("profileName", profile.profileName)
            put("isDefault", profile.isDefault)
            put("lastModifiedUtc", profile.lastModifiedUtc)

            put("steeringConfig", JSONObject().apply {
                put("maxRotationDegrees", profile.steeringConfig.maxRotationDegrees)
                put("deadzonePercent", profile.steeringConfig.deadzonePercent)
                put("gammaCurve", profile.steeringConfig.gammaCurve)
                put("returnToCenterSpeed", profile.steeringConfig.returnToCenterSpeed)
                put("useGyroSteering", profile.steeringConfig.useGyroSteering)
            })

            put("actions", JSONArray().apply {
                profile.actions.forEach { act ->
                    put(JSONObject().apply {
                        put("id", act.id)
                        put("name", act.name)
                        put("category", act.category)
                        put("inputType", act.inputType.name)
                        put("bindingType", act.bindingType.name)
                        put("xboxTarget", act.xboxTarget.name)
                        put("keyboardKeyCode", act.keyboardKeyCode)
                        put("keyboardKeyName", act.keyboardKeyName)
                        put("behavior", act.behavior.name)
                        put("repeatIntervalMs", act.repeatIntervalMs)
                        put("isCustom", act.isCustom)
                        put("description", act.description)
                    })
                }
            })

            put("layout", JSONArray().apply {
                profile.layout.forEach { elem ->
                    put(JSONObject().apply {
                        put("elementId", elem.elementId)
                        put("actionId", elem.actionId)
                        put("label", elem.label)
                        put("shape", elem.shape.name)
                        put("normalizedX", elem.normalizedX)
                        put("normalizedY", elem.normalizedY)
                        put("normalizedWidth", elem.normalizedWidth)
                        put("normalizedHeight", elem.normalizedHeight)
                        put("opacity", elem.opacity)
                        put("accentColorHex", elem.accentColorHex)
                        put("hapticFeedback", elem.hapticFeedback)
                        put("isEnabled", elem.isEnabled)
                    })
                }
            })
        }
        return obj.toString(2)
    }

    fun parseProfileFromJson(jsonStr: String): ControllerProfile {
        val root = JSONObject(jsonStr)
        val profile = ControllerProfile(
            profileId = root.optString("profileId", "custom"),
            profileName = root.optString("profileName", "Custom Profile"),
            isDefault = root.optBoolean("isDefault", false),
            lastModifiedUtc = root.optString("lastModifiedUtc", "")
        )

        root.optJSONObject("steeringConfig")?.let { sc ->
            profile.steeringConfig = SteeringConfig(
                maxRotationDegrees = sc.optInt("maxRotationDegrees", 540),
                deadzonePercent = sc.optDouble("deadzonePercent", 0.02).toFloat(),
                gammaCurve = sc.optDouble("gammaCurve", 1.15).toFloat(),
                returnToCenterSpeed = sc.optDouble("returnToCenterSpeed", 0.85).toFloat(),
                useGyroSteering = sc.optBoolean("useGyroSteering", false)
            )
        }

        root.optJSONArray("actions")?.let { arr ->
            for (i in 0 until arr.length()) {
                val actObj = arr.getJSONObject(i)
                profile.actions.add(
                    GameAction(
                        id = actObj.optString("id", ""),
                        name = actObj.optString("name", ""),
                        category = actObj.optString("category", "General"),
                        inputType = runCatching { InputType.valueOf(actObj.optString("inputType")) }.getOrDefault(InputType.DigitalButton),
                        bindingType = runCatching { BindingType.valueOf(actObj.optString("bindingType")) }.getOrDefault(BindingType.XboxController),
                        xboxTarget = runCatching { XboxButton.valueOf(actObj.optString("xboxTarget")) }.getOrDefault(XboxButton.None),
                        keyboardKeyCode = actObj.optInt("keyboardKeyCode", 0),
                        keyboardKeyName = actObj.optString("keyboardKeyName", ""),
                        behavior = runCatching { InputBehavior.valueOf(actObj.optString("behavior")) }.getOrDefault(InputBehavior.Instant),
                        repeatIntervalMs = actObj.optInt("repeatIntervalMs", 50),
                        isCustom = actObj.optBoolean("isCustom", false),
                        description = actObj.optString("description", "")
                    )
                )
            }
        }

        root.optJSONArray("layout")?.let { arr ->
            for (i in 0 until arr.length()) {
                val elemObj = arr.getJSONObject(i)
                profile.layout.add(
                    TouchControlElement(
                        elementId = elemObj.optString("elementId", ""),
                        actionId = elemObj.optString("actionId", ""),
                        label = elemObj.optString("label", ""),
                        shape = runCatching { ControlShape.valueOf(elemObj.optString("shape")) }.getOrDefault(ControlShape.RoundedButton),
                        normalizedX = elemObj.optDouble("normalizedX", 0.5).toFloat(),
                        normalizedY = elemObj.optDouble("normalizedY", 0.5).toFloat(),
                        normalizedWidth = elemObj.optDouble("normalizedWidth", 0.1).toFloat(),
                        normalizedHeight = elemObj.optDouble("normalizedHeight", 0.1).toFloat(),
                        opacity = elemObj.optDouble("opacity", 0.85).toFloat(),
                        accentColorHex = elemObj.optString("accentColorHex", "#00E5FF"),
                        hapticFeedback = elemObj.optBoolean("hapticFeedback", true),
                        isEnabled = elemObj.optBoolean("isEnabled", true)
                    )
                )
            }
        }

        return profile
    }

    private fun createDefaultProfiles(): List<ControllerProfile> {
        val standard = ControllerProfile(
            profileId = "profile_standard",
            profileName = "Standard Racing",
            isDefault = true,
            steeringConfig = SteeringConfig(maxRotationDegrees = 540, deadzonePercent = 0.02f, gammaCurve = 1.15f)
        ).apply {
            actions.addAll(ALL_FORZA_ACTIONS)

            layout.add(TouchControlElement(elementId = "wheel", actionId = "steer", label = "STEER", shape = ControlShape.CircleWheel, normalizedX = 0.04f, normalizedY = 0.12f, normalizedWidth = 0.44f, normalizedHeight = 0.78f, accentColorHex = "#FFFFFF"))
            layout.add(TouchControlElement(elementId = "brake", actionId = "brake", label = "BRAKE", shape = ControlShape.VerticalPedal, normalizedX = 0.68f, normalizedY = 0.12f, normalizedWidth = 0.13f, normalizedHeight = 0.58f, accentColorHex = "#FF1744"))
            layout.add(TouchControlElement(elementId = "gas", actionId = "throttle", label = "GAS", shape = ControlShape.VerticalPedal, normalizedX = 0.83f, normalizedY = 0.12f, normalizedWidth = 0.13f, normalizedHeight = 0.58f, accentColorHex = "#00E676"))
            layout.add(TouchControlElement(elementId = "handbrake", actionId = "handbrake", label = "HANDBRAKE", shape = ControlShape.WideBar, normalizedX = 0.66f, normalizedY = 0.74f, normalizedWidth = 0.30f, normalizedHeight = 0.18f, accentColorHex = "#FFFFFF"))
            layout.add(TouchControlElement(elementId = "auto_drive", actionId = "auto_drive", label = "AUTO DRIVE", shape = ControlShape.RoundedButton, normalizedX = 0.51f, normalizedY = 0.76f, normalizedWidth = 0.12f, normalizedHeight = 0.16f, accentColorHex = "#CCFF00"))
            layout.add(TouchControlElement(elementId = "rewind", actionId = "rewind", label = "REWIND", shape = ControlShape.RoundedButton, normalizedX = 0.51f, normalizedY = 0.14f, normalizedWidth = 0.12f, normalizedHeight = 0.16f, accentColorHex = "#FFB300"))
        }

        val tlou = createTlouProfile()
        return listOf(standard, tlou)
    }

    fun createTlouProfile(): ControllerProfile {
        return ControllerProfile(
            profileId = "profile_tlou2",
            profileName = "The Last of Us Part II",
            isDefault = false,
            steeringConfig = SteeringConfig(maxRotationDegrees = 540, deadzonePercent = 0.02f, gammaCurve = 1.0f)
        ).apply {
            actions.addAll(ALL_TLOU_ACTIONS)

            // Left Movement Stick
            layout.add(TouchControlElement(elementId = "tlou_move", actionId = "move_stick", label = "MOVE (LS)", shape = ControlShape.AnalogStick, normalizedX = 0.04f, normalizedY = 0.38f, normalizedWidth = 0.25f, normalizedHeight = 0.52f, accentColorHex = "#00E5FF"))

            // Left Shoulder Triggers: Aim & Dodge
            layout.add(TouchControlElement(elementId = "tlou_lt", actionId = "aim_weapon", label = "AIM (LT)", shape = ControlShape.RoundedButton, normalizedX = 0.04f, normalizedY = 0.08f, normalizedWidth = 0.14f, normalizedHeight = 0.20f, accentColorHex = "#00E5FF"))
            layout.add(TouchControlElement(elementId = "tlou_lb", actionId = "dodge", label = "DODGE (LB)", shape = ControlShape.RoundedButton, normalizedX = 0.20f, normalizedY = 0.08f, normalizedWidth = 0.12f, normalizedHeight = 0.20f, accentColorHex = "#FFFFFF"))

            // Tactical D-Pad Weapon Cross (Center-Left)
            layout.add(TouchControlElement(elementId = "tlou_dup", actionId = "dpad_up", label = "MEDKIT", shape = ControlShape.RoundedButton, normalizedX = 0.34f, normalizedY = 0.36f, normalizedWidth = 0.08f, normalizedHeight = 0.15f, accentColorHex = "#00E676"))
            layout.add(TouchControlElement(elementId = "tlou_ddown", actionId = "dpad_down", label = "THROW", shape = ControlShape.RoundedButton, normalizedX = 0.34f, normalizedY = 0.68f, normalizedWidth = 0.08f, normalizedHeight = 0.15f, accentColorHex = "#FFB300"))
            layout.add(TouchControlElement(elementId = "tlou_dleft", actionId = "dpad_left", label = "PISTOL", shape = ControlShape.RoundedButton, normalizedX = 0.28f, normalizedY = 0.52f, normalizedWidth = 0.08f, normalizedHeight = 0.15f, accentColorHex = "#FFFFFF"))
            layout.add(TouchControlElement(elementId = "tlou_dright", actionId = "dpad_right", label = "RIFLE", shape = ControlShape.RoundedButton, normalizedX = 0.40f, normalizedY = 0.52f, normalizedWidth = 0.08f, normalizedHeight = 0.15f, accentColorHex = "#FFFFFF"))

            // Right Aim & Look Stick
            layout.add(TouchControlElement(elementId = "tlou_aim", actionId = "aim_stick", label = "LOOK / AIM (RS)", shape = ControlShape.AnalogStick, normalizedX = 0.50f, normalizedY = 0.40f, normalizedWidth = 0.24f, normalizedHeight = 0.50f, accentColorHex = "#CCFF00"))

            // Right Shoulder Triggers: Fire & Listen Mode
            layout.add(TouchControlElement(elementId = "tlou_rt", actionId = "fire_weapon", label = "FIRE (RT)", shape = ControlShape.RoundedButton, normalizedX = 0.82f, normalizedY = 0.08f, normalizedWidth = 0.14f, normalizedHeight = 0.20f, accentColorHex = "#FF1744"))
            layout.add(TouchControlElement(elementId = "tlou_rb", actionId = "listen_mode", label = "LISTEN (RB)", shape = ControlShape.RoundedButton, normalizedX = 0.68f, normalizedY = 0.08f, normalizedWidth = 0.12f, normalizedHeight = 0.20f, accentColorHex = "#CCFF00"))

            // Action Diamond (Far Right)
            layout.add(TouchControlElement(elementId = "tlou_y", actionId = "btn_y", label = "GRAB (Y)", shape = ControlShape.RoundedButton, normalizedX = 0.86f, normalizedY = 0.32f, normalizedWidth = 0.09f, normalizedHeight = 0.16f, accentColorHex = "#FFB300"))
            layout.add(TouchControlElement(elementId = "tlou_x", actionId = "btn_x", label = "STRIKE (X)", shape = ControlShape.RoundedButton, normalizedX = 0.78f, normalizedY = 0.49f, normalizedWidth = 0.09f, normalizedHeight = 0.16f, accentColorHex = "#00E5FF"))
            layout.add(TouchControlElement(elementId = "tlou_b", actionId = "btn_b", label = "CROUCH (B)", shape = ControlShape.RoundedButton, normalizedX = 0.94f, normalizedY = 0.49f, normalizedWidth = 0.09f, normalizedHeight = 0.16f, accentColorHex = "#FF1744"))
            layout.add(TouchControlElement(elementId = "tlou_a", actionId = "btn_a", label = "JUMP (A)", shape = ControlShape.RoundedButton, normalizedX = 0.86f, normalizedY = 0.66f, normalizedWidth = 0.09f, normalizedHeight = 0.16f, accentColorHex = "#00E676"))

            // Top Center Utility
            layout.add(TouchControlElement(elementId = "tlou_bag", actionId = "map", label = "BAG (VIEW)", shape = ControlShape.RoundedButton, normalizedX = 0.45f, normalizedY = 0.08f, normalizedWidth = 0.09f, normalizedHeight = 0.16f, accentColorHex = "#7E94AD"))
            layout.add(TouchControlElement(elementId = "tlou_pause", actionId = "pause", label = "MENU", shape = ControlShape.RoundedButton, normalizedX = 0.56f, normalizedY = 0.08f, normalizedWidth = 0.08f, normalizedHeight = 0.16f, accentColorHex = "#7E94AD"))
        }
    }

    companion object {
        val ALL_TLOU_ACTIONS = listOf(
            GameAction(id = "move_stick", name = "Move (Left Stick)", inputType = InputType.AnalogAxis, bindingType = BindingType.XboxController, xboxTarget = XboxButton.LeftThumbX, description = "Character 360° Movement"),
            GameAction(id = "aim_stick", name = "Aim & Look (Right Stick)", inputType = InputType.AnalogAxis, bindingType = BindingType.XboxController, xboxTarget = XboxButton.RightThumbX, description = "Camera Look & Reticle Aim"),
            GameAction(id = "aim_weapon", name = "Aim Weapon (LT)", inputType = InputType.AnalogTrigger, bindingType = BindingType.XboxController, xboxTarget = XboxButton.LeftTrigger, description = "Aim Gun / Scope / Draw Bow"),
            GameAction(id = "fire_weapon", name = "Fire / Strike (RT)", inputType = InputType.AnalogTrigger, bindingType = BindingType.XboxController, xboxTarget = XboxButton.RightTrigger, description = "Shoot / Melee Attack"),
            GameAction(id = "dodge", name = "Dodge (LB)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.LeftBumper, description = "Dodge incoming melee attacks"),
            GameAction(id = "listen_mode", name = "Listen Mode (RB)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.RightBumper, description = "Enhanced hearing through obstacles"),
            GameAction(id = "btn_a", name = "Jump / Vault (A)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.A, description = "Jump, vault barriers, climb"),
            GameAction(id = "btn_b", name = "Crouch / Prone (B)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.B, description = "Crouch or hold to go prone"),
            GameAction(id = "btn_x", name = "Melee Attack (X)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.X, description = "Strike with fists or melee weapon"),
            GameAction(id = "btn_y", name = "Interact / Shiv (Y)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.Y, description = "Grab / Stealth kill / Pick up supplies / Reload"),
            GameAction(id = "dpad_up", name = "Medkit / Heal (Up)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.DpadUp, description = "Use medkit or toggle flashlight"),
            GameAction(id = "dpad_down", name = "Throwables (Down)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.DpadDown, description = "Equip Brick, Bottle, or Bomb"),
            GameAction(id = "dpad_left", name = "Sidearms (Left)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.DpadLeft, description = "Swap to Pistol / Revolver"),
            GameAction(id = "dpad_right", name = "Long Guns (Right)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.DpadRight, description = "Swap to Rifle / Shotgun / Bow"),
            GameAction(id = "map", name = "Backpack / Crafting (Back)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.Back, description = "Open backpack and crafting grid"),
            GameAction(id = "pause", name = "Pause / Menu (Start)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.Start, description = "Pause game / Options"),
            GameAction(id = "btn_ls", name = "Sprint (L3)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.LeftThumbClick, description = "Sprint toggle"),
            GameAction(id = "btn_rs", name = "Flashlight / Zoom (R3)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.RightThumbClick, description = "Scope zoom toggle / Flashlight")
        )

        val ALL_FORZA_ACTIONS = listOf(
            GameAction(id = "steer", name = "Steering Wheel", inputType = InputType.AnalogAxis, bindingType = BindingType.XboxController, xboxTarget = XboxButton.LeftThumbX, description = "Proportional analog steering (-1.0 to +1.0)"),
            GameAction(id = "throttle", name = "Accelerator (RT)", inputType = InputType.AnalogTrigger, bindingType = BindingType.XboxController, xboxTarget = XboxButton.RightTrigger, keyboardKeyCode = 87, keyboardKeyName = "W", description = "Analog accelerator pedal"),
            GameAction(id = "brake", name = "Brake / Rev (LT)", inputType = InputType.AnalogTrigger, bindingType = BindingType.XboxController, xboxTarget = XboxButton.LeftTrigger, keyboardKeyCode = 83, keyboardKeyName = "S", description = "Analog brake & reverse pedal"),
            GameAction(id = "handbrake", name = "Handbrake / E-Brake", inputType = InputType.DigitalButton, bindingType = BindingType.Composite, xboxTarget = XboxButton.A, keyboardKeyCode = 32, keyboardKeyName = "Space", behavior = InputBehavior.Hold, description = "Instant drift / e-brake"),
            GameAction(id = "btn_a", name = "A Button (Select / OK)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.A, description = "Xbox (A) button"),
            GameAction(id = "btn_b", name = "B Button (Shift Up)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.B, description = "Xbox (B) / Shift Up"),
            GameAction(id = "btn_x", name = "X Button (Shift Down)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.X, description = "Xbox (X) / Shift Down"),
            GameAction(id = "rewind", name = "Y Button (Rewind)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.Y, keyboardKeyCode = 82, keyboardKeyName = "R", description = "Xbox (Y) / Forza Rewind"),
            GameAction(id = "btn_lb", name = "LB (Clutch)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.LeftBumper, description = "Left Bumper / Manual Clutch"),
            GameAction(id = "camera", name = "RB (Switch Camera)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.RightBumper, keyboardKeyCode = 67, keyboardKeyName = "C", description = "Right Bumper / Switch Camera"),
            GameAction(id = "btn_ls", name = "LS (Horn / Click)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.LeftThumbClick, description = "Left Stick Click / Car Horn"),
            GameAction(id = "btn_rs", name = "RS (Look Back / Click)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.RightThumbClick, description = "Right Stick Click / Look Back"),
            GameAction(id = "map", name = "View (World Map)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.Back, description = "View Button / World Map"),
            GameAction(id = "pause", name = "Menu (Pause Game)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.Start, description = "Menu Button / Pause"),
            GameAction(id = "dpad_up", name = "D-Pad UP (Photo Mode)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.DpadUp, description = "Photo Mode / Telemetry"),
            GameAction(id = "dpad_down", name = "D-Pad DOWN (ANNA / Chat)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.DpadDown, description = "ANNA Navigation / Link Chat"),
            GameAction(id = "dpad_left", name = "D-Pad LEFT (Forza LINK)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.DpadLeft, description = "Forza LINK / Previous Station"),
            GameAction(id = "dpad_right", name = "D-Pad RIGHT (Radio Next)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.DpadRight, description = "Next Radio Station / Telemetry"),
            GameAction(id = "auto_drive", name = "Auto-Drive (Assist)", inputType = InputType.DigitalButton, bindingType = BindingType.KeyboardKey, keyboardKeyCode = 67, keyboardKeyName = "C+2", description = "Hardware driving assist sequence toggle")
        )
    }
}

