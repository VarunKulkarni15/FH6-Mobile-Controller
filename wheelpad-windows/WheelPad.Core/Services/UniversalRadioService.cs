using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading;
using System.Threading.Tasks;

namespace WheelPad.Core.Services
{
    public class RadioStation
    {
        [JsonPropertyName("id")]
        public string Id { get; set; } = "";

        [JsonPropertyName("name")]
        public string Name { get; set; } = "";

        [JsonPropertyName("genre")]
        public string? Genre { get; set; }
    }

    public class UniversalRadioStatus
    {
        [JsonPropertyName("isInstalled")]
        public bool IsInstalled { get; set; }

        [JsonPropertyName("currentStation")]
        public string CurrentStation { get; set; } = "Radio Off";

        [JsonPropertyName("currentTrack")]
        public string? CurrentTrack { get; set; }

        [JsonPropertyName("volume")]
        public int Volume { get; set; } = 100;
    }

    public class UniversalRadioService : IDisposable
    {
        public const string DefaultModUrl = "http://localhost:8420";
        private readonly HttpClient _httpClient;
        private readonly Timer _healthCheckTimer;

        public bool IsModDetected { get; private set; }
        public UniversalRadioStatus Status { get; private set; } = new();
        public List<RadioStation> Stations { get; private set; } = new();

        public event EventHandler<UniversalRadioStatus>? StatusUpdated;
        public event EventHandler<string>? LogMessage;

        public UniversalRadioService()
        {
            _httpClient = new HttpClient { Timeout = TimeSpan.FromSeconds(2) };
            _healthCheckTimer = new Timer(HealthCheckCallback, null, 2000, 5000);
        }

        private async void HealthCheckCallback(object? state)
        {
            await CheckModAvailabilityAsync();
        }

        public async Task<bool> CheckModAvailabilityAsync()
        {
            try
            {
                var resp = await _httpClient.GetAsync($"{DefaultModUrl}/api/status");
                if (resp.IsSuccessStatusCode)
                {
                    string json = await resp.Content.ReadAsStringAsync();
                    using var doc = JsonDocument.Parse(json);
                    var root = doc.RootElement;

                    Status.IsInstalled = true;
                    if (root.TryGetProperty("station", out var stProp))
                        Status.CurrentStation = stProp.GetString() ?? "Unknown";
                    if (root.TryGetProperty("track", out var trProp))
                        Status.CurrentTrack = trProp.GetString();

                    if (!IsModDetected)
                    {
                        IsModDetected = true;
                        LogMessage?.Invoke(this, "[UniversalRadio] Detected FH6 Universal Radio mod on port 8420!");
                        await FetchStationsAsync();
                    }

                    StatusUpdated?.Invoke(this, Status);
                    return true;
                }
            }
            catch
            {
                // Mod not running / not installed
                if (IsModDetected)
                {
                    IsModDetected = false;
                    Status.IsInstalled = false;
                    Status.CurrentStation = "Radio Off";
                    StatusUpdated?.Invoke(this, Status);
                }
            }

            return false;
        }

        public async Task<List<RadioStation>> FetchStationsAsync()
        {
            if (!IsModDetected) return Stations;

            try
            {
                var resp = await _httpClient.GetAsync($"{DefaultModUrl}/api/stations");
                if (resp.IsSuccessStatusCode)
                {
                    string json = await resp.Content.ReadAsStringAsync();
                    var list = JsonSerializer.Deserialize<List<RadioStation>>(json);
                    if (list != null)
                    {
                        Stations = list;
                        return Stations;
                    }
                }
            }
            catch { }

            return Stations;
        }

        public async Task<bool> NextStationAsync()
        {
            if (!IsModDetected) return false;
            try
            {
                var resp = await _httpClient.PostAsync($"{DefaultModUrl}/api/station/next", null);
                await CheckModAvailabilityAsync();
                return resp.IsSuccessStatusCode;
            }
            catch
            {
                return false;
            }
        }

        public void Dispose()
        {
            _healthCheckTimer.Dispose();
            _httpClient.Dispose();
        }
    }
}

