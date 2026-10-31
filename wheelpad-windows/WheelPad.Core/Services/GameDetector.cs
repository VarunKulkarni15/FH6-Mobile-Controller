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
            "ForzaHorizon5",
            "forzahorizon5",
            "ForzaHorizon4",
            "forzahorizon4",
            "ForzaHorizon3",
            "forzahorizon3",
            "ForzaMotorsport",
            "forzamotorsport",
            "ForzaHorizon6",
            "forzahorizon6",
            "Forza",
            "forza"
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
            string lower = processName.ToLowerInvariant();
            if (lower.Contains("forzahorizon6") || lower.Contains("fh6")) return "Forza Horizon 6";
            if (lower.Contains("forzahorizon5") || lower.Contains("fh5")) return "Forza Horizon 5";
            if (lower.Contains("forzahorizon4") || lower.Contains("fh4")) return "Forza Horizon 4";
            if (lower.Contains("forzahorizon3") || lower.Contains("fh3")) return "Forza Horizon 3";
            if (lower.Contains("forzamotorsport")) return "Forza Motorsport";
            return "Forza Horizon Series";
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

            // Poll process table every 2 seconds (0.001% CPU)
            _pollTimer = new Timer(PollCallback, null, 1000, 2000);
        }

        private void PollCallback(object? state)
        {
            try
            {
                Process? activeProcess = null;

                foreach (var name in _targetProcessNames)
                {
                    var matches = Process.GetProcessesByName(name);
                    if (matches.Length > 0)
                    {
                        activeProcess = matches.First();
                        break;
                    }
                }

                if (activeProcess != null && !_isGameRunning)
                {
                    // Game just launched!
                    _isGameRunning = true;
                    _lastProcessId = activeProcess.Id;
                    string friendlyName = GetFriendlyGameName(activeProcess.ProcessName);
                    CurrentGameName = friendlyName;

                    string? path = null;
                    try { path = activeProcess.MainModule?.FileName; } catch { }

                    LogMessage?.Invoke(this, $"[GameDetector] Detected {friendlyName} started (PID {_lastProcessId})");

                    if (AutoProfileSwitchEnabled && !string.IsNullOrEmpty(_autoActivateProfileId))
                    {
                        _profileManager.SetActiveProfile(_autoActivateProfileId);
                    }

                    var args = new GameStatusEventArgs(true, _lastProcessId, friendlyName, path);
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
                else if (activeProcess == null && _isGameRunning)
                {
                    // Game just exited
                    _isGameRunning = false;
                    string closedGame = CurrentGameName ?? "Forza Horizon";
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
                LogMessage?.Invoke(this, $"[GameDetector] Error polling processes: {ex.Message}");
            }
        }

        public void SetAutoActivateProfile(string profileId)
        {
            _autoActivateProfileId = profileId;
        }

        public void Dispose()
        {
            _pollTimer.Dispose();
        }
    }
}

