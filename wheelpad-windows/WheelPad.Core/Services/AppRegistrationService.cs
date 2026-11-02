using System;
using System.Diagnostics;
using System.IO;
using Microsoft.Win32;

namespace WheelPad.Core.Services;

/// <summary>
/// Handles integration with Windows OS:
/// - Registers Start Menu shortcuts so Windows Search (Win+S / Win key) indexes and surfaces the app.
/// - Registers App Paths so "fh6-controller-windows" or "fh6" can be run from Win+R.
/// - Registers in HKCU Uninstall so Windows Settings -> Installed Apps lists the suite with icon and metadata.
/// </summary>
public static class AppRegistrationService
{
    private const string AppTitle = "Forza Horizon Controller";
    private const string AppDescription = "Sim Racing Controller Companion & Telemetry Suite for PC";
    private const string PublisherName = "Varun Kulkarni";
    private const string AppVersion = "1.2.0";

    public static void EnsureRegistered()
    {
        try
        {
            string? currentExe = Process.GetCurrentProcess().MainModule?.FileName;
            if (string.IsNullOrEmpty(currentExe) || !File.Exists(currentExe) || currentExe.EndsWith("dotnet.exe", StringComparison.OrdinalIgnoreCase))
                return;

            string exeDir = Path.GetDirectoryName(currentExe) ?? "";

            // 1. Create / Refresh Start Menu Shortcut for Windows Search indexing
            string startMenuDir = Environment.GetFolderPath(Environment.SpecialFolder.Programs);
            if (Directory.Exists(startMenuDir))
            {
                string shortcutPath = Path.Combine(startMenuDir, $"{AppTitle}.lnk");
                CreateWindowsShortcut(shortcutPath, currentExe, exeDir, AppDescription);
            }

            // 2. Register Windows App Paths (Win+R / Start Menu command indexing)
            try
            {
                using var appPathsKey = Registry.CurrentUser.CreateSubKey(@"Software\Microsoft\Windows\CurrentVersion\App Paths\fh6-controller-windows.exe");
                if (appPathsKey != null)
                {
                    appPathsKey.SetValue("", currentExe);
                    appPathsKey.SetValue("Path", exeDir);
                }

                using var fh6Key = Registry.CurrentUser.CreateSubKey(@"Software\Microsoft\Windows\CurrentVersion\App Paths\fh6.exe");
                if (fh6Key != null)
                {
                    fh6Key.SetValue("", currentExe);
                    fh6Key.SetValue("Path", exeDir);
                }
            }
            catch { }

            // 3. Register in Windows Installed Apps (Settings -> Apps & Windows Search priority)
            try
            {
                using var uninstallKey = Registry.CurrentUser.CreateSubKey(@"Software\Microsoft\Windows\CurrentVersion\Uninstall\ForzaHorizonController");
                if (uninstallKey != null)
                {
                    uninstallKey.SetValue("DisplayName", AppTitle);
                    uninstallKey.SetValue("DisplayVersion", AppVersion);
                    uninstallKey.SetValue("Publisher", PublisherName);
                    uninstallKey.SetValue("DisplayIcon", $"{currentExe},0");
                    uninstallKey.SetValue("InstallLocation", exeDir);
                    uninstallKey.SetValue("HelpLink", "https://github.com/VarunKulkarni15/FH6-Mobile-Controller");
                    uninstallKey.SetValue("URLInfoAbout", "https://horizoncontro.varunkulkarni.dpdns.org");
                    uninstallKey.SetValue("NoModify", 1, RegistryValueKind.DWord);
                    uninstallKey.SetValue("NoRepair", 1, RegistryValueKind.DWord);
                }
            }
            catch { }
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"[AppRegistrationService] Notice: {ex.Message}");
        }
    }

    private static void CreateWindowsShortcut(string shortcutPath, string targetExe, string workingDir, string description)
    {
        try
        {
            Type? shellType = Type.GetTypeFromProgID("WScript.Shell");
            if (shellType == null)
                return;

            dynamic shell = Activator.CreateInstance(shellType)!;
            dynamic shortcut = shell.CreateShortcut(shortcutPath);
            shortcut.TargetPath = targetExe;
            shortcut.WorkingDirectory = workingDir;
            shortcut.Description = description;
            shortcut.IconLocation = $"{targetExe},0";
            shortcut.Save();
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"[AppRegistrationService] Shortcut creation notice: {ex.Message}");
        }
    }
}

