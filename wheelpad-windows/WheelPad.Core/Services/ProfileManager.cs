using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using WheelPad.Core.Models;

namespace WheelPad.Core.Services
{
    public class ProfileManager
    {
        private readonly string _profilesDirectory;
        private readonly JsonSerializerOptions _jsonOptions;
        private readonly List<ControllerProfile> _profiles = new();
        private ControllerProfile _activeProfile;

        public event EventHandler<ControllerProfile>? ActiveProfileChanged;
        public event EventHandler<List<ControllerProfile>>? ProfilesUpdated;

        public ProfileManager(string? baseDirectory = null)
        {
            _profilesDirectory = baseDirectory ?? Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                "WheelPad",
                "Profiles");

            _jsonOptions = new JsonSerializerOptions
            {
                WriteIndented = true,
                PropertyNameCaseInsensitive = true
            };

            Directory.CreateDirectory(_profilesDirectory);
            LoadProfiles();
            _activeProfile = _profiles.FirstOrDefault(p => p.IsDefault) ?? _profiles.First();
        }

        public IReadOnlyList<ControllerProfile> Profiles => _profiles.AsReadOnly();
        public ControllerProfile ActiveProfile => _activeProfile;

        public void LoadProfiles()
        {
            _profiles.Clear();
            var files = Directory.GetFiles(_profilesDirectory, "*.json");

            if (files.Length == 0)
            {
                SeedDefaultProfiles();
                return;
            }

            foreach (var file in files)
            {
                try
                {
                    string json = File.ReadAllText(file);
                    var profile = JsonSerializer.Deserialize<ControllerProfile>(json, _jsonOptions);
                    if (profile != null)
                    {
                        _profiles.Add(profile);
                    }
                }
                catch (Exception ex)
                {
                    Console.Error.WriteLine($"[ProfileManager] Error loading profile from {file}: {ex.Message}");
                }
            }

            if (_profiles.Count == 0)
            {
                SeedDefaultProfiles();
            }
        }

        public void SaveProfile(ControllerProfile profile)
        {
            profile.LastModifiedUtc = DateTime.UtcNow;
            string filePath = Path.Combine(_profilesDirectory, $"{SanitizeFileName(profile.ProfileId)}.json");
            string json = JsonSerializer.Serialize(profile, _jsonOptions);
            File.WriteAllText(filePath, json);

            var existingIndex = _profiles.FindIndex(p => p.ProfileId == profile.ProfileId);
            if (existingIndex >= 0)
            {
                _profiles[existingIndex] = profile;
            }
            else
            {
                _profiles.Add(profile);
            }

            if (_activeProfile?.ProfileId == profile.ProfileId)
            {
                _activeProfile = profile;
                ActiveProfileChanged?.Invoke(this, _activeProfile);
            }

            ProfilesUpdated?.Invoke(this, _profiles);
        }

        public bool SetActiveProfile(string profileId)
        {
            var match = _profiles.FirstOrDefault(p => p.ProfileId == profileId);
            if (match == null) return false;

            _activeProfile = match;
            ActiveProfileChanged?.Invoke(this, _activeProfile);
            return true;
        }

        public ControllerProfile CreateProfile(string name, string? baseProfileId = null)
        {
            ControllerProfile newProfile;
            if (!string.IsNullOrEmpty(baseProfileId))
            {
                var baseProfile = _profiles.FirstOrDefault(p => p.ProfileId == baseProfileId);
                newProfile = baseProfile != null ? baseProfile.Clone() : new ControllerProfile();
            }
            else
            {
                newProfile = new ControllerProfile();
            }

            newProfile.ProfileId = Guid.NewGuid().ToString("N");
            newProfile.ProfileName = name;
            newProfile.IsDefault = false;
            SaveProfile(newProfile);
            return newProfile;
        }

        public bool DeleteProfile(string profileId)
        {
            var profile = _profiles.FirstOrDefault(p => p.ProfileId == profileId);
            if (profile == null || profile.IsDefault) return false; // Prevent deleting default fallback

            string filePath = Path.Combine(_profilesDirectory, $"{SanitizeFileName(profileId)}.json");
            if (File.Exists(filePath))
            {
                File.Delete(filePath);
            }

            _profiles.Remove(profile);

            if (_activeProfile.ProfileId == profileId)
            {
                _activeProfile = _profiles.FirstOrDefault(p => p.IsDefault) ?? _profiles.First();
                ActiveProfileChanged?.Invoke(this, _activeProfile);
            }

            ProfilesUpdated?.Invoke(this, _profiles);
            return true;
        }

        public void ImportProfileFromJson(string json)
        {
            var profile = JsonSerializer.Deserialize<ControllerProfile>(json, _jsonOptions);
            if (profile != null)
            {
                profile.ProfileId = Guid.NewGuid().ToString("N");
                profile.ProfileName += " (Imported)";
                profile.IsDefault = false;
                SaveProfile(profile);
            }
        }

        public string ExportProfileToJson(string profileId)
        {
            var profile = _profiles.FirstOrDefault(p => p.ProfileId == profileId) ?? _activeProfile;
            return JsonSerializer.Serialize(profile, _jsonOptions);
        }

        private void SeedDefaultProfiles()
        {
            // Try loading from shared-protocol/default_profiles.json if available
            string sharedDefaultsPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "default_profiles.json");
            if (File.Exists(sharedDefaultsPath))
            {
                try
                {
                    string json = File.ReadAllText(sharedDefaultsPath);
                    var defaults = JsonSerializer.Deserialize<List<ControllerProfile>>(json, _jsonOptions);
                    if (defaults != null && defaults.Count > 0)
                    {
                        foreach (var def in defaults)
                        {
                            SaveProfile(def);
                        }
                        return;
                    }
                }
                catch { }
            }

            // Fallback hard-coded initial standard profile
            var standard = new ControllerProfile
            {
                ProfileId = "profile_standard",
                ProfileName = "Standard Racing",
                IsDefault = true,
                SteeringConfig = new SteeringConfig
                {
                    MaxRotationDegrees = 540,
                    DeadzonePercent = 0.02f,
                    GammaCurve = 1.15f
                }
            };

            // Add standard actions
            standard.Actions.Add(new GameAction { Id = "steer", Name = "Steering", InputType = InputType.AnalogAxis, BindingType = BindingType.XboxController, XboxTarget = XboxButton.LeftThumbX });
            standard.Actions.Add(new GameAction { Id = "throttle", Name = "Accelerator", InputType = InputType.AnalogTrigger, BindingType = BindingType.XboxController, XboxTarget = XboxButton.RightTrigger, KeyboardKeyCode = 87, KeyboardKeyName = "W" });
            standard.Actions.Add(new GameAction { Id = "brake", Name = "Brake / Reverse", InputType = InputType.AnalogTrigger, BindingType = BindingType.XboxController, XboxTarget = XboxButton.LeftTrigger, KeyboardKeyCode = 83, KeyboardKeyName = "S" });
            standard.Actions.Add(new GameAction { Id = "handbrake", Name = "Handbrake (E-Brake)", InputType = InputType.DigitalButton, BindingType = BindingType.Composite, XboxTarget = XboxButton.A, KeyboardKeyCode = 32, KeyboardKeyName = "Space", Behavior = InputBehavior.Hold });
            standard.Actions.Add(new GameAction { Id = "rewind", Name = "Rewind", InputType = InputType.DigitalButton, BindingType = BindingType.XboxController, XboxTarget = XboxButton.Y, KeyboardKeyCode = 82, KeyboardKeyName = "R" });
            standard.Actions.Add(new GameAction { Id = "camera", Name = "Camera", InputType = InputType.DigitalButton, BindingType = BindingType.XboxController, XboxTarget = XboxButton.RightBumper, KeyboardKeyCode = 67, KeyboardKeyName = "C" });

            // Add standard layout
            standard.Layout.Add(new TouchControlElement { ElementId = "wheel", ActionId = "steer", Label = "STEER", Shape = ControlShape.CircleWheel, NormalizedX = 0.04f, NormalizedY = 0.15f, NormalizedWidth = 0.42f, NormalizedHeight = 0.75f, AccentColorHex = "#00E5FF" });
            standard.Layout.Add(new TouchControlElement { ElementId = "brake", ActionId = "brake", Label = "BRAKE", Shape = ControlShape.VerticalPedal, NormalizedX = 0.70f, NormalizedY = 0.22f, NormalizedWidth = 0.12f, NormalizedHeight = 0.65f, AccentColorHex = "#FF1744" });
            standard.Layout.Add(new TouchControlElement { ElementId = "gas", ActionId = "throttle", Label = "GAS", Shape = ControlShape.VerticalPedal, NormalizedX = 0.85f, NormalizedY = 0.22f, NormalizedWidth = 0.12f, NormalizedHeight = 0.65f, AccentColorHex = "#00E676" });
            standard.Layout.Add(new TouchControlElement { ElementId = "handbrake", ActionId = "handbrake", Label = "E-BRAKE", Shape = ControlShape.WideBar, NormalizedX = 0.68f, NormalizedY = 0.88f, NormalizedWidth = 0.30f, NormalizedHeight = 0.10f, AccentColorHex = "#FFD600" });

            SaveProfile(standard);
        }

        private static string SanitizeFileName(string name)
        {
            var invalid = Path.GetInvalidFileNameChars();
            return new string(name.Where(ch => !invalid.Contains(ch)).ToArray());
        }
    }
}

