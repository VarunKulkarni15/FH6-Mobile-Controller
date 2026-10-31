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
                list.add(profile)
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
            actions.add(GameAction(id = "steer", name = "Steering", inputType = InputType.AnalogAxis, bindingType = BindingType.XboxController, xboxTarget = XboxButton.LeftThumbX))
            actions.add(GameAction(id = "throttle", name = "Accelerator (RT)", inputType = InputType.AnalogTrigger, bindingType = BindingType.XboxController, xboxTarget = XboxButton.RightTrigger, keyboardKeyCode = 87, keyboardKeyName = "W"))
            actions.add(GameAction(id = "brake", name = "Brake / Rev (LT)", inputType = InputType.AnalogTrigger, bindingType = BindingType.XboxController, xboxTarget = XboxButton.LeftTrigger, keyboardKeyCode = 83, keyboardKeyName = "S"))
            actions.add(GameAction(id = "handbrake", name = "Handbrake (A)", inputType = InputType.DigitalButton, bindingType = BindingType.Composite, xboxTarget = XboxButton.A, keyboardKeyCode = 32, keyboardKeyName = "Space", behavior = InputBehavior.Hold))
            actions.add(GameAction(id = "btn_a", name = "A Button", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.A))
            actions.add(GameAction(id = "btn_b", name = "B Button", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.B))
            actions.add(GameAction(id = "btn_x", name = "X Button", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.X))
            actions.add(GameAction(id = "rewind", name = "Y Button (Rewind)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.Y, keyboardKeyCode = 82, keyboardKeyName = "R"))
            actions.add(GameAction(id = "btn_lb", name = "LB (Clutch)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.LeftBumper))
            actions.add(GameAction(id = "camera", name = "RB (Camera)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.RightBumper, keyboardKeyCode = 67, keyboardKeyName = "C"))
            actions.add(GameAction(id = "btn_ls", name = "LS (Horn / Click)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.LeftThumbClick))
            actions.add(GameAction(id = "btn_rs", name = "RS (Look Back)", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.RightThumbClick))
            actions.add(GameAction(id = "map", name = "View / Map", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.Back))
            actions.add(GameAction(id = "pause", name = "Menu / Pause", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.Start))
            actions.add(GameAction(id = "dpad_up", name = "D-Pad UP", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.DpadUp))
            actions.add(GameAction(id = "dpad_down", name = "D-Pad DOWN", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.DpadDown))
            actions.add(GameAction(id = "dpad_left", name = "D-Pad LEFT", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.DpadLeft))
            actions.add(GameAction(id = "dpad_right", name = "D-Pad RIGHT", inputType = InputType.DigitalButton, bindingType = BindingType.XboxController, xboxTarget = XboxButton.DpadRight))
            actions.add(GameAction(id = "auto_drive", name = "Auto Drive (C+2)", inputType = InputType.DigitalButton, bindingType = BindingType.KeyboardKey, keyboardKeyCode = 67, keyboardKeyName = "C+2", description = "1-Click Full Auto Drive (triggers C then Num 2)"))

            layout.add(TouchControlElement(elementId = "wheel", actionId = "steer", label = "STEER", shape = ControlShape.CircleWheel, normalizedX = 0.04f, normalizedY = 0.12f, normalizedWidth = 0.44f, normalizedHeight = 0.78f, accentColorHex = "#FFFFFF"))
            layout.add(TouchControlElement(elementId = "brake", actionId = "brake", label = "BRAKE", shape = ControlShape.VerticalPedal, normalizedX = 0.68f, normalizedY = 0.12f, normalizedWidth = 0.13f, normalizedHeight = 0.58f, accentColorHex = "#FF1744"))
            layout.add(TouchControlElement(elementId = "gas", actionId = "throttle", label = "GAS", shape = ControlShape.VerticalPedal, normalizedX = 0.83f, normalizedY = 0.12f, normalizedWidth = 0.13f, normalizedHeight = 0.58f, accentColorHex = "#00E676"))
            layout.add(TouchControlElement(elementId = "handbrake", actionId = "handbrake", label = "HANDBRAKE", shape = ControlShape.WideBar, normalizedX = 0.66f, normalizedY = 0.74f, normalizedWidth = 0.30f, normalizedHeight = 0.18f, accentColorHex = "#FFFFFF"))
        }

        return listOf(standard)
    }
}

