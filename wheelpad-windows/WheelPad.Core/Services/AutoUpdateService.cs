using System;
using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Reflection;
using System.Text.Json;
using System.Threading.Tasks;

namespace WheelPad.Core.Services
{
    public class VersionInfo
    {
        public int VersionCode { get; set; } = 1;
        public string VersionName { get; set; } = "1.0.0";
        public string DownloadUrl { get; set; } = "";
        public string Changelog { get; set; } = "";
    }

    public class UpdateCheckResult
    {
        public bool IsUpdateAvailable { get; set; }
        public VersionInfo? LatestVersion { get; set; }
        public string CurrentVersion { get; set; } = "1.5.0";
        public string? ErrorMessage { get; set; }
    }

    public class AutoUpdateService
    {
                private static readonly string[] VersionUrls = new[]
        {
            "https://raw.githubusercontent.com/VarunKulkarni15/FH6-Mobile-Controller/master/site/version.json",
            "https://horizoncontro.varunkulkarni.dpdns.org/version.json"
        };
        public const int CurrentVersionCode = 9;
        public const string CurrentVersionName = "1.5.0";

        private readonly HttpClient _httpClient;

        public AutoUpdateService()
        {
            _httpClient = new HttpClient { Timeout = TimeSpan.FromSeconds(8) };
            _httpClient.DefaultRequestHeaders.Add("User-Agent", "FH6-Companion-Windows/1.5.0");
        }

        public async Task<UpdateCheckResult> CheckForUpdatesAsync()
        {
            Exception? lastEx = null;
            foreach (var url in VersionUrls)
            {
                try
                {
                    var response = await _httpClient.GetStringAsync(url);
                    using var doc = JsonDocument.Parse(response);

                    if (doc.RootElement.TryGetProperty("windows", out var winElem))
                    {
                        int remoteCode = winElem.GetProperty("versionCode").GetInt32();
                        string remoteName = winElem.GetProperty("versionName").GetString() ?? "1.5.0";
                        string downloadUrl = winElem.GetProperty("downloadUrl").GetString() ?? "";
                        string changelog = winElem.TryGetProperty("changelog", out var cl) ? cl.GetString() ?? "" : "";

                        var info = new VersionInfo
                        {
                            VersionCode = remoteCode,
                            VersionName = remoteName,
                            DownloadUrl = downloadUrl,
                            Changelog = changelog
                        };

                        bool isNewer = remoteCode > CurrentVersionCode;

                        return new UpdateCheckResult
                        {
                            IsUpdateAvailable = isNewer,
                            LatestVersion = info,
                            CurrentVersion = CurrentVersionName
                        };
                    }
                }
                catch (Exception ex)
                {
                    lastEx = ex;
                }
            }

            return new UpdateCheckResult
            {
                IsUpdateAvailable = false,
                CurrentVersion = CurrentVersionName,
                ErrorMessage = lastEx?.Message
            };
        }

        public async Task<bool> DownloadAndApplyUpdateAsync(string downloadUrl, Action<int> onProgress)
        {
            try
            {
                string tempDir = Path.GetTempPath();
                string tempExe = Path.Combine(tempDir, "fh6-companion-update.exe");
                string updaterBat = Path.Combine(tempDir, "fh6-apply-update.bat");

                using (var response = await _httpClient.GetAsync(downloadUrl, HttpCompletionOption.ResponseHeadersRead))
                {
                    response.EnsureSuccessStatusCode();
                    long? totalBytes = response.Content.Headers.ContentLength;

                    using var sourceStream = await response.Content.ReadAsStreamAsync();
                    using var targetStream = new FileStream(tempExe, FileMode.Create, FileAccess.Write, FileShare.None, 8192, true);

                    byte[] buffer = new byte[16384];
                    long totalRead = 0;
                    int bytesRead;

                    while ((bytesRead = await sourceStream.ReadAsync(buffer, 0, buffer.Length)) > 0)
                    {
                        await targetStream.WriteAsync(buffer, 0, bytesRead);
                        totalRead += bytesRead;

                        if (totalBytes.HasValue && totalBytes.Value > 0)
                        {
                            int pct = (int)((totalRead * 100) / totalBytes.Value);
                            onProgress(pct);
                        }
                    }
                }

                string currentExe = Process.GetCurrentProcess().MainModule?.FileName ?? "";
                if (string.IsNullOrEmpty(currentExe) || !File.Exists(currentExe))
                {
                    currentExe = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "fh6-controller-windows.exe");
                }

                // Batch script that waits for current process to exit, swaps executable, and launches
                string batchScript = $@"@echo off
timeout /t 1 /nobreak > nul
:retry
copy /y ""{tempExe}"" ""{currentExe}"" > nul 2>&1
if errorlevel 1 (
    timeout /t 1 /nobreak > nul
    goto retry
)
start """" ""{currentExe}""
del ""{tempExe}"" > nul 2>&1
(goto) 2>nul & del ""%~f0""
";
                await File.WriteAllTextAsync(updaterBat, batchScript);

                var psi = new ProcessStartInfo
                {
                    FileName = updaterBat,
                    CreateNoWindow = true,
                    UseShellExecute = true,
                    WindowStyle = ProcessWindowStyle.Hidden
                };

                Process.Start(psi);
                Environment.Exit(0);
                return true;
            }
            catch (Exception)
            {
                return false;
            }
        }
    }
}

