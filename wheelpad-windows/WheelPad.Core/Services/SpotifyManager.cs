using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading;
using System.Threading.Tasks;

namespace WheelPad.Core.Services
{
    public class SpotifyAccountSession
    {
        [JsonPropertyName("accountId")]
        public string AccountId { get; set; } = "account_1";

        [JsonPropertyName("displayName")]
        public string DisplayName { get; set; } = "Spotify Account 1";

        [JsonPropertyName("accessToken")]
        public string? AccessToken { get; set; }

        [JsonPropertyName("refreshToken")]
        public string? RefreshToken { get; set; }

        [JsonPropertyName("tokenExpiresUtc")]
        public DateTime? TokenExpiresUtc { get; set; }

        [JsonPropertyName("isAuthorized")]
        public bool IsAuthorized => !string.IsNullOrEmpty(RefreshToken);
    }

    public class SpotifyTrackState
    {
        [JsonPropertyName("trackName")]
        public string TrackName { get; set; } = "No Track Playing";

        [JsonPropertyName("artistName")]
        public string ArtistName { get; set; } = "";

        [JsonPropertyName("albumArtUrl")]
        public string? AlbumArtUrl { get; set; }

        [JsonPropertyName("isPlaying")]
        public bool IsPlaying { get; set; }

        [JsonPropertyName("progressMs")]
        public long ProgressMs { get; set; }

        [JsonPropertyName("durationMs")]
        public long DurationMs { get; set; }

        [JsonPropertyName("activeAccountIndex")]
        public int ActiveAccountIndex { get; set; } = 1;

        [JsonPropertyName("accountName")]
        public string AccountName { get; set; } = "";

        [JsonPropertyName("deviceName")]
        public string? DeviceName { get; set; }
    }

    public class SpotifyManager : IDisposable
    {
        public const string DefaultClientId = "b91f1b6237a4487b8db0bb6b7e6f8ad1"; // WheelPad Open App Client ID
        private const string TokenEndpoint = "https://accounts.spotify.com/api/token";
        private const string PlayerEndpoint = "https://api.spotify.com/v1/me/player";

        private readonly HttpClient _httpClient;
        private readonly string _tokenStoragePath;
        private readonly JsonSerializerOptions _jsonOptions;
        private readonly Timer _pollTimer;

        public SpotifyAccountSession Account1 { get; private set; } = new() { AccountId = "account_1", DisplayName = "Primary Account" };
        public SpotifyAccountSession Account2 { get; private set; } = new() { AccountId = "account_2", DisplayName = "Secondary Account" };
        public int ActiveAccountIndex { get; private set; } = 1;

        public SpotifyTrackState CurrentTrack { get; private set; } = new();

        public event EventHandler<SpotifyTrackState>? TrackStateUpdated;
        public event EventHandler<string>? LogMessage;

        public SpotifyManager(string? storageDir = null)
        {
            _httpClient = new HttpClient();
            _jsonOptions = new JsonSerializerOptions { WriteIndented = true, PropertyNameCaseInsensitive = true };

            string baseDir = storageDir ?? Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                "WheelPad");
            Directory.CreateDirectory(baseDir);
            _tokenStoragePath = Path.Combine(baseDir, "spotify_sessions.json");

            LoadSessions();

            // Poll player status every 2 seconds
            _pollTimer = new Timer(PollPlaybackCallback, null, 1500, 2000);
        }

        public void SwitchActiveAccount(int accountIndex)
        {
            if (accountIndex != 1 && accountIndex != 2) return;
            ActiveAccountIndex = accountIndex;
            LogMessage?.Invoke(this, $"[Spotify] Switched to Account {accountIndex} ({GetActiveSession().DisplayName})");
            _ = UpdateCurrentPlaybackAsync();
        }

        public SpotifyAccountSession GetActiveSession()
        {
            return ActiveAccountIndex == 1 ? Account1 : Account2;
        }

        public async Task<bool> SendPlaybackCommandAsync(string command)
        {
            var session = GetActiveSession();
            if (!session.IsAuthorized)
            {
                // Fallback to Windows Media Keys
                SendWindowsMediaKey(command);
                return true;
            }

            string? token = await GetValidAccessTokenAsync(session);
            if (string.IsNullOrEmpty(token))
            {
                SendWindowsMediaKey(command);
                return true;
            }

            try
            {
                using var req = new HttpRequestMessage();
                req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);

                switch (command.ToLowerInvariant())
                {
                    case "play":
                        req.Method = HttpMethod.Put;
                        req.RequestUri = new Uri($"{PlayerEndpoint}/play");
                        break;
                    case "pause":
                        req.Method = HttpMethod.Put;
                        req.RequestUri = new Uri($"{PlayerEndpoint}/pause");
                        break;
                    case "next":
                        req.Method = HttpMethod.Post;
                        req.RequestUri = new Uri($"{PlayerEndpoint}/next");
                        break;
                    case "previous":
                        req.Method = HttpMethod.Post;
                        req.RequestUri = new Uri($"{PlayerEndpoint}/previous");
                        break;
                    default:
                        return false;
                }

                var resp = await _httpClient.SendAsync(req);
                await Task.Delay(300); // Allow Spotify backend to update state
                await UpdateCurrentPlaybackAsync();
                return resp.IsSuccessStatusCode;
            }
            catch (Exception ex)
            {
                LogMessage?.Invoke(this, $"[Spotify] Command error: {ex.Message}. Falling back to media keys.");
                SendWindowsMediaKey(command);
                return false;
            }
        }

        private async void PollPlaybackCallback(object? state)
        {
            await UpdateCurrentPlaybackAsync();
        }

        public async Task UpdateCurrentPlaybackAsync()
        {
            var session = GetActiveSession();
            if (!session.IsAuthorized) return;

            string? token = await GetValidAccessTokenAsync(session);
            if (string.IsNullOrEmpty(token)) return;

            try
            {
                using var req = new HttpRequestMessage(HttpMethod.Get, PlayerEndpoint);
                req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);

                var resp = await _httpClient.SendAsync(req);
                if (resp.StatusCode == HttpStatusCode.NoContent)
                {
                    CurrentTrack.IsPlaying = false;
                    CurrentTrack.TrackName = "Paused / Idle";
                    TrackStateUpdated?.Invoke(this, CurrentTrack);
                    return;
                }

                if (!resp.IsSuccessStatusCode) return;

                string json = await resp.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(json);
                var root = doc.RootElement;

                var track = new SpotifyTrackState
                {
                    ActiveAccountIndex = ActiveAccountIndex,
                    AccountName = session.DisplayName
                };

                if (root.TryGetProperty("is_playing", out var isPlayingProp))
                    track.IsPlaying = isPlayingProp.GetBoolean();

                if (root.TryGetProperty("progress_ms", out var progProp))
                    track.ProgressMs = progProp.GetInt64();

                if (root.TryGetProperty("device", out var devProp) && devProp.TryGetProperty("name", out var devNameProp))
                    track.DeviceName = devNameProp.GetString();

                if (root.TryGetProperty("item", out var itemProp) && itemProp.ValueKind == JsonValueKind.Object)
                {
                    if (itemProp.TryGetProperty("name", out var nameProp))
                        track.TrackName = nameProp.GetString() ?? "Unknown";

                    if (itemProp.TryGetProperty("duration_ms", out var durProp))
                        track.DurationMs = durProp.GetInt64();

                    if (itemProp.TryGetProperty("artists", out var artistsProp) && artistsProp.GetArrayLength() > 0)
                    {
                        var firstArtist = artistsProp[0];
                        if (firstArtist.TryGetProperty("name", out var artNameProp))
                            track.ArtistName = artNameProp.GetString() ?? "";
                    }

                    if (itemProp.TryGetProperty("album", out var albumProp) && albumProp.TryGetProperty("images", out var imagesProp) && imagesProp.GetArrayLength() > 0)
                    {
                        var img = imagesProp[0];
                        if (img.TryGetProperty("url", out var urlProp))
                            track.AlbumArtUrl = urlProp.GetString();
                    }
                }

                CurrentTrack = track;
                TrackStateUpdated?.Invoke(this, CurrentTrack);
            }
            catch { }
        }

        private async Task<string?> GetValidAccessTokenAsync(SpotifyAccountSession session)
        {
            if (string.IsNullOrEmpty(session.RefreshToken)) return null;

            if (session.AccessToken != null && session.TokenExpiresUtc.HasValue && session.TokenExpiresUtc.Value > DateTime.UtcNow.AddMinutes(1))
            {
                return session.AccessToken;
            }

            // Refresh token via OAuth
            try
            {
                var content = new FormUrlEncodedContent(new Dictionary<string, string>
                {
                    { "grant_type", "refresh_token" },
                    { "refresh_token", session.RefreshToken },
                    { "client_id", DefaultClientId }
                });

                var resp = await _httpClient.PostAsync(TokenEndpoint, content);
                if (resp.IsSuccessStatusCode)
                {
                    string json = await resp.Content.ReadAsStringAsync();
                    using var doc = JsonDocument.Parse(json);
                    var root = doc.RootElement;

                    session.AccessToken = root.GetProperty("access_token").GetString();
                    int expiresIn = root.GetProperty("expires_in").GetInt32();
                    session.TokenExpiresUtc = DateTime.UtcNow.AddSeconds(expiresIn);

                    if (root.TryGetProperty("refresh_token", out var newRefresh))
                    {
                        session.RefreshToken = newRefresh.GetString();
                    }

                    SaveSessions();
                    return session.AccessToken;
                }
            }
            catch (Exception ex)
            {
                LogMessage?.Invoke(this, $"[Spotify] Refresh token error: {ex.Message}");
            }

            return null;
        }

        private void LoadSessions()
        {
            if (!File.Exists(_tokenStoragePath)) return;
            try
            {
                string json = File.ReadAllText(_tokenStoragePath);
                using var doc = JsonDocument.Parse(json);
                if (doc.RootElement.TryGetProperty("account1", out var a1))
                    Account1 = JsonSerializer.Deserialize<SpotifyAccountSession>(a1.GetRawText(), _jsonOptions) ?? Account1;
                if (doc.RootElement.TryGetProperty("account2", out var a2))
                    Account2 = JsonSerializer.Deserialize<SpotifyAccountSession>(a2.GetRawText(), _jsonOptions) ?? Account2;
            }
            catch { }
        }

        public void SaveSessions()
        {
            try
            {
                var payload = new { account1 = Account1, account2 = Account2 };
                string json = JsonSerializer.Serialize(payload, _jsonOptions);
                File.WriteAllText(_tokenStoragePath, json);
            }
            catch { }
        }

        #region Windows SMTC Fallback Media Keys

        private const int VK_VOLUME_MUTE = 0xAD;
        private const int VK_VOLUME_DOWN = 0xAE;
        private const int VK_VOLUME_UP = 0xAF;
        private const int VK_MEDIA_NEXT_TRACK = 0xB0;
        private const int VK_MEDIA_PREV_TRACK = 0xB1;
        private const int VK_MEDIA_PLAY_PAUSE = 0xB3;

        [System.Runtime.InteropServices.DllImport("user32.dll")]
        private static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo);

        public void SendWindowsMediaKey(string command)
        {
            byte vk = command.ToLowerInvariant() switch
            {
                "next" => (byte)VK_MEDIA_NEXT_TRACK,
                "previous" => (byte)VK_MEDIA_PREV_TRACK,
                "volume_up" => (byte)VK_VOLUME_UP,
                "volume_down" => (byte)VK_VOLUME_DOWN,
                "mute" => (byte)VK_VOLUME_MUTE,
                _ => (byte)VK_MEDIA_PLAY_PAUSE
            };

            keybd_event(vk, 0, 0, UIntPtr.Zero);
            keybd_event(vk, 0, 2, UIntPtr.Zero); // KEYEVENTF_KEYUP
        }

        #endregion

        public void Dispose()
        {
            _pollTimer.Dispose();
            _httpClient.Dispose();
        }
    }
}
