package com.wheelpad.app.ui.controls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wheelpad.app.data.model.*
import com.wheelpad.app.data.repository.ProfileRepository
import com.wheelpad.app.network.ControlSyncClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ControlEditorUiState(
    val currentProfile: ControllerProfile,
    val selectedElementId: String? = null,
    val isEditMode: Boolean = false,
    val isActionCatalogVisible: Boolean = false,
    val availableActions: List<GameAction> = emptyList(),
    val statusMessage: String = ""
)

class ControlEditorViewModel(
    private val repository: ProfileRepository,
    private val syncClient: ControlSyncClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ControlEditorUiState(
            currentProfile = repository.loadActiveProfile(),
            availableActions = ProfileRepository.ALL_FORZA_ACTIONS
        )
    )
    val uiState: StateFlow<ControlEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            syncClient.syncedProfile.collect { remoteProfile ->
                if (remoteProfile != null) {
                    val mergedActions = (remoteProfile.actions + ProfileRepository.ALL_FORZA_ACTIONS).distinctBy { it.id }
                    val currentProf = _uiState.value.currentProfile

                    // Only adopt remote profile if it is a different profile or strictly newer
                    val shouldAdopt = remoteProfile.profileId != currentProf.profileId ||
                            (remoteProfile.lastModifiedUtc.isNotBlank() && remoteProfile.lastModifiedUtc > currentProf.lastModifiedUtc)

                    if (shouldAdopt) {
                        _uiState.value = _uiState.value.copy(
                            currentProfile = remoteProfile,
                            availableActions = mergedActions,
                            statusMessage = "Synced with Windows: ${remoteProfile.profileName}"
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            availableActions = mergedActions
                        )
                    }
                }
            }
        }
    }

    fun toggleEditMode() {
        val newMode = !_uiState.value.isEditMode
        _uiState.value = _uiState.value.copy(
            isEditMode = newMode,
            selectedElementId = if (!newMode) null else _uiState.value.selectedElementId
        )
        if (!newMode) {
            // Persist when exiting edit mode
            saveAndSyncProfile()
        }
    }

    fun selectElement(elementId: String?) {
        _uiState.value = _uiState.value.copy(selectedElementId = elementId)
    }

    fun resetToDefaultLayout() {
        val defaultProfile = repository.resetActiveProfileToDefault()
        _uiState.value = _uiState.value.copy(
            currentProfile = defaultProfile,
            availableActions = ProfileRepository.ALL_FORZA_ACTIONS,
            selectedElementId = null,
            statusMessage = "Reset layout to default FH6 configuration."
        )
        syncClient.syncProfileToWindows(defaultProfile)
    }

    fun updateElementPosition(elementId: String, normX: Float, normY: Float) {
        val profile = _uiState.value.currentProfile
        val newLayout = profile.layout.map { elem ->
            if (elem.elementId == elementId) {
                elem.copy(
                    normalizedX = normX.coerceIn(0.0f, 1.0f - elem.normalizedWidth),
                    normalizedY = normY.coerceIn(0.0f, 1.0f - elem.normalizedHeight)
                )
            } else elem
        }.toMutableList()

        val updatedProfile = profile.copy(layout = newLayout)
        _uiState.value = _uiState.value.copy(currentProfile = updatedProfile)
        saveAndSyncProfile()
    }

    fun updateElementDeltaPosition(elementId: String, deltaNormX: Float, deltaNormY: Float) {
        val profile = _uiState.value.currentProfile
        val newLayout = profile.layout.map { elem ->
            if (elem.elementId == elementId) {
                val newX = (elem.normalizedX + deltaNormX).coerceIn(0.0f, 1.0f - elem.normalizedWidth)
                val newY = (elem.normalizedY + deltaNormY).coerceIn(0.0f, 1.0f - elem.normalizedHeight)
                elem.copy(normalizedX = newX, normalizedY = newY)
            } else elem
        }.toMutableList()

        val updatedProfile = profile.copy(layout = newLayout)
        _uiState.value = _uiState.value.copy(currentProfile = updatedProfile)
    }

    fun updateElementDeltaSize(elementId: String, deltaScale: Float) {
        val profile = _uiState.value.currentProfile
        val newLayout = profile.layout.map { elem ->
            if (elem.elementId == elementId) {
                val ratio = (elem.normalizedHeight / elem.normalizedWidth).coerceIn(0.2f, 5.0f)
                val newW = (elem.normalizedWidth + deltaScale).coerceIn(0.04f, 0.65f)
                val newH = (newW * ratio).coerceIn(0.04f, 0.85f)
                elem.copy(normalizedWidth = newW, normalizedHeight = newH)
            } else elem
        }.toMutableList()

        val updatedProfile = profile.copy(layout = newLayout)
        _uiState.value = _uiState.value.copy(currentProfile = updatedProfile)
        saveAndSyncProfile()
    }

    fun updateElementSize(elementId: String, normWidth: Float, normHeight: Float) {
        val profile = _uiState.value.currentProfile
        val newLayout = profile.layout.map { elem ->
            if (elem.elementId == elementId) {
                elem.copy(
                    normalizedWidth = normWidth.coerceIn(0.04f, 0.65f),
                    normalizedHeight = normHeight.coerceIn(0.04f, 0.85f)
                )
            } else elem
        }.toMutableList()

        val updatedProfile = profile.copy(layout = newLayout)
        _uiState.value = _uiState.value.copy(currentProfile = updatedProfile)
        saveAndSyncProfile()
    }

    fun updateElementLabel(elementId: String, newLabel: String) {
        val profile = _uiState.value.currentProfile
        val newLayout = profile.layout.map { elem ->
            if (elem.elementId == elementId) elem.copy(label = newLabel) else elem
        }.toMutableList()

        val updatedProfile = profile.copy(layout = newLayout)
        _uiState.value = _uiState.value.copy(currentProfile = updatedProfile)
        saveAndSyncProfile()
    }

    fun removeElement(elementId: String) {
        val profile = _uiState.value.currentProfile
        val newLayout = profile.layout.filter { it.elementId != elementId }.toMutableList()
        val updatedProfile = profile.copy(layout = newLayout)
        _uiState.value = _uiState.value.copy(
            currentProfile = updatedProfile,
            selectedElementId = null
        )
        saveAndSyncProfile()
    }

    fun addActionToLayout(action: GameAction) {
        val profile = _uiState.value.currentProfile

        val shape = when (action.inputType) {
            InputType.AnalogAxis -> ControlShape.CircleWheel
            InputType.AnalogTrigger -> ControlShape.VerticalPedal
            InputType.DigitalButton -> if (action.id == "handbrake") ControlShape.WideBar else ControlShape.RoundedButton
        }

        val newElement = TouchControlElement(
            elementId = "elem_" + UUID.randomUUID().toString().take(8),
            actionId = action.id,
            label = action.name.take(12).uppercase(),
            shape = shape,
            normalizedX = 0.40f,
            normalizedY = 0.40f,
            normalizedWidth = when (shape) {
                ControlShape.CircleWheel -> 0.44f
                ControlShape.WideBar -> 0.30f
                ControlShape.VerticalPedal -> 0.13f
                else -> 0.09f
            },
            normalizedHeight = when (shape) {
                ControlShape.CircleWheel -> 0.78f
                ControlShape.WideBar -> 0.18f
                ControlShape.VerticalPedal -> 0.58f
                else -> 0.14f
            },
            accentColorHex = "#FFFFFF",
            hapticFeedback = true,
            isEnabled = true
        )

        val newLayout = profile.layout.toMutableList().apply { add(newElement) }
        val updatedProfile = profile.copy(layout = newLayout)
        _uiState.value = _uiState.value.copy(
            currentProfile = updatedProfile,
            selectedElementId = newElement.elementId,
            isActionCatalogVisible = false
        )
        saveAndSyncProfile()
    }

    fun createCustomAction(
        name: String,
        bindingType: BindingType,
        xboxTarget: XboxButton,
        keyCode: Int,
        keyName: String,
        behavior: InputBehavior
    ) {
        val profile = _uiState.value.currentProfile
        val newAction = GameAction(
            id = "custom_" + UUID.randomUUID().toString().take(8),
            name = name,
            category = "Custom",
            inputType = InputType.DigitalButton,
            bindingType = bindingType,
            xboxTarget = xboxTarget,
            keyboardKeyCode = keyCode,
            keyboardKeyName = keyName,
            behavior = behavior,
            isCustom = true,
            description = "Custom user action"
        )

        profile.actions.add(newAction)
        _uiState.value = _uiState.value.copy(
            currentProfile = profile,
            availableActions = profile.actions
        )
        addActionToLayout(newAction)
    }

    fun testAction(actionId: String, isPressed: Boolean) {
        syncClient.testActionInput(actionId, isPressed)
    }

    fun setActionCatalogVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(isActionCatalogVisible = visible)
    }

    fun saveAndSyncProfile() {
        val profile = _uiState.value.currentProfile
        repository.saveProfile(profile)
        syncClient.syncProfileToWindows(profile)
    }
}

