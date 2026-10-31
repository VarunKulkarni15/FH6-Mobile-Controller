using System;
using System.Net;
using System.Net.Sockets;
using System.Threading;
using System.Threading.Tasks;
using WheelPad.Core.Models;
using WheelPad.Core.Services;

namespace WheelPad.Server
{
    internal class Program
    {
        private static async Task Main(string[] args)
        {
            Console.Title = "WheelPad Companion Server - Forza Horizon 6";
            Console.ForegroundColor = ConsoleColor.Cyan;
            Console.WriteLine(@"
 __          ___               _ _____           _ 
 \ \        / / |             | |  __ \         | |
  \ \  /\  / /| |__   ___  ___| | |__) |_ _  __| |
   \ \/  \/ / | '_ \ / _ \/ _ \ |  ___/ _` |/ _` |
    \  /\  /  | | | |  __/  __/ | |  | (_| | (_| |
     \/  \/   |_| |_|\___|\___|_|_|   \__,_|\__,_|
            FORZA HORIZON 6 TOUCH CONTROLLER
");
            Console.ResetColor();

            string localIp = GetLocalIpAddress();
            Console.WriteLine($"[Network] Host Local IP: {localIp}");
            Console.WriteLine($"[Network] FastStream Port: {UdpFastStreamReceiver.DefaultUdpPort} (UDP)");
            Console.WriteLine($"[Network] Control Sync Port: {ControlSyncServer.DefaultTcpPort} (TCP)");
            Console.WriteLine();

            // 1. Initialize Profile Manager
            var profileManager = new ProfileManager();
            Console.WriteLine($"[Profiles] Loaded {profileManager.Profiles.Count} profile(s). Active: '{profileManager.ActiveProfile.ProfileName}'");

            // 2. Initialize Virtual Xbox Controller
            var xbox = new ViGEmXboxController();
            xbox.Connect();
            if (xbox.IsConnected)
            {
                Console.ForegroundColor = ConsoleColor.Green;
                Console.WriteLine("[Controller] ViGEmBus Virtual Xbox 360 controller connected.");
            }
            else
            {
                Console.ForegroundColor = ConsoleColor.Yellow;
                Console.WriteLine($"[Controller] Virtual controller notice: {xbox.DriverError ?? "Fallback mode active"}");
            }
            Console.ResetColor();

            // 3. Initialize Input Dispatcher
            var dispatcher = new InputDispatcher(profileManager, xbox);

            // 4. Initialize TCP Control Sync Server
            var syncServer = new ControlSyncServer(profileManager, dispatcher);
            syncServer.LogMessage += (s, msg) => Console.WriteLine(msg);
            syncServer.ClientConnectionChanged += (s, connected) =>
            {
                Console.ForegroundColor = connected ? ConsoleColor.Green : ConsoleColor.Red;
                Console.WriteLine($"[Status] Android Companion {(connected ? "CONNECTED" : "DISCONNECTED")}");
                Console.ResetColor();
            };
            syncServer.Start();

            // 5. Initialize UDP FastStream Receiver
            var udpReceiver = new UdpFastStreamReceiver(dispatcher);
            int currentPps = 0;
            udpReceiver.PacketsPerSecondUpdated += (s, pps) => currentPps = pps;
            udpReceiver.LogMessage += (s, msg) => Console.WriteLine(msg);
            udpReceiver.Start();

            // 6. Initialize Game Detector (Auto-detects forzahorizon6.exe)
            var gameDetector = new GameDetector(profileManager, syncServer);
            gameDetector.LogMessage += (s, msg) => Console.WriteLine(msg);
            gameDetector.GameStatusChanged += (s, e) =>
            {
                Console.ForegroundColor = e.IsRunning ? ConsoleColor.Green : ConsoleColor.DarkYellow;
                Console.WriteLine($"[FH6 Detection] Forza Horizon 6 is {(e.IsRunning ? "RUNNING (PID " + e.ProcessId + ")" : "STOPPED")}");
                Console.ResetColor();
            };

            // 7. Initialize Dual-Account Spotify Manager
            var spotifyManager = new SpotifyManager();
            syncServer.Spotify = spotifyManager;
            spotifyManager.LogMessage += (s, msg) => Console.WriteLine(msg);
            spotifyManager.TrackStateUpdated += (s, track) =>
            {
                syncServer.BroadcastEvent(new
                {
                    type = "SPOTIFY_STATE_CHANGED",
                    track
                });
            };

            // 8. Initialize FH6 Universal Radio Mod Client
            var radioService = new UniversalRadioService();
            syncServer.Radio = radioService;
            radioService.LogMessage += (s, msg) => Console.WriteLine(msg);
            radioService.StatusUpdated += (s, radio) =>
            {
                syncServer.BroadcastEvent(new
                {
                    type = "RADIO_STATUS_CHANGED",
                    radio
                });
            };

            Console.WriteLine();
            Console.ForegroundColor = ConsoleColor.DarkCyan;
            Console.WriteLine("-------------------------------------------------------------------");
            Console.WriteLine(" [P] Cycle Profile | [T] Test Handbrake | [S] Next Song | [Q] Exit ");
            Console.WriteLine("-------------------------------------------------------------------");
            Console.ResetColor();

            bool running = true;
            while (running)
            {
                if (Console.KeyAvailable)
                {
                    var key = Console.ReadKey(intercept: true).Key;
                    switch (key)
                    {
                        case ConsoleKey.P:
                            CycleProfile(profileManager);
                            break;
                        case ConsoleKey.T:
                            Console.WriteLine("[Test] Pulsing Handbrake (Space + Xbox A)...");
                            dispatcher.DispatchDigitalInput("handbrake", true);
                            await Task.Delay(200);
                            dispatcher.DispatchDigitalInput("handbrake", false);
                            break;
                        case ConsoleKey.S:
                            Console.WriteLine("[Spotify] Skipping to next track...");
                            await spotifyManager.SendPlaybackCommandAsync("next");
                            break;
                        case ConsoleKey.Q:
                            running = false;
                            break;
                    }
                }

                await Task.Delay(100);
            }

            Console.WriteLine("[Shutdown] Cleaning up controller, detector, and servers...");
            radioService.Dispose();
            spotifyManager.Dispose();
            gameDetector.Dispose();
            udpReceiver.Dispose();
            syncServer.Dispose();
            dispatcher.Dispose();
            xbox.Dispose();
            Console.WriteLine("[Shutdown] Complete.");
        }

        private static void CycleProfile(ProfileManager pm)
        {
            var profiles = pm.Profiles;
            if (profiles.Count <= 1) return;

            int currentIndex = -1;
            for (int i = 0; i < profiles.Count; i++)
            {
                if (profiles[i].ProfileId == pm.ActiveProfile.ProfileId)
                {
                    currentIndex = i;
                    break;
                }
            }

            int nextIndex = (currentIndex + 1) % profiles.Count;
            pm.SetActiveProfile(profiles[nextIndex].ProfileId);
            Console.ForegroundColor = ConsoleColor.Cyan;
            Console.WriteLine($"[Profiles] Switched active profile to: '{pm.ActiveProfile.ProfileName}'");
            Console.ResetColor();
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
