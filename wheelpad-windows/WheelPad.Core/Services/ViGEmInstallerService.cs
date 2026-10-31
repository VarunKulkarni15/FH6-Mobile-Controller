using System;
using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Runtime.Versioning;
using System.Threading.Tasks;
using Microsoft.Win32;

namespace WheelPad.Core.Services
{
    [SupportedOSPlatform("windows")]
    public static class ViGEmInstallerService
    {
        private const string VigemDownloadUrl = "https://github.com/nefarius/ViGEmBus/releases/download/v1.22.0/ViGEmBus_1.22.0_x64_x86_arm64.exe";

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

        public static async Task<bool> DownloadAndInstallDriverAsync(Action<string>? statusCallback = null)
        {
            if (IsDriverInstalled())
            {
                statusCallback?.Invoke("VIGEMBUS ALREADY INSTALLED");
                return true;
            }

            statusCallback?.Invoke("DOWNLOADING VIGEMBUS DRIVER...");

            string tempPath = Path.Combine(Path.GetTempPath(), "ViGEmBus_Setup.exe");

            try
            {
                using (var httpClient = new HttpClient())
                {
                    httpClient.Timeout = TimeSpan.FromMinutes(2);
                    using var response = await httpClient.GetAsync(VigemDownloadUrl, HttpCompletionOption.ResponseHeadersRead);
                    response.EnsureSuccessStatusCode();

                    using var fs = new FileStream(tempPath, FileMode.Create, FileAccess.Write, FileShare.None);
                    await response.Content.CopyToAsync(fs);
                }

                statusCallback?.Invoke("INSTALLING VIGEMBUS DRIVER...");

                var startInfo = new ProcessStartInfo
                {
                    FileName = tempPath,
                    Arguments = "/passive /norestart",
                    UseShellExecute = true,
                    Verb = "runas" // Windows UAC Elevation prompt
                };

                using var process = Process.Start(startInfo);
                if (process != null)
                {
                    await process.WaitForExitAsync();

                    // Wait up to 5 seconds for driver service registry key to register
                    for (int i = 0; i < 10; i++)
                    {
                        if (IsDriverInstalled())
                        {
                            statusCallback?.Invoke("VIGEMBUS INSTALLED SUCCESSFULLY");
                            return true;
                        }
                        await Task.Delay(500);
                    }
                }

                return IsDriverInstalled();
            }
            catch (Exception ex)
            {
                statusCallback?.Invoke($"INSTALL ERROR: {ex.Message}");
                return false;
            }
            finally
            {
                try
                {
                    if (File.Exists(tempPath))
                    {
                        File.Delete(tempPath);
                    }
                }
                catch { }
            }
        }
    }
}
