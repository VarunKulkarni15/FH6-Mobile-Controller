package com.wheelpad.app.network

import com.wheelpad.app.data.model.ControllerProfile
import com.wheelpad.app.data.repository.ProfileRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

class ControlSyncClient(
    private val repository: ProfileRepository
) {
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private var connectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _syncedProfile = MutableStateFlow<ControllerProfile?>(null)
    val syncedProfile: StateFlow<ControllerProfile?> = _syncedProfile

    private val _spotifyState = MutableStateFlow(com.wheelpad.app.data.model.SpotifyTrackState())
    val spotifyState: StateFlow<com.wheelpad.app.data.model.SpotifyTrackState> = _spotifyState

    private val _radioStatus = MutableStateFlow(com.wheelpad.app.data.model.RadioStatus())
    val radioStatus: StateFlow<com.wheelpad.app.data.model.RadioStatus> = _radioStatus

    private val _gameStatus = MutableStateFlow(com.wheelpad.app.data.model.GameStatus())
    val gameStatus: StateFlow<com.wheelpad.app.data.model.GameStatus> = _gameStatus

    private val _isGamePaused = MutableStateFlow(false)
    val isGamePaused: StateFlow<Boolean> = _isGamePaused

    private val _statusMessage = MutableStateFlow("Disconnected")
    val statusMessage: StateFlow<String> = _statusMessage

    fun connect(host: String, port: Int = 4243) {
        disconnect()

        connectionScope.launch {
            try {
                _statusMessage.value = "Connecting to $host:$port..."
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 5000)
                socket = s
                writer = PrintWriter(OutputStreamWriter(s.getOutputStream(), "UTF-8"), true)
                reader = BufferedReader(InputStreamReader(s.getInputStream(), "UTF-8"))

                _isConnected.value = true
                _statusMessage.value = "Connected to Windows"

                listenLoop()
            } catch (e: Exception) {
                _isConnected.value = false
                _statusMessage.value = "Connection failed: ${e.message}"
            }
        }
    }

    private suspend fun listenLoop() = withContext(Dispatchers.IO) {
        try {
            while (_isConnected.value && socket?.isClosed == false) {
                val line = reader?.readLine() ?: break
                handleIncomingMessage(line)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            disconnect()
        }
    }

    private fun handleIncomingMessage(jsonStr: String) {
        try {
            val root = JSONObject(jsonStr)
            val type = root.optString("type", "")

            when (type) {
                "HANDSHAKE_RESPONSE" -> {
                    val activeProfObj = root.optJSONObject("activeProfile")
                    if (activeProfObj != null) {
                        val remoteProfile = repository.parseProfileFromJson(activeProfObj.toString())
                        val localProfile = repository.loadActiveProfile()

                        val localTime = localProfile.lastModifiedUtc
                        val remoteTime = remoteProfile.lastModifiedUtc

                        // Check if local profile is custom or newer than remote
                        val localIsPreferred = when {
                            localTime.isNotBlank() && remoteTime.isBlank() -> true
                            localTime.isNotBlank() && remoteTime.isNotBlank() && localTime >= remoteTime -> true
                            localProfile.layout.isNotEmpty() && remoteProfile.layout.isEmpty() -> true
                            else -> false
                        }

                        if (localIsPreferred) {
                            // Keep user's customized local layout and sync it up to Windows companion
                            _syncedProfile.value = localProfile
                            _statusMessage.value = "Active layout '${localProfile.profileName}' synced to Windows"
                            syncProfileToWindows(localProfile)
                        } else {
                            repository.saveProfile(remoteProfile)
                            repository.setActiveProfileId(remoteProfile.profileId)
                            _syncedProfile.value = remoteProfile
                            _statusMessage.value = "Profile '${remoteProfile.profileName}' synced from Windows"
                        }
                    }
                }
                "SET_ACTIVE_PROFILE_ACK" -> {
                    val activeProfObj = root.optJSONObject("activeProfile")
                    if (activeProfObj != null) {
                        val profile = repository.parseProfileFromJson(activeProfObj.toString())
                        _syncedProfile.value = profile
                    }
                }
                "SAVE_PROFILE_ACK" -> {
                    _statusMessage.value = "Profile saved to Windows companion"
                }
                "GAME_STATUS_CHANGED" -> {
                    _gameStatus.value = com.wheelpad.app.data.model.GameStatus(
                        isRunning = root.optBoolean("isRunning", false),
                        gameName = root.optString("gameName", "Forza Horizon 6"),
                        processId = root.optInt("processId", 0)
                    )
                }
                "PAUSE_STATE_CHANGED" -> {
                    _isGamePaused.value = root.optBoolean("isPaused", false)
                }
                "SPOTIFY_STATE_CHANGED" -> {
                    val trackObj = root.optJSONObject("track")
                    if (trackObj != null) {
                        _spotifyState.value = com.wheelpad.app.data.model.SpotifyTrackState(
                            trackName = trackObj.optString("trackName", "No Track"),
                            artistName = trackObj.optString("artistName", ""),
                            albumArtUrl = trackObj.optString("albumArtUrl", null),
                            isPlaying = trackObj.optBoolean("isPlaying", false),
                            progressMs = trackObj.optLong("progressMs", 0),
                            durationMs = trackObj.optLong("durationMs", 0),
                            activeAccountIndex = trackObj.optInt("activeAccountIndex", 1),
                            accountName = trackObj.optString("accountName", "Account 1"),
                            deviceName = trackObj.optString("deviceName", null)
                        )
                    }
                }
                "RADIO_STATUS_CHANGED" -> {
                    val radioObj = root.optJSONObject("radio")
                    if (radioObj != null) {
                        _radioStatus.value = com.wheelpad.app.data.model.RadioStatus(
                            isInstalled = radioObj.optBoolean("isInstalled", false),
                            currentStation = radioObj.optString("currentStation", "Radio Off"),
                            currentTrack = radioObj.optString("currentTrack", null),
                            volume = radioObj.optInt("volume", 100)
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun syncProfileToWindows(profile: ControllerProfile) {
        connectionScope.launch(Dispatchers.IO) {
            try {
                repository.saveProfile(profile)
                if (_isConnected.value && writer != null) {
                    val payload = JSONObject().apply {
                        put("type", "SAVE_PROFILE")
                        put("profile", JSONObject(repository.serializeProfileToJson(profile)))
                    }
                    writer?.println(payload.toString())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setActiveProfile(profileId: String) {
        connectionScope.launch(Dispatchers.IO) {
            try {
                repository.setActiveProfileId(profileId)
                if (_isConnected.value && writer != null) {
                    val payload = JSONObject().apply {
                        put("type", "SET_ACTIVE_PROFILE")
                        put("profileId", profileId)
                    }
                    writer?.println(payload.toString())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun testActionInput(actionId: String, isPressed: Boolean) {
        connectionScope.launch(Dispatchers.IO) {
            try {
                if (_isConnected.value && writer != null) {
                    val payload = JSONObject().apply {
                        put("type", "TEST_ACTION_INPUT")
                        put("actionId", actionId)
                        put("isPressed", isPressed)
                    }
                    writer?.println(payload.toString())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendSpotifyCommand(command: String) {
        connectionScope.launch(Dispatchers.IO) {
            try {
                if (_isConnected.value && writer != null) {
                    val payload = JSONObject().apply {
                        put("type", "SPOTIFY_COMMAND")
                        put("command", command)
                    }
                    writer?.println(payload.toString())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun switchSpotifyAccount(accountIndex: Int) {
        connectionScope.launch(Dispatchers.IO) {
            try {
                if (_isConnected.value && writer != null) {
                    val payload = JSONObject().apply {
                        put("type", "SPOTIFY_SWITCH_ACCOUNT")
                        put("accountIndex", accountIndex)
                    }
                    writer?.println(payload.toString())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun nextRadioStation() {
        connectionScope.launch(Dispatchers.IO) {
            try {
                if (_isConnected.value && writer != null) {
                    val payload = JSONObject().apply {
                        put("type", "RADIO_NEXT_STATION")
                    }
                    writer?.println(payload.toString())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun disconnect() {
        _isConnected.value = false
        _statusMessage.value = "Disconnected"
        try {
            socket?.close()
        } catch (e: Exception) { }
        socket = null
        writer = null
        reader = null
    }
}

