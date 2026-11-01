using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;

namespace WheelPad.Core.Services
{
    public class GameStatusEventArgs : EventArgs
    {
        public bool IsRunning { get; }
        public int ProcessId { get; }
        public string ProcessName { get; }
        public string GameName => GameDetector.GetFriendlyGameName(ProcessName);
        public string? ExecutablePath { get; }

        public GameStatusEventArgs(bool isRunning, int processId, string processName, string? executablePath = null)
        {
            IsRunning = isRunning;
            ProcessId = processId;
            ProcessName = processName;
            ExecutablePath = executablePath;
        }
    }

    public class GameDetector : IDisposable
    {
        public static readonly string[] DefaultTargetProcesses = new[]
        {
            "forzahorizon6",
            "ForzaHorizon6",
            "fh6",
            "FH6",
            "forzahorizon5",
            "ForzaHorizon5",
            "fh5",
            "FH5",
            "forzahorizon4",
            "ForzaHorizon4",
            "fh4",
            "FH4",
            "forzahorizon3",
            "ForzaHorizon3",
            "forzamotorsport",
            "ForzaMotorsport",
            "forza",
            "Forza",
            "tlou-i",
            "tlou-i-l",
            "tlou",
            "tlou2",
            "TheLastOfUs",
            "TheLastOfUsPartI",
            "TheLastOfUsPartII",
            "Minecraft.Windows",
            "Minecraft",
            "minecraft",
            "javaw"
        };

        private readonly string[] _targetProcessNames;
        private readonly ProfileManager _profileManager;
        private readonly ControlSyncServer? _syncServer;
        private readonly Timer _pollTimer;

        private bool _isGameRunning;
        private int _lastProcessId;
        private string? _autoActivateProfileId;

        public bool IsGameRunning => _isGameRunning;
        public int CurrentProcessId => _lastProcessId;
        public string? CurrentGameName { get; private set; }
        public bool AutoProfileSwitchEnabled { get; set; } = true;

        public static string GetFriendlyGameName(string processName)
        {
            if (string.IsNullOrWhiteSpace(processName)) return "Unknown Game";
            string lower = processName.ToLowerInvariant();
            if (lower.Contains("6") && (lower.Contains("horizon") || lower.Contains("fh"))) return "Forza Horizon 6";
            if (lower.Contains("5") && (lower.Contains("horizon") || lower.Contains("fh"))) return "Forza Horizon 5";
            if (lower.Contains("4") && (lower.Contains("horizon") || lower.Contains("fh"))) return "Forza Horizon 4";
            if (lower.Contains("3") && (lower.Contains("horizon") || lower.Contains("fh"))) return "Forza Horizon 3";
            if (lower.Contains("motorsport")) return "Forza Motorsport";
            if (lower.Contains("tlou") || lower.Contains("lastofus")) return "The Last of Us Part II";
            if (lower.Contains("minecraft.windows") || lower.Contains("minecraft")) return "Minecraft Bedrock";
            if (lower.Contains("javaw")) return "Minecraft Java";
            if (lower.Contains("forza")) return "Forza Horizon";
            return processName;
        }

        public event EventHandler<GameStatusEventArgs>? GameStatusChanged;
        public event EventHandler<string>? LogMessage;

        public GameDetector(
            ProfileManager profileManager,
            ControlSyncServer? syncServer = null,
            string[]? targetProcessNames = null,
            string? autoActivateProfileId = null)
        {
            _profileManager = profileManager;
            _syncServer = syncServer;
            _targetProcessNames = targetProcessNames ?? DefaultTargetProcesses;
            _autoActivateProfileId = autoActivateProfileId;

            // Poll process table immediately and then every 2 seconds
            _pollTimer = new Timer(PollCallback, null, 100, 2000);
        }

        public void CheckImmediately()
        {
            PollCallback(null);
        }

        private void PollCallback(object? state)
        {
            try
            {
                Process? activeProcess = null;

                foreach (var name in _targetProcessNames)
                {
                    try
                    {
                        var matches = Process.GetProcessesByName(name);
                        if (matches.Length > 0)
                        {
                            activeProcess = matches.First();
                            break;
                        }
                    }
                    catch { }
                }

                if (activeProcess != null)
                {
                    if (!_isGameRunning || _lastProcessId != activeProcess.Id)
                    {
                        _isGameRunning = true;
                        _lastProcessId = activeProcess.Id;
                        string friendlyName = GetFriendlyGameName(activeProcess.ProcessName);
                        CurrentGameName = friendlyName;

                        string? path = null;
                        try { path = activeProcess.MainModule?.FileName; } catch { }

                        LogMessage?.Invoke(this, $"[GameDetector] Detected {friendlyName} running (PID {_lastProcessId})");

                        if (AutoProfileSwitchEnabled && !string.IsNullOrEmpty(_autoActivateProfileId))
                        {
                            _profileManager.SetActiveProfile(_autoActivateProfileId);
                        }

                        var args = new GameStatusEventArgs(true, _lastProcessId, activeProcess.ProcessName, path);
                        GameStatusChanged?.Invoke(this, args);

                        // Notify Android companion via TCP sync
                        _syncServer?.BroadcastEvent(new
                        {
                            type = "GAME_STATUS_CHANGED",
                            isRunning = true,
                            gameName = friendlyName,
                            processId = _lastProcessId
                        });
                    }
                }
                else if (_isGameRunning)
                {
                    // Game just exited
                    _isGameRunning = false;
                    string closedGame = CurrentGameName ?? "Game";
                    CurrentGameName = null;
                    LogMessage?.Invoke(this, $"[GameDetector] {closedGame} closed (PID {_lastProcessId})");

                    var args = new GameStatusEventArgs(false, _lastProcessId, closedGame);
                    _lastProcessId = 0;
                    GameStatusChanged?.Invoke(this, args);

                    // Notify Android companion
                    _syncServer?.BroadcastEvent(new
                    {
                        type = "GAME_STATUS_CHANGED",
                        isRunning = false,
                        gameName = closedGame,
                        processId = 0
                    });
                }
            }
            catch (Exception ex)
            {
                LogMessage?.Invoke(this, $"[GameDetector] Error in poll loop: {ex.Message}");
            }
        }

        public void Dispose()
        {
            _pollTimer.Dispose();
        }
    }
}
