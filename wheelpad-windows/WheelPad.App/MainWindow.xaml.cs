using System;
using System.Collections.Generic;
using System.Drawing;
using System.Net;
using System.Net.Sockets;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;
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

        private int _currentBgIndex = 0;
        private readonly List<BitmapImage> _bgImages = new();
        private System.Windows.Threading.DispatcherTimer? _bgTimer;
        private bool _activeOnFirstImage = true;

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

            // Ambient background slideshow
            InitAmbientBackgroundSlideshow();

            // 2. Initialize System Tray NotifyIcon with custom Forza Horizon logo
            System.Drawing.Icon? trayIcon = null;
            try
            {
                string exePath = Environment.ProcessPath ?? System.Diagnostics.Process.GetCurrentProcess().MainModule?.FileName ?? "";
                if (System.IO.File.Exists(exePath))
                {
                    trayIcon = System.Drawing.Icon.ExtractAssociatedIcon(exePath);
                }
            }
            catch { }

            if (trayIcon == null)
            {
                try
                {
                    string iconPath = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "app_icon.ico");
                    if (System.IO.File.Exists(iconPath))
                    {
                        trayIcon = new System.Drawing.Icon(iconPath);
                    }
                    else
                    {
                        var stream = System.Windows.Application.GetResourceStream(new Uri("pack://application:,,,/app_icon.ico"))?.Stream;
                        if (stream != null)
                        {
                            trayIcon = new System.Drawing.Icon(stream);
                        }
                    }
                }
                catch { }
            }

            if (trayIcon != null)
            {
                try
                {
                    this.Icon = System.Windows.Interop.Imaging.CreateBitmapSourceFromHIcon(
                        trayIcon.Handle,
                        Int32Rect.Empty,
                        System.Windows.Media.Imaging.BitmapSizeOptions.FromEmptyOptions()
                    );
                }
                catch { }
            }

            _notifyIcon = new Forms.NotifyIcon
            {
                Text = "FH6 Controller // Forza Horizon",
                Icon = trayIcon ?? SystemIcons.Application,
                Visible = true
            };

            var trayMenu = new Forms.ContextMenuStrip();
            trayMenu.Items.Add("Open Dashboard", null, (s, e) => RestoreFromTray());
            trayMenu.Items.Add("Cycle Profile", null, (s, e) => CycleProfile());
            trayMenu.Items.Add(new Forms.ToolStripSeparator());
            trayMenu.Items.Add("Exit FH6 Controller", null, (s, e) => ExitApplication());
            _notifyIcon.ContextMenuStrip = trayMenu;
            _notifyIcon.Click += (s, e) => RestoreFromTray();
            _notifyIcon.DoubleClick += (s, e) => RestoreFromTray();

            // Background listener for single-instance foreground restore
            Task.Run(() =>
            {
                try
                {
                    using var restoreHandle = EventWaitHandle.OpenExisting(App.RestoreEventName);
                    while (!_isExplicitExit)
                    {
                        if (restoreHandle.WaitOne(1000))
                        {
                            if (!_isExplicitExit)
                            {
                                Dispatcher.InvokeAsync(() => RestoreFromTray());
                            }
                        }
                    }
                }
                catch { }
            });

            // 3. Setup Telemetry & FastStream Listeners
            _dispatcher.InputDispatched += OnInputDispatched;
            _syncServer.ClientConnectionChanged += OnClientConnectionChanged;
            _gameDetector.GameStatusChanged += OnGameStatusChanged;
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
                string qrPayload = $"https://horizoncontro.varunkulkarni.dpdns.org/?ip={localIp}&tcp=4243&udp=4242";
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
                TxtControllerStatus.Text = "CHECKING VIGEMBUS DRIVER...";
                StatusDot.Fill = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xFF, 0xCC, 0x00));
                PillViGEm.BorderBrush = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xFF, 0xCC, 0x00));

                // Auto-download and install ViGEmBus driver seamlessly
                Task.Run(async () =>
                {
                    if (!ViGEmInstallerService.IsDriverInstalled())
                    {
                        await Dispatcher.InvokeAsync(() =>
                        {
                            TxtControllerStatus.Text = "AUTO-DOWNLOADING VIGEMBUS...";
                            PillViGEm.BorderBrush = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0x00, 0xF2, 0xFE));
                        });

                        bool success = await ViGEmInstallerService.DownloadAndInstallDriverAsync(msg =>
                        {
                            Dispatcher.InvokeAsync(() =>
                            {
                                TxtControllerStatus.Text = msg.ToUpperInvariant();
                            });
                        });

                        if (success)
                        {
                            _xbox.Connect();
                            await Dispatcher.InvokeAsync(() =>
                            {
                                if (_xbox.IsConnected)
                                {
                                    TxtControllerStatus.Text = "STEAM / ViGEmBUS ACTIVE";
                                    StatusDot.Fill = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xCC, 0xFF, 0x00));
                                    PillViGEm.BorderBrush = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xCC, 0xFF, 0x00));
                                }
                            });
                        }
                    }
                });
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

                // 2. Throttle RT (0 to 214 px vertical fill)
                float throttleClamped = Math.Clamp(e.Throttle, 0.0f, 1.0f);
                BarThrottleFill.Height = throttleClamped * 214.0;
                TxtThrottleVal.Text = $"{(int)(throttleClamped * 100)}%";

                // 3. Brake LT (0 to 214 px vertical fill)
                float brakeClamped = Math.Clamp(e.Brake, 0.0f, 1.0f);
                BarBrakeFill.Height = brakeClamped * 214.0;
                TxtBrakeVal.Text = $"{(int)(brakeClamped * 100)}%";
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

        private void OnGameStatusChanged(object? sender, GameStatusEventArgs e)
        {
            Dispatcher.InvokeAsync(() =>
            {
                if (e.IsRunning)
                {
                    TxtGameStatus.Text = e.GameName.ToUpper();
                    TxtGameStatus.Foreground = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xCC, 0xFF, 0x00));
                    TxtProcessId.Text = e.ProcessId.ToString();
                    TxtProcessId.Foreground = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xCC, 0xFF, 0x00));

                    _notifyIcon.ShowBalloonTip(
                        4000,
                        $"{e.GameName} Active",
                        $"{e.GameName} detected (PID {e.ProcessId})! FastStream 120Hz synchronized.",
                        Forms.ToolTipIcon.Info
                    );
                }
                else
                {
                    TxtGameStatus.Text = "Scanning for game...";
                    TxtGameStatus.Foreground = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0x7E, 0xE6, 0xD8));
                    TxtProcessId.Text = "--";
                    TxtProcessId.Foreground = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xA0, 0xD0, 0xCC));

                    _notifyIcon.ShowBalloonTip(
                        3000,
                        "Forza Horizon",
                        $"{e.GameName} exited. Controller in standby.",
                        Forms.ToolTipIcon.Info
                    );
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

        private void BtnMinimizeTray_Click(object sender, RoutedEventArgs e)
        {
            Hide();
            _notifyIcon.ShowBalloonTip(
                2000,
                "FH6 Controller Companion",
                "Minimized to system tray. FH6 Controller is active in the background.",
                Forms.ToolTipIcon.Info
            );
        }

        private void RestoreFromTray()
        {
            Show();
            WindowState = WindowState.Normal;
            Activate();
            Topmost = true;
            Topmost = false;
            Focus();
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
            _bgTimer?.Stop();

            System.Windows.Application.Current.Shutdown();
        }

        private void InitAmbientBackgroundSlideshow()
        {
            string[] imageNames = new[]
            {
                "forza_night_drift.jpg",
                "forza_pagoda_pass.jpg",
                "forza_flower_circuit.jpg",
                "forza_mountain_valley.jpg"
            };

            foreach (var imgName in imageNames)
            {
                try
                {
                    string path = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "Assets", "Images", imgName);
                    if (System.IO.File.Exists(path))
                    {
                        var bmp = new BitmapImage();
                        bmp.BeginInit();
                        bmp.UriSource = new Uri(path, UriKind.Absolute);
                        bmp.CacheOption = BitmapCacheOption.OnLoad;
                        bmp.EndInit();
                        bmp.Freeze();
                        _bgImages.Add(bmp);
                    }
                }
                catch { }
            }

            if (_bgImages.Count > 0)
            {
                ImgAmbientBg1.Source = _bgImages[0];
                ImgAmbientBg1.Opacity = 0.16;
                ImgAmbientBg2.Opacity = 0;

                _bgTimer = new System.Windows.Threading.DispatcherTimer
                {
                    Interval = TimeSpan.FromSeconds(9)
                };
                _bgTimer.Tick += (s, e) => TransitionNextBackground();
                _bgTimer.Start();
            }
        }

        private void TransitionNextBackground()
        {
            if (_bgImages.Count <= 1) return;

            _currentBgIndex = (_currentBgIndex + 1) % _bgImages.Count;
            var nextImage = _bgImages[_currentBgIndex];

            var fadeIn = new System.Windows.Media.Animation.DoubleAnimation(0, 0.16, TimeSpan.FromMilliseconds(1600));
            var fadeOut = new System.Windows.Media.Animation.DoubleAnimation(0.16, 0, TimeSpan.FromMilliseconds(1600));

            if (_activeOnFirstImage)
            {
                ImgAmbientBg2.Source = nextImage;
                ImgAmbientBg2.BeginAnimation(UIElement.OpacityProperty, fadeIn);
                ImgAmbientBg1.BeginAnimation(UIElement.OpacityProperty, fadeOut);
                _activeOnFirstImage = false;
            }
            else
            {
                ImgAmbientBg1.Source = nextImage;
                ImgAmbientBg1.BeginAnimation(UIElement.OpacityProperty, fadeIn);
                ImgAmbientBg2.BeginAnimation(UIElement.OpacityProperty, fadeOut);
                _activeOnFirstImage = true;
            }
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
