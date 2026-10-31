using System;
using System.IO;
using System.Diagnostics;
using System.Runtime.Versioning;
using Microsoft.Win32;

namespace WheelPad.Core.Services
{
    public class PlatformStatus
    {
        public bool IsSteamInstalled { get; set; }
        public bool IsXboxAppInstalled { get; set; }
        public bool IsEpicGamesInstalled { get; set; }
        public bool IsViGEmBusInstalled { get; set; }

        public string SteamText => IsSteamInstalled ? "STEAM: READY" : "STEAM: NOT DETECTED";
        public string XboxText => IsXboxAppInstalled ? "XBOX / MS STORE: READY" : "XBOX: NOT DETECTED";
        public string EpicText => IsEpicGamesInstalled ? "EPIC GAMES: READY" : "EPIC: NOT DETECTED";
        public string ViGEmText => IsViGEmBusInstalled ? "ViGEmBUS: INSTALLED" : "ViGEmBUS: MISSING";

        public string SummaryText
        {
            get
            {
                var platforms = new System.Collections.Generic.List<string>();
                if (IsSteamInstalled) platforms.Add("Steam");
                if (IsXboxAppInstalled) platforms.Add("Xbox / MS Store");
                if (IsEpicGamesInstalled) platforms.Add("Epic Games");

                if (platforms.Count > 0)
                {
                    return $"Detected: {string.Join(", ", platforms)}. Xbox 360 emulation works seamlessly with all of them!";
                }
                return "Sim Rig ready: Works with Steam, Xbox Game Pass, and Epic Games Store.";
            }
        }
    }

    [SupportedOSPlatform("windows")]
    public static class PlatformDetector
    {
        public static PlatformStatus CheckPlatforms()
        {
            var status = new PlatformStatus();

            // 1. Steam Check
            try
            {
                using var key = Registry.CurrentUser.OpenSubKey(@"Software\Valve\Steam");
                if (key != null)
                {
                    status.IsSteamInstalled = true;
                }
                else
                {
                    string defaultSteam = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Steam", "steam.exe");
                    status.IsSteamInstalled = File.Exists(defaultSteam) || Process.GetProcessesByName("steam").Length > 0;
                }
            }
            catch
            {
                status.IsSteamInstalled = false;
            }

            // 2. Xbox App / Microsoft Gaming Services Check
            try
            {
                using var key = Registry.LocalMachine.OpenSubKey(@"SOFTWARE\Microsoft\GamingServices");
                if (key != null)
                {
                    status.IsXboxAppInstalled = true;
                }
                else
                {
                    // Check user Appx package registry or Xbox app process
                    using var uKey = Registry.CurrentUser.OpenSubKey(@"Software\Classes\Local Settings\Software\Microsoft\Windows\CurrentVersion\AppModel\SystemAppData\Microsoft.GamingApp_8wekyb3d8bbwe");
                    status.IsXboxAppInstalled = uKey != null || Process.GetProcessesByName("XboxPcApp").Length > 0 || Process.GetProcessesByName("GamingServices").Length > 0;
                }
            }
            catch
            {
                status.IsXboxAppInstalled = true; // Windows 10/11 includes MS Store by default
            }

            // 3. Epic Games Launcher Check
            try
            {
                using var key = Registry.LocalMachine.OpenSubKey(@"SOFTWARE\EpicGames\EOS") 
                             ?? Registry.LocalMachine.OpenSubKey(@"SOFTWARE\WOW6432Node\Epic Games\EpicGamesLauncher");
                if (key != null)
                {
                    status.IsEpicGamesInstalled = true;
                }
                else
                {
                    string defaultEpic = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Epic Games", "Launcher", "Portal", "Binaries", "Win64", "EpicGamesLauncher.exe");
                    status.IsEpicGamesInstalled = File.Exists(defaultEpic) || Process.GetProcessesByName("EpicGamesLauncher").Length > 0;
                }
            }
            catch
            {
                status.IsEpicGamesInstalled = false;
            }

            // 4. ViGEmBus Driver Check
            status.IsViGEmBusInstalled = ViGEmInstallerService.IsDriverInstalled();

            return status;
        }
    }
}
