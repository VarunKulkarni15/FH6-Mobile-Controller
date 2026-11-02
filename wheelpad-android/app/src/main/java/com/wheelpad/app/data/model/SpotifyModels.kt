package com.wheelpad.app.data.model

data class SpotifyTrackState(
    val trackName: String = "No Track Playing",
    val artistName: String = "",
    val albumArtUrl: String? = null,
    val isPlaying: Boolean = false,
    val progressMs: Long = 0,
    val durationMs: Long = 0,
    val activeAccountIndex: Int = 1,
    val accountName: String = "Account 1",
    val deviceName: String? = null
)

data class RadioStatus(
    val isInstalled: Boolean = false,
    val currentStation: String = "Radio Off",
    val currentTrack: String? = null,
    val volume: Int = 100
)

data class GameStatus(
    val isRunning: Boolean = false,
    val gameName: String = "Forza Horizon 6",
    val processId: Int = 0
)

