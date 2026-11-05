using System.Runtime.InteropServices;
using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Sockets;
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
        private readonly Timer _keyStateTimer;

        private bool _isGameRunning;
        private bool _isGamePaused;
        private bool _lastEscDown;
        private bool _lastMDown;
        private bool _lastEnterDown;
        private bool _lastSpaceDown;
        private bool _lastWDown;
        private bool _lastSDown;
        private int _menuDepth = 0;
        private long _lastMenuToggleTicks = 0;
        public long LastMenuToggleTicks => Interlocked.Read(ref _lastMenuToggleTicks);

        private UdpClient? _telemetryClient;
        private CancellationTokenSource? _telemetryCts;
        private long _lastTelemetryTicks = 0;
        private int _lastIsRaceOn = 0;

        public bool HasRecentTelemetry => (DateTime.UtcNow.Ticks - Interlocked.Read(ref _lastTelemetryTicks)) < TimeSpan.FromSeconds(1.5).Ticks;
        public bool IsTelemetryDriving => HasRecentTelemetry && _lastIsRaceOn == 1;

        [DllImport("user32.dll")]
        private static extern short GetAsyncKeyState(int vKey);

        [DllImport("user32.dll")]
        private static extern IntPtr GetForegroundWindow();

        [DllImport("user32.dll")]
        private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);
        private int _lastProcessId;
        private string? _autoActivateProfileId;

        public bool IsGameRunning => _isGameRunning;
        public bool IsGamePaused => _isGamePaused;
        public string CurrentMenuMode => _currentMenuMode;
        public int MenuDepth => _menuDepth;
        private string _currentMenuMode = "PAUSE";
        public int CurrentProcessId => _lastProcessId;
        public string? CurrentGameName { get; private set; }
        public bool AutoProfileSwitchEnabled { get; set; } = true;

        public static string GetFriendlyGameName(string processName)
        {
            if (string.IsNullOrWhiteSpace(processName)) return "SIM RACING GAME";
            string lower = processName.ToLowerInvariant();
            if (lower.Contains("6") && (lower.Contains("horizon") || lower.Contains("fh"))) return "Open-World Racing (Next-Gen)";
            if (lower.Contains("5") && (lower.Contains("horizon") || lower.Contains("fh"))) return "Open-World Racing 5";
            if (lower.Contains("4") && (lower.Contains("horizon") || lower.Contains("fh"))) return "Open-World Racing 4";
            if (lower.Contains("3") && (lower.Contains("horizon") || lower.Contains("fh"))) return "Open-World Racing 3";
            if (lower.Contains("motorsport")) return "Track Motorsport Sim";
            if (lower.Contains("tlou") || lower.Contains("lastofus")) return "Action Adventure";
            if (lower.Contains("minecraft.windows") || lower.Contains("minecraft")) return "Sandbox Game";
            if (lower.Contains("javaw")) return "Sandbox Java";
            if (lower.Contains("forza") || lower.Contains("horizon")) return "Open-World Racing Sim";
            return processName.ToUpper();
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

            // High-frequency key monitor (50ms) to detect ESC/M/Enter/Space on laptop keyboard
            _keyStateTimer = new Timer(KeyPollCallback, null, 200, 50);

            // Background telemetry listener for Forza Horizon 60Hz Data Out (Port 5300)
            StartTelemetryListener();
        }

        private void StartTelemetryListener()
        {
            try
            {
                _telemetryCts = new CancellationTokenSource();
                _telemetryClient = new UdpClient(new IPEndPoint(IPAddress.Any, 5300));
                _ = Task.Run(async () =>
                {
                    while (!_telemetryCts.Token.IsCancellationRequested && _telemetryClient != null)
                    {
                        try
                        {
                            var res = await _telemetryClient.ReceiveAsync(_telemetryCts.Token);
                            if (res.Buffer.Length >= 4)
                            {
                                int isRaceOn = BitConverter.ToInt32(res.Buffer, 0);
                                Interlocked.Exchange(ref _lastTelemetryTicks, DateTime.UtcNow.Ticks);
                                _lastIsRaceOn = isRaceOn;

                                // Telemetry Authoritative Driving Recovery:
                                // When isRaceOn == 1, car is actively driving on road/track.
                                // Recover any paused state back to Driving Cockpit automatically.
                                // Telemetry Authoritative Driving Recovery:
                                // GUARD: Must be outside 1000ms grace period of pausing, to ignore in-flight stale telemetry!
                                long ticksSinceToggle = DateTime.UtcNow.Ticks - Interlocked.Read(ref _lastMenuToggleTicks);
                                bool isGracePeriod = ticksSinceToggle < TimeSpan.FromMilliseconds(1000).Ticks;

                                if (isRaceOn == 1 && _isGamePaused && !isGracePeriod)
                                {
                                    _menuDepth = 0;
                                    SetPauseState(false, "PAUSE");
                                }
                                // NOTE: We NEVER auto-pause on isRaceOn == 0!
                                // Cutscenes, intro cinematics, car selection, and loading screens have isRaceOn == 0.
                                // Pausing must be explicitly initiated by user input (Pause / Map / ESC).
                            }
                        }
                        catch (OperationCanceledException) { break; }
                        catch { }
                    }
                });
                LogMessage?.Invoke(this, "[GameDetector] Listening for Telemetry Data Out on UDP 5300...");
            }
            catch (Exception ex)
            {
                LogMessage?.Invoke(this, $"[GameDetector] Telemetry listener not started: {ex.Message}");
            }
        }

        public void HandleMenuSelect()
        {
            // Pure pass-through, no artificial depth guessing
        }

        public void HandleMenuBack()
        {
            // Pure pass-through, no artificial depth guessing
        }

        private void KeyPollCallback(object? state)
        {
            try
            {
                // 0x1B = VK_ESCAPE, 0x4D = 'M', 0x0D = ENTER, 0x20 = SPACE, 0x57 = 'W', 0x53 = 'S'
                bool escDown = (GetAsyncKeyState(0x1B) & 0x8000) != 0;
                bool mDown = (GetAsyncKeyState(0x4D) & 0x8000) != 0;
                bool enterDown = (GetAsyncKeyState(0x0D) & 0x8000) != 0;
                bool spaceDown = (GetAsyncKeyState(0x20) & 0x8000) != 0;
                bool wDown = (GetAsyncKeyState(0x57) & 0x8000) != 0;
                bool sDown = (GetAsyncKeyState(0x53) & 0x8000) != 0;

                // Keyboard monitoring remains active so laptop ESC/M is always responsive
                if (_isGameRunning)
                {
                    IntPtr fg = GetForegroundWindow();
                    GetWindowThreadProcessId(fg, out uint fgPid);
                    bool isGameActive = (fgPid == (uint)_lastProcessId || fg == IntPtr.Zero);
                    if (!isGameActive)
                    {
                        try
                        {
                            var proc = Process.GetProcessById((int)fgPid);
                            string pName = proc.ProcessName.ToLowerInvariant();
                            if (pName.Contains("forza") || pName.Contains("applicationframehost") || pName.Contains("fh"))
                            {
                                isGameActive = true;
                            }
                        }
                        catch { }
                    }

                    if (isGameActive)
                    {
                        // 400ms Debounce guard: Ignore keyboard ESC / M echo immediately after a menu state change
                        bool isWithinDebounce = (DateTime.UtcNow.Ticks - Interlocked.Read(ref _lastMenuToggleTicks)) < TimeSpan.FromMilliseconds(400).Ticks;

                        if (mDown && !_lastMDown && !isWithinDebounce)
                        {
                            // In Forza, M on PC keyboard ONLY opens the map! It never closes the map!
                            if (!_isGamePaused)
                            {
                                _menuDepth = 0;
                                SetPauseState(true, "MAP");
                            }
                        }
                        else if (escDown && !_lastEscDown && !isWithinDebounce)
                        {
                            if (!_isGamePaused)
                            {
                                _menuDepth = 0;
                                SetPauseState(true, "PAUSE");
                            }
                            else
                            {
                                _menuDepth = 0;
                                SetPauseState(false, "PAUSE");
                            }
                        }
                        // No random auto-guessing from W/S/Space/Enter
                    }
                }
                _lastEscDown = escDown;
                _lastMDown = mDown;
                _lastEnterDown = enterDown;
                _lastSpaceDown = spaceDown;
                _lastWDown = wDown;
                _lastSDown = sDown;
            }
            catch { }
        }

        public void SetPauseState(bool isPaused, string menuMode = "PAUSE")
        {
            Interlocked.Exchange(ref _lastMenuToggleTicks, DateTime.UtcNow.Ticks);
            if (!isPaused)
            {
                _menuDepth = 0;
            }
            _isGamePaused = isPaused;
            _currentMenuMode = menuMode;
            _syncServer?.BroadcastEvent(new
            {
                type = "PAUSE_STATE_CHANGED",
                isPaused = _isGamePaused,
                menuMode = _currentMenuMode
            });
            LogMessage?.Invoke(this, $"[GameDetector] State updated: Mode={_currentMenuMode}, Paused={_isGamePaused}, Depth={_menuDepth}");
        }

        public void CheckImmediately(bool forceNotify = false)
        {
            if (forceNotify)
            {
                _isGameRunning = false;
                _lastProcessId = 0;
            }
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

                // Fallback: search all running processes if exact name did not match
                if (activeProcess == null)
                {
                    try
                    {
                        var all = Process.GetProcesses();
                        foreach (var p in all)
                        {
                            try
                            {
                                string pName = p.ProcessName.ToLowerInvariant();
                                if (pName.Contains("forzahorizon") || pName.Contains("forzamotorsport") ||
                                    pName == "fh5" || pName == "fh4" || pName == "fh6" ||
                                    pName.StartsWith("forza", StringComparison.OrdinalIgnoreCase))
                                {
                                    activeProcess = p;
                                    break;
                                }
                            }
                            catch { }
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
            _keyStateTimer?.Dispose();
            try
            {
                _telemetryCts?.Cancel();
                _telemetryClient?.Close();
                _telemetryClient?.Dispose();
            }
            catch { }
        }
    }
}
