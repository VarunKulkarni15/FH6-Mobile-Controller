using System.Collections.Generic;
using System.Linq;
using System.Net.NetworkInformation;
using System;
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
        private readonly AutoUpdateService _autoUpdateService;
        private readonly Forms.NotifyIcon _notifyIcon;
        private bool _isExplicitExit = false;
        private string? _pendingUpdateUrl;

        private static void Log(string msg)
        {
            try
            {
                App.Log($"[MainWindow] {msg}");
            }
            catch { }
        }

        public MainWindow()
        {
            Log("Constructor start");
            try
            {
                InitializeComponent();
                Log("InitializeComponent done");
            }
            catch (Exception ex)
            {
                Log($"InitializeComponent FAILED: {ex}");
                throw;
            }

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
            _dispatcher.GameDetector = _gameDetector;
            _autoUpdateService = new AutoUpdateService();

            // Ambient background: Single Forza Horizon GT-R image
            InitAmbientBackground();

            // 2. Initialize System Tray NotifyIcon with custom Forza Horizon logo
            System.Drawing.Icon? trayIcon = null;
            try
            {
                string iconPath = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "app_icon.ico");
                if (System.IO.File.Exists(iconPath))
                {
                    trayIcon = new System.Drawing.Icon(iconPath, 32, 32);
                }
            }
            catch { }

            if (trayIcon == null)
            {
                try
                {
                    var stream = System.Windows.Application.GetResourceStream(new Uri("pack://application:,,,/app_icon.ico"))?.Stream;
                    if (stream != null)
                    {
                        trayIcon = new System.Drawing.Icon(stream, 32, 32);
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
                Text = "HRZN Controller // Windows Companion",
                Icon = trayIcon ?? System.Drawing.SystemIcons.Application,
                Visible = true
            };

            var trayMenu = new Forms.ContextMenuStrip();
            trayMenu.Items.Add("Open Dashboard", null, (s, e) => RestoreFromTray());
            trayMenu.Items.Add("Cycle Profile", null, (s, e) => CycleProfile());
            trayMenu.Items.Add(new Forms.ToolStripSeparator());
            trayMenu.Items.Add("Exit Companion", null, (s, e) => ExitApplication());
            _notifyIcon.ContextMenuStrip = trayMenu;
            _notifyIcon.Click += (s, e) => RestoreFromTray();
            _notifyIcon.DoubleClick += (s, e) => RestoreFromTray();

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
                string qrPayload = $"https://hrzncontroller.varunkulkarni.dpdns.org/?ip={localIp}&tcp=4243&udp=4242";
                ImgQrCode.Source = QrCodeHelper.GenerateQrCode(qrPayload);
            }
            catch { }

            if (_xbox.IsConnected)
            {
                TxtControllerStatus.Text = "XINPUT / VIGEMBUS ACTIVE";
                StatusDot.Fill = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xCC, 0xFF, 0x00));
            }
            else
            {
                TxtControllerStatus.Text = "READY (STANDBY)";
                StatusDot.Fill = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0x00, 0xDD, 0xFF));
            }
            if (_xbox.IsConnected)
            {
                TxtControllerStatus.Text = "XINPUT / VIGEMBUS ACTIVE";
                StatusDot.Fill = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xCC, 0xFF, 0x00));
            }
            else
            {
                TxtControllerStatus.Text = "READY (STANDBY)";
                StatusDot.Fill = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0x00, 0xDD, 0xFF));
            }

            // 6. Platform & Launcher Detection (Steam, Xbox Game Pass, Epic Games)
            try
            {
                var platforms = PlatformDetector.CheckPlatforms();
                TxtSteamStatus.Text = platforms.IsSteamInstalled ? "STEAM: READY" : "STEAM: -";
                TxtSteamStatus.Foreground = platforms.IsSteamInstalled 
                    ? new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xCC, 0xFF, 0x00)) 
                    : new SolidColorBrush(System.Windows.Media.Color.FromRgb(0x7E, 0x94, 0xAD));

                TxtXboxStatus.Text = platforms.IsXboxAppInstalled ? "XBOX: READY" : "XBOX: -";
                TxtXboxStatus.Foreground = platforms.IsXboxAppInstalled 
                    ? new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xCC, 0xFF, 0x00)) 
                    : new SolidColorBrush(System.Windows.Media.Color.FromRgb(0x7E, 0x94, 0xAD));

                TxtEpicStatus.Text = platforms.IsEpicGamesInstalled ? "EPIC: READY" : "EPIC: -";
                TxtEpicStatus.Foreground = platforms.IsEpicGamesInstalled 
                    ? new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xCC, 0xFF, 0x00)) 
                    : new SolidColorBrush(System.Windows.Media.Color.FromRgb(0x7E, 0x94, 0xAD));

                TxtLauncherNote.Text = platforms.SummaryText;
            }
            catch { }

            // Window Close handler
            Closing += MainWindow_Closing;

            // Trigger immediate game detection on startup
            _gameDetector.CheckImmediately();

            // Check for updates in background
            _ = CheckForUpdatesSilentlyAsync();
            Log("Constructor finished successfully!");
        }

        private void BtnCopyIp_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                System.Windows.Clipboard.SetText(TxtLocalIp.Text);
                TxtFooter.Text = $"Host IP '{TxtLocalIp.Text}' copied to clipboard!";
            }
            catch { }
        }

        private async Task CheckForUpdatesSilentlyAsync()
        {
            try
            {
                var result = await _autoUpdateService.CheckForUpdatesAsync();
                if (result.IsUpdateAvailable && result.LatestVersion != null)
                {
                    await Dispatcher.InvokeAsync(() =>
                    {
                        _pendingUpdateUrl = result.LatestVersion.DownloadUrl;
                        TxtUpdateTitle.Text = $"v{result.LatestVersion.VersionName} is now live";
                        TxtUpdateChangelog.Text = $"Changelog: {result.LatestVersion.Changelog}";
                        BannerUpdate.Visibility = Visibility.Visible;
                    });
                }
            }
            catch { }
        }

        private async void BtnCheckUpdates_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                TxtFooter.Text = "Checking for updates...";
                var result = await _autoUpdateService.CheckForUpdatesAsync();
                if (result.IsUpdateAvailable && result.LatestVersion != null)
                {
                    _pendingUpdateUrl = result.LatestVersion.DownloadUrl;
                    TxtUpdateTitle.Text = $"v{result.LatestVersion.VersionName} is now live";
                    TxtUpdateChangelog.Text = $"Changelog: {result.LatestVersion.Changelog}";
                    BannerUpdate.Visibility = Visibility.Visible;
                    TxtFooter.Text = $"Update v{result.LatestVersion.VersionName} available! Click 'UPDATE NOW' above.";
                }
                else
                {
                    TxtFooter.Text = $"Companion is up-to-date (v{AutoUpdateService.CurrentVersionName}). No updates found.";
                }
            }
            catch (Exception ex)
            {
                TxtFooter.Text = $"Update check failed: {ex.Message}";
            }
        }

        private async void BtnApplyUpdate_Click(object sender, RoutedEventArgs e)
        {
            if (string.IsNullOrEmpty(_pendingUpdateUrl)) return;

            BtnApplyUpdate.IsEnabled = false;
            BtnApplyUpdate.Content = "DOWNLOADING...";
            ProgUpdate.Visibility = Visibility.Visible;
            ProgUpdate.Value = 0;

            bool success = await _autoUpdateService.DownloadAndApplyUpdateAsync(_pendingUpdateUrl, pct =>
            {
                Dispatcher.InvokeAsync(() =>
                {
                    ProgUpdate.Value = pct;
                    BtnApplyUpdate.Content = $"DOWNLOADING {pct}%";
                });
            });

            if (!success)
            {
                Dispatcher.Invoke(() =>
                {
                    BtnApplyUpdate.IsEnabled = true;
                    BtnApplyUpdate.Content = "RETRY UPDATE";
                    TxtFooter.Text = "Update download failed. Please check network connection.";
                });
            }
        }

        private void BtnDismissUpdate_Click(object sender, RoutedEventArgs e)
        {
            BannerUpdate.Visibility = Visibility.Collapsed;
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
                    TxtPhoneStatus.Text = "CONNECTED (120Hz STREAM)";
                    TxtPhoneStatus.Foreground = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xCC, 0xFF, 0x00));

                    _notifyIcon.ShowBalloonTip(
                        3000,
                        "HRZN Controller Companion",
                        "Mobile Controller connected. Xbox 360 controller synchronized.",
                        Forms.ToolTipIcon.Info
                    );
                }
                else
                {
                    TxtPhoneStatus.Text = "Waiting for connection...";
                    TxtPhoneStatus.Foreground = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xFF, 0xCC, 0x00));

                    _notifyIcon.ShowBalloonTip(
                        2500,
                        "HRZN Controller Companion",
                        "Mobile Controller disconnected. Inputs neutral.",
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
                        3500,
                        $"{e.GameName} Active",
                        $"{e.GameName} detected (PID {e.ProcessId}). FastStream synchronized.",
                        Forms.ToolTipIcon.Info
                    );
                }
                else
                {
                    TxtGameStatus.Text = "Scanning for game...";
                    TxtGameStatus.Foreground = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0x7E, 0x94, 0xAD));
                    TxtProcessId.Text = "--";
                    TxtProcessId.Foreground = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0x95, 0xA7, 0xBC));
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
                2000,
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
                "HRZN Controller Companion",
                "Minimized to system tray. Companion is active in the background.",
                Forms.ToolTipIcon.Info
            );
        }

        public void RestoreFromTray()
        {
            Show();
            Visibility = Visibility.Visible;
            WindowState = WindowState.Normal;
            Activate();
            Topmost = true;
            Topmost = false;
            Focus();
        }

        private void MainWindow_Closing(object? sender, System.ComponentModel.CancelEventArgs e)
        {
            ExitApplication();
        }

        private void ExitApplication()
        {
            _isExplicitExit = true;
            _notifyIcon.Visible = false;
            _notifyIcon.Dispose();

            _beaconBroadcaster.Dispose();
            _gameDetector.Dispose();
            _udpReceiver.Dispose();
            _syncServer.Dispose();
            _dispatcher.Dispose();
            _xbox.Dispose();

            System.Windows.Application.Current.Shutdown();
        }

        private void InitAmbientBackground()
        {
            try
            {
                string path = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "Assets", "Images", "forzahorizon1222.jpg");
                BitmapImage bmp = new BitmapImage();
                bmp.BeginInit();
                if (System.IO.File.Exists(path))
                {
                    bmp.UriSource = new Uri(path, UriKind.Absolute);
                }
                else
                {
                    bmp.UriSource = new Uri("pack://application:,,,/Assets/Images/forzahorizon1222.jpg", UriKind.Absolute);
                }
                bmp.CacheOption = BitmapCacheOption.OnLoad;
                bmp.EndInit();
                bmp.Freeze();
                ImgAmbientBg.Source = bmp;
            }
            catch { }
        }

        private string GetLocalIpAddress()
        {
            try
            {
                var interfaces = NetworkInterface.GetAllNetworkInterfaces()
                    .Where(nic => nic.OperationalStatus == OperationalStatus.Up &&
                                  nic.NetworkInterfaceType != NetworkInterfaceType.Loopback)
                    .Where(nic =>
                    {
                        string desc = nic.Description.ToLower();
                        string name = nic.Name.ToLower();
                        return !desc.Contains("virtualbox") &&
                               !desc.Contains("vmware") &&
                               !desc.Contains("hyper-v") &&
                               !desc.Contains("vethernet") &&
                               !desc.Contains("wsl") &&
                               !name.Contains("virtualbox") &&
                               !name.Contains("vethernet") &&
                               !name.Contains("wsl");
                    })
                    .OrderByDescending(nic => nic.GetIPProperties().GatewayAddresses.Count > 0 ? 1 : 0)
                    .ThenByDescending(nic => nic.NetworkInterfaceType == NetworkInterfaceType.Wireless80211 ? 2 : (nic.NetworkInterfaceType == NetworkInterfaceType.Ethernet ? 1 : 0));

                foreach (var nic in interfaces)
                {
                    var ipProps = nic.GetIPProperties();
                    foreach (var addr in ipProps.UnicastAddresses)
                    {
                        if (addr.Address.AddressFamily == AddressFamily.InterNetwork)
                        {
                            string ipStr = addr.Address.ToString();
                            if (!ipStr.StartsWith("127.") && !ipStr.StartsWith("169.254.") && !ipStr.StartsWith("192.168.56."))
                            {
                                return ipStr;
                            }
                        }
                    }
                }
            }
            catch { }

            try
            {
                using var socket = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, 0);
                socket.Connect("8.8.8.8", 65530);
                if (socket.LocalEndPoint is IPEndPoint endPoint)
                {
                    string fallbackIp = endPoint.Address.ToString();
                    if (!fallbackIp.StartsWith("192.168.56."))
                    {
                        return fallbackIp;
                    }
                }
            }
            catch { }

            return "127.0.0.1";
        }
    }
}
