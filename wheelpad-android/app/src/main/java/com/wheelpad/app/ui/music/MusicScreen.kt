package com.wheelpad.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelpad.app.data.model.RadioStatus
import com.wheelpad.app.data.model.SpotifyTrackState
import com.wheelpad.app.network.ControlSyncClient

/**
 * Compact Floating Music Bar for the In-Drive HUD
 * Does not obstruct steering or pedals.
 */
@Composable
fun FloatingMiniMusicBar(
    syncClient: ControlSyncClient,
    modifier: Modifier = Modifier
) {
    val track by syncClient.spotifyState.collectAsState()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .border(1.2.dp, Color(0x60FFFFFF), RoundedCornerShape(24.dp))
            .background(Color(0xD0131720))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Account Badge Toggle (Account 1 vs Account 2)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(12.dp))
                .clickable {
                    val nextAcct = if (track.activeAccountIndex == 1) 2 else 1
                    syncClient.switchSpotifyAccount(nextAcct)
                }
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "ACCT ${track.activeAccountIndex}",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Track Name & Artist
        Column(modifier = Modifier.widthIn(max = 140.dp)) {
            Text(
                text = track.trackName,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (track.artistName.isNotEmpty()) {
                Text(
                    text = track.artistName,
                    color = Color(0x99FFFFFF),
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
        }

        // Play / Pause Button
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .border(1.dp, Color.White, CircleShape)
                .clickable {
                    val cmd = if (track.isPlaying) "pause" else "play"
                    syncClient.sendSpotifyCommand(cmd)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (track.isPlaying) "||" else "▶",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
        }

        // Next Track Button
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .border(1.dp, Color(0x60FFFFFF), CircleShape)
                .clickable { syncClient.sendSpotifyCommand("next") },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "▶|",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Full Music Center Screen Tab
 */
@Composable
fun MusicCenterScreen(
    syncClient: ControlSyncClient,
    modifier: Modifier = Modifier
) {
    val track by syncClient.spotifyState.collectAsState()
    val radio by syncClient.radioStatus.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C1017))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Section 1: Top Navigation & Dual-Account Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SPOTIFY MUSIC CENTER",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )

            // Two Separately Authorized Spotify Accounts Toggle
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountPill(
                    label = "ACCOUNT 1",
                    isSelected = track.activeAccountIndex == 1,
                    onClick = { syncClient.switchSpotifyAccount(1) }
                )
                AccountPill(
                    label = "ACCOUNT 2",
                    isSelected = track.activeAccountIndex == 2,
                    onClick = { syncClient.switchSpotifyAccount(2) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Section 2: Player Card (Minimalist Outline)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.5.dp, Color(0x60FFFFFF), RoundedCornerShape(20.dp))
                .background(Color(0x15FFFFFF))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = track.trackName,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = track.artistName,
                    color = Color(0xB3FFFFFF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                val devName = track.deviceName
                if (!devName.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "PLAYING ON: ${devName.uppercase()}",
                        color = Color(0x66FFFFFF),
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Playback Controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { syncClient.sendSpotifyCommand("previous") },
                        modifier = Modifier.border(1.dp, Color(0x60FFFFFF), CircleShape)
                    ) {
                        Text("|◀", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = {
                            val cmd = if (track.isPlaying) "pause" else "play"
                            syncClient.sendSpotifyCommand(cmd)
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .border(1.5.dp, Color.White, CircleShape)
                    ) {
                        Text(
                            text = if (track.isPlaying) "||" else "▶",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    IconButton(
                        onClick = { syncClient.sendSpotifyCommand("next") },
                        modifier = Modifier.border(1.dp, Color(0x60FFFFFF), CircleShape)
                    ) {
                        Text("▶|", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section 3: Optional FH6 Universal Radio Mod Panel
        if (radio.isInstalled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "FH6 IN-GAME RADIO MOD",
                            color = Color(0x80FFFFFF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = radio.currentStation,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = { syncClient.nextRadioStation() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FFFFFF))
                    ) {
                        Text("NEXT STATION", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AccountPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color.White else Color.Transparent)
            .border(1.dp, Color.White, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color(0xFF0C1017) else Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

