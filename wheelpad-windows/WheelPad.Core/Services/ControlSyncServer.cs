using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using WheelPad.Core.Models;

namespace WheelPad.Core.Services
{
    public class ControlSyncServer : IDisposable
    {
        public const int DefaultTcpPort = 4243;
        private readonly ProfileManager _profileManager;
        private readonly InputDispatcher _inputDispatcher;
        private readonly int _port;
        private TcpListener? _listener;
        private CancellationTokenSource? _cts;
        private readonly JsonSerializerOptions _jsonOptions;
        private readonly System.Collections.Concurrent.ConcurrentDictionary<StreamWriter, bool> _activeWriters = new();

        public event EventHandler<bool>? ClientConnectionChanged;
        public event EventHandler<string>? LogMessage;

        public SpotifyManager? Spotify { get; set; }
        public UniversalRadioService? Radio { get; set; }

        public bool IsClientConnected => !_activeWriters.IsEmpty;

        public void BroadcastEvent(object payload)
        {
            try
            {
                string json = JsonSerializer.Serialize(payload, _jsonOptions);
                foreach (var writer in _activeWriters.Keys)
                {
                    try
                    {
                        writer.WriteLine(json);
                    }
                    catch { }
                }
            }
            catch (Exception ex)
            {
                LogMessage?.Invoke(this, $"[SyncServer] Broadcast error: {ex.Message}");
            }
        }

        public ControlSyncServer(ProfileManager profileManager, InputDispatcher inputDispatcher, int port = DefaultTcpPort)
        {
            _profileManager = profileManager;
            _inputDispatcher = inputDispatcher;
            _port = port;

            _jsonOptions = new JsonSerializerOptions
            {
                PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
                PropertyNameCaseInsensitive = true,
                WriteIndented = false
            };
        }

        public void Start()
        {
            _cts = new CancellationTokenSource();
            _listener = new TcpListener(IPAddress.Any, _port);
            _listener.Start();

            LogMessage?.Invoke(this, $"[SyncServer] Listening for Android companion on port {_port}...");
            _ = AcceptClientsAsync(_cts.Token);
        }

        private async Task AcceptClientsAsync(CancellationToken token)
        {
            while (!token.IsCancellationRequested && _listener != null)
            {
                try
                {
                    var client = await _listener.AcceptTcpClientAsync(token);
                    _ = HandleClientSessionAsync(client, token);
                }
                catch (OperationCanceledException)
                {
                    break;
                }
                catch (Exception ex)
                {
                    LogMessage?.Invoke(this, $"[SyncServer] Error accepting client: {ex.Message}");
                }
            }
        }

        private async Task HandleClientSessionAsync(TcpClient client, CancellationToken token)
        {
            var endPoint = client.Client.RemoteEndPoint?.ToString() ?? "Unknown";
            LogMessage?.Invoke(this, $"[SyncServer] Android connected from {endPoint}");
            ClientConnectionChanged?.Invoke(this, true);

            using var networkStream = client.GetStream();
            using var reader = new StreamReader(networkStream, Encoding.UTF8);
            using var writer = new StreamWriter(networkStream, new UTF8Encoding(false)) { AutoFlush = true };
            _activeWriters.TryAdd(writer, true);

            try
            {
                // 1. Send initial Handshake with Available Actions & Active Profile
                var handshakePayload = new
                {
                    type = "HANDSHAKE_RESPONSE",
                    serverVersion = "1.0.0",
                    deviceType = "Windows Companion",
                    activeProfile = _profileManager.ActiveProfile,
                    allProfiles = _profileManager.Profiles,
                    isPaused = _inputDispatcher.GameDetector?.IsGamePaused ?? false,
                    menuMode = _inputDispatcher.GameDetector?.CurrentMenuMode ?? "PAUSE"
                };

                string handshakeJson = JsonSerializer.Serialize(handshakePayload, _jsonOptions);
                await writer.WriteLineAsync(handshakeJson);

                // 2. Process incoming client requests
                while (!token.IsCancellationRequested && client.Connected)
                {
                    string? line = await reader.ReadLineAsync(token);
                    if (line == null) break; // Client disconnected

                    await ProcessClientMessageAsync(line, writer);
                }
            }
            catch (Exception ex)
            {
                LogMessage?.Invoke(this, $"[SyncServer] Client session error: {ex.Message}");
            }
            finally
            {
                _activeWriters.TryRemove(writer, out _);
                client.Close();
                ClientConnectionChanged?.Invoke(this, IsClientConnected);
                _inputDispatcher.ResetAllToNeutral();
                LogMessage?.Invoke(this, $"[SyncServer] Android disconnected. Inputs reset to neutral.");
            }
        }

        private async Task ProcessClientMessageAsync(string messageJson, StreamWriter writer)
        {
            try
            {
                using var doc = JsonDocument.Parse(messageJson);
                var root = doc.RootElement;
                if (!root.TryGetProperty("type", out var typeProp)) return;

                string type = typeProp.GetString() ?? "";

                switch (type)
                {
                    case "GET_PROFILES":
                        var profilesResp = new
                        {
                            type = "PROFILES_RESPONSE",
                            activeProfileId = _profileManager.ActiveProfile.ProfileId,
                            profiles = _profileManager.Profiles
                        };
                        await writer.WriteLineAsync(JsonSerializer.Serialize(profilesResp, _jsonOptions));
                        break;

                    case "SET_ACTIVE_PROFILE":
                        if (root.TryGetProperty("profileId", out var pidProp))
                        {
                            string pid = pidProp.GetString() ?? "";
                            bool success = _profileManager.SetActiveProfile(pid);
                            var ack = new { type = "SET_ACTIVE_PROFILE_ACK", success, activeProfile = _profileManager.ActiveProfile };
                            await writer.WriteLineAsync(JsonSerializer.Serialize(ack, _jsonOptions));
                        }
                        break;

                    case "SAVE_PROFILE":
                        if (root.TryGetProperty("profile", out var profProp))
                        {
                            var updatedProfile = JsonSerializer.Deserialize<ControllerProfile>(profProp.GetRawText(), _jsonOptions);
                            if (updatedProfile != null)
                            {
                                _profileManager.SaveProfile(updatedProfile);
                                LogMessage?.Invoke(this, $"[SyncServer] Saved updated profile '{updatedProfile.ProfileName}' from Android.");
                                var saveAck = new { type = "SAVE_PROFILE_ACK", success = true, profileId = updatedProfile.ProfileId };
                                await writer.WriteLineAsync(JsonSerializer.Serialize(saveAck, _jsonOptions));
                            }
                        }
                        break;

                    case "TEST_ACTION_INPUT":
                        if (root.TryGetProperty("actionId", out var actIdProp) && root.TryGetProperty("isPressed", out var pressProp))
                        {
                            string actionId = actIdProp.GetString() ?? "";
                            bool isPressed = pressProp.GetBoolean();
                            _inputDispatcher.DispatchDigitalInput(actionId, isPressed);
                            LogMessage?.Invoke(this, $"[SyncServer] Test input: action '{actionId}', state: {isPressed}");
                        }
                        break;

                    case "SPOTIFY_COMMAND":
                        if (root.TryGetProperty("command", out var cmdProp) && Spotify != null)
                        {
                            string cmd = cmdProp.GetString() ?? "";
                            _ = Spotify.SendPlaybackCommandAsync(cmd);
                        }
                        break;

                    case "SPOTIFY_SWITCH_ACCOUNT":
                        if (root.TryGetProperty("accountIndex", out var acctProp) && Spotify != null)
                        {
                            int acct = acctProp.GetInt32();
                            Spotify.SwitchActiveAccount(acct);
                        }
                        break;

                    case "RADIO_NEXT_STATION":
                        if (Radio != null)
                        {
                            _ = Radio.NextStationAsync();
                        }
                        break;

                    case "SET_PAUSE_STATE":
                        if (root.TryGetProperty("isPaused", out var pauseProp))
                        {
                            bool isPaused = pauseProp.GetBoolean();
                            string mode = root.TryGetProperty("menuMode", out var modeProp) ? (modeProp.GetString() ?? "PAUSE") : "PAUSE";
                            _inputDispatcher.GameDetector?.SetPauseState(isPaused, mode);
                        }
                        break;

                    case "PING":
                        var pong = new { type = "PONG", timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() };
                        await writer.WriteLineAsync(JsonSerializer.Serialize(pong, _jsonOptions));
                        break;
                }
            }
            catch (Exception ex)
            {
                LogMessage?.Invoke(this, $"[SyncServer] Error processing message: {ex.Message}");
            }
        }

        public void Stop()
        {
            _cts?.Cancel();
            _listener?.Stop();
            _listener = null;
        }

        public void Dispose()
        {
            Stop();
            _cts?.Dispose();
        }
    }
}

