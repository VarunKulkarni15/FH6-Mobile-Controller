using System;
using System.Diagnostics;
using System.Runtime.Versioning;
using Microsoft.Win32;

namespace WheelPad.Core.Services
{
    [SupportedOSPlatform("windows")]
    public static class ViGEmInstallerService
    {
        public const string VigemDownloadUrl = "https://github.com/nefarius/ViGEmBus/releases/latest";

        public static bool IsDriverInstalled()
        {
            try
            {
                using var key = Registry.LocalMachine.OpenSubKey(@"SYSTEM\CurrentControlSet\Services\ViGEmBus");
                return key != null;
            }
            catch
            {
                return false;
            }
        }

        public static void OpenDriverDownloadPage()
        {
            try
            {
                Process.Start(new ProcessStartInfo(VigemDownloadUrl)
                {
                    UseShellExecute = true
                });
            }
            catch { }
        }
    }
}
