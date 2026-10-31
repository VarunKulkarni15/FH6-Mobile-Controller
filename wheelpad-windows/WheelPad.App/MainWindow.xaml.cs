using System;
using System.Drawing;
using System.Net;
using System.Net.Sockets;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Media;
using WheelPad.Core.Models;
using WheelPad.Core.Services;
using Forms = System.Windows.Forms;

namespace WheelPad.App
{
    public partial class MainWindow : Window
    {
        private readonly ProfileManager _profileManager;
        private readonly ViGEmXboxController _xbox;
        private readonly InputDispatcher _dispatcher;
        private readonly ControlSyncServer _syncServer;
        private readonly UdpFastStreamReceiver _udpReceiver;
        private readonly BeaconBroadcaster _beaconBroadcaster;
        private readonly GameDetector _gameDetector;
        private readonly SpotifyManager _spotifyManager;
        private readonly UniversalRadioService _radioService;
        private readonly Forms.NotifyIcon _notifyIcon;
        private bool _isExplicitExit = false;

        public MainWindow()
        {
            InitializeComponent();

            string localIp = GetLocalIpAddress();

            // 1. Initialize Subsystems
            _profileManager = new ProfileManager();
            _xbox = new ViGEmXboxController();
            _xbox.Connect();

            _dispatcher = new InputDispatcher(_profileManager, _xbox);
            _syncServer = new ControlSyncServer(_profileManager, _dispatcher);
            _udpReceiver = new UdpFastStreamReceiver(_dispatcher);
            _beaconBroadcaster = new BeaconBroadcaster();
            _gameDetector = new GameDetector(_profileManager, _syncServer);
            _spotifyManager = new SpotifyManager();
            _syncServer.Spotify = _spotifyManager;
            _radioService = new UniversalRadioService();
            _syncServer.Radio = _radioService;

            // 2. Initialize System Tray NotifyIcon
            _notifyIcon = new Forms.NotifyIcon
            {
                Text = "WheelPad Companion — Forza Horizon 6",
                Icon = SystemIcons.Application,
                Visible = true
            };

            var trayMenu = new Forms.ContextMenuStrip();
            trayMenu.Items.Add("Open Dashboard", null, (s, e) => RestoreFromTray());
            trayMenu.Items.Add("Cycle Profile", null, (s, e) => CycleProfile());
            trayMenu.Items.Add(new Forms.ToolStripSeparator());
            trayMenu.Items.Add("Exit WheelPad", null, (s, e) => ExitApplication());
            _notifyIcon.ContextMenuStrip = trayMenu;
            _notifyIcon.DoubleClick += (s, e) => RestoreFromTray();

            // 3. Setup Telemetry & FastStream Listeners
            _dispatcher.InputDispatched += OnInputDispatched;
            _syncServer.ClientConnectionChanged += OnClientConnectionChanged;
            _gameDetector.GameStatusChanged += OnGameStatusChanged;
            _spotifyManager.TrackStateUpdated += OnTrackStateUpdated;
            _radioService.StatusUpdated += OnRadioStatusUpdated;
            _udpReceiver.PacketsPerSecondUpdated += OnPacketsPerSecondUpdated;

            // 4. Start Servers & Broadcaster
            _syncServer.Start();
            _udpReceiver.Start();
            _beaconBroadcaster.Start(localIp);

            // 5. Populate Initial UI & Generate Pairing QR Code
            TxtLocalIp.Text = localIp;
            try
            {
                // Web deep-link payload: camera scanners open website with IP parameters which deep-links to app
                string qrPayload = $"https://fh6-controller.github.io/?ip={localIp}&tcp=4243&udp=4242";
                ImgQrCode.Source = QrCodeHelper.GenerateQrCode(qrPayload);
            }
            catch { }

            if (_xbox.IsConnected)
            {
                TxtControllerStatus.Text = "STEAM / ViGEmBUS ACTIVE";
                StatusDot.Fill = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xCC, 0xFF, 0x00));
                PillViGEm.BorderBrush = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xCC, 0xFF, 0x00));
            }
            else
            {
                TxtControllerStatus.Text = "FALLBACK KEYBOARD MODE";
                StatusDot.Fill = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xFF, 0xCC, 0x00));
                PillViGEm.BorderBrush = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xFF, 0xCC, 0x00));
            }

            // Window Close handler
            Closing += MainWindow_Closing;
        }

        private void OnPacketsPerSecondUpdated(object? sender, int pps)
        {
            Dispatcher.InvokeAsync(() =>
            {
                TxtPacketRate.Text = $"{pps} PACKETS/SEC";
            });
        }

        private void OnInputDispatched(object? sender, InputDispatchedEventArgs e)
        {
            Dispatcher.InvokeAsync(() =>
            {
                // 1. Steering Wheel dynamic visual rotation (-90 to +90 degrees)
                float steerClamped = Math.Clamp(e.Steering, -1.0f, 1.0f);
                WheelRotation.Angle = steerClamped * 90.0;
                int steerPercent = (int)(steerClamped * 100);
                string steerDir = steerPercent < 0 ? "LEFT" : steerPercent > 0 ? "RIGHT" : "CENTER";
                TxtSteerVal.Text = $"STEERING: {steerDir} ({Math.Abs(steerPercent)}%)";

                // 2. Throttle RT (0 to 164 px vertical fill)
                float throttleClamped = Math.Clamp(e.Throttle, 0.0f, 1.0f);
                BarThrottleFill.Height = throttleClamped * 164.0;
                TxtThrottleVal.Text = $"{(int)(throttleClamped * 100)}%";

                // 3. Brake LT (0 to 164 px vertical fill)
                float brakeClamped = Math.Clamp(e.Brake, 0.0f, 1.0f);
                BarBrakeFill.Height = brakeClamped * 164.0;
                TxtBrakeVal.Text = $"{(int)(brakeClamped * 100)}%";

                // 4. Handbrake Neon State
                if (e.Handbrake)
                {
                    BadgeHandbrake.Background = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0x3B, 0x0D, 0x1A));
                    BadgeHandbrake.BorderBrush = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xFF, 0x00, 0x55));
                    TxtHandbrakeState.Text = "ACTIVE (PULLED)";
                    TxtHandbrakeState.Foreground = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xFF, 0x00, 0x55));
                }
                else
                {
                    BadgeHandbrake.Background = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0x14, 0x19, 0x26));
                    BadgeHandbrake.BorderBrush = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0x26, 0x33, 0x4A));
                    TxtHandbrakeState.Text = "RELEASED";
                    TxtHandbrakeState.Foreground = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0x6C, 0x7D, 0x93));
                }
            });
        }

        private void OnClientConnectionChanged(object? sender, bool connected)
        {
            Dispatcher.InvokeAsync(() =>
            {
                if (connected)
                {
                    TxtPhoneStatus.Text = "CONNECTED (0-DELAY STREAM)";
                    TxtPhoneStatus.Foreground = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xCC, 0xFF, 0x00));

                    _notifyIcon.ShowBalloonTip(
                        3500,
                        "FH6 Controller Companion",
                        "Mobile Controller connected.\nVirtual Xbox 360 controller is synchronized for Forza Horizon 6.",
                        Forms.ToolTipIcon.Info
                    );
                }
                else
                {
                    TxtPhoneStatus.Text = "Waiting for connection...";
                    TxtPhoneStatus.Foreground = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xFF, 0xCC, 0x00));

                    _notifyIcon.ShowBalloonTip(
                        3000,
                        "FH6 Controller Companion",
                        "Mobile Controller disconnected. Inputs reset to neutral.",
                        Forms.ToolTipIcon.Warning
                    );
                }
            });
        }

        private void BtnAutoDrive_Click(object sender, RoutedEventArgs e)
        {
            _dispatcher.TriggerAutoDriveSequence();
            _notifyIcon.ShowBalloonTip(
                2000,
                "Auto-Drive Assist",
                "Auto-drive assist sequence dispatched (C + Num 2)",
                Forms.ToolTipIcon.Info
            );
        }

        private void OnGameStatusChanged(object? sender, GameStatusEventArgs e)
        {
            Dispatcher.InvokeAsync(() =>
            {
                if (e.IsRunning)
                {
                    TxtGameStatus.Text = $"RUNNING (PID {e.ProcessId})";
                    TxtGameStatus.Foreground = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0x00, 0xE6, 0x76));

                    _notifyIcon.ShowBalloonTip(
                        3500,
                        "Forza Horizon 6 Detected",
                        $"Forza Horizon 6 is active (PID {e.ProcessId})! WheelPad is ready.",
                        Forms.ToolTipIcon.Info
                    );
                }
                else
                {
                    TxtGameStatus.Text = "Scanning for game...";
                    TxtGameStatus.Foreground = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0x88, 0x99, 0xB0));
                }
            });
        }

        private void OnTrackStateUpdated(object? sender, SpotifyTrackState track)
        {
            Dispatcher.InvokeAsync(() =>
            {
                TxtMediaTitle.Text = string.IsNullOrEmpty(track.TrackName) ? "No Track Playing" : track.TrackName;
                TxtMediaArtist.Text = string.IsNullOrEmpty(track.ArtistName) ? "YouTube Music / Spotify / Windows Media" : track.ArtistName;
            });
        }

        private void OnRadioStatusUpdated(object? sender, UniversalRadioStatus radio)
        {
            Dispatcher.InvokeAsync(() =>
            {
                if (radio.IsInstalled)
                {
                    TxtRadioStatus.Text = $"Synced: {radio.CurrentStation}";
                    TxtRadioStatus.Foreground = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0x00, 0xE6, 0x76));
                }
            });
        }

        private void CycleProfile()
        {
            var profiles = _profileManager.Profiles;
            if (profiles.Count <= 1) return;

            int currentIndex = -1;
            for (int i = 0; i < profiles.Count; i++)
            {
                if (profiles[i].ProfileId == _profileManager.ActiveProfile.ProfileId)
                {
                    currentIndex = i;
                    break;
                }
            }

            int nextIndex = (currentIndex + 1) % profiles.Count;
            _profileManager.SetActiveProfile(profiles[nextIndex].ProfileId);

            _notifyIcon.ShowBalloonTip(
                2500,
                "Profile Switched",
                $"Active Profile: {_profileManager.ActiveProfile.ProfileName}",
                Forms.ToolTipIcon.Info
            );
        }

        private void BtnTestHandbrake_Click(object sender, RoutedEventArgs e)
        {
            _ = Task.Run(async () =>
            {
                _dispatcher.DispatchDigitalInput("handbrake", true);
                await Task.Delay(250);
                _dispatcher.DispatchDigitalInput("handbrake", false);
            });
        }

        private void BtnMediaPrev_Click(object sender, RoutedEventArgs e)
        {
            _spotifyManager.SendWindowsMediaKey("previous");
        }

        private void BtnMediaPlayPause_Click(object sender, RoutedEventArgs e)
        {
            _spotifyManager.SendWindowsMediaKey("toggle");
        }

        private void BtnMediaNext_Click(object sender, RoutedEventArgs e)
        {
            _spotifyManager.SendWindowsMediaKey("next");
        }

        private void BtnMinimizeTray_Click(object sender, RoutedEventArgs e)
        {
            Hide();
            _notifyIcon.ShowBalloonTip(
                2000,
                "WheelPad Companion",
                "Minimized to system tray. WheelPad is active in the background.",
                Forms.ToolTipIcon.Info
            );
        }

        private void RestoreFromTray()
        {
            Show();
            WindowState = WindowState.Normal;
            Activate();
        }

        private void MainWindow_Closing(object? sender, System.ComponentModel.CancelEventArgs e)
        {
            if (!_isExplicitExit)
            {
                e.Cancel = true;
                Hide();
                _notifyIcon.ShowBalloonTip(
                    2000,
                    "WheelPad Still Active",
                    "WheelPad is running in the background for your game. Right-click the tray icon to exit.",
                    Forms.ToolTipIcon.Info
                );
            }
        }

        private void ExitApplication()
        {
            _isExplicitExit = true;
            _notifyIcon.Visible = false;
            _notifyIcon.Dispose();

            _beaconBroadcaster.Dispose();
            _radioService.Dispose();
            _spotifyManager.Dispose();
            _gameDetector.Dispose();
            _udpReceiver.Dispose();
            _syncServer.Dispose();
            _dispatcher.Dispose();
            _xbox.Dispose();

            System.Windows.Application.Current.Shutdown();
        }

        private static string GetLocalIpAddress()
        {
            try
            {
                using var socket = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, 0);
                socket.Connect("8.8.8.8", 65530);
                if (socket.LocalEndPoint is IPEndPoint endPoint)
                {
                    return endPoint.Address.ToString();
                }
            }
            catch { }
            return "127.0.0.1";
        }
    }
}
