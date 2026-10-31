using System;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace WheelPad.Core.Services
{
    public class BeaconBroadcaster : IDisposable
    {
        public const int BeaconPort = 4240;
        private readonly int _tcpPort;
        private readonly int _udpPort;
        private UdpClient? _broadcaster;
        private Timer? _timer;
        private CancellationTokenSource? _cts;
        private string _localIp = "127.0.0.1";

        public event EventHandler<string>? LogMessage;

        public BeaconBroadcaster(int tcpPort = 4243, int udpPort = 4242)
        {
            _tcpPort = tcpPort;
            _udpPort = udpPort;
        }

        public void Start(string localIp)
        {
            _localIp = localIp;
            _cts = new CancellationTokenSource();

            try
            {
                _broadcaster = new UdpClient();
                _broadcaster.EnableBroadcast = true;
                _broadcaster.Client.Bind(new IPEndPoint(IPAddress.Any, BeaconPort));

                // Send broadcast every 1000ms
                _timer = new Timer(SendBeaconCallback, null, 100, 1000);

                // Listen for unicast/broadcast discovery ping from phone
                _ = ListenForPingAsync(_cts.Token);

                LogMessage?.Invoke(this, $"[Beacon] Auto-discovery beacon active on port {BeaconPort} for IP {_localIp}");
            }
            catch (Exception ex)
            {
                LogMessage?.Invoke(this, $"[Beacon] Could not bind beacon port {BeaconPort}: {ex.Message}");
            }
        }

        private void SendBeaconCallback(object? state)
        {
            try
            {
                if (_broadcaster == null) return;
                string json = $"{{\"service\":\"wheelpad\",\"ip\":\"{_localIp}\",\"tcpPort\":{_tcpPort},\"udpPort\":{_udpPort},\"pc\":\"{Environment.MachineName}\"}}";
                byte[] bytes = Encoding.UTF8.GetBytes($"WHEELPAD_BEACON:{json}");

                var endPoint = new IPEndPoint(IPAddress.Broadcast, BeaconPort);
                _broadcaster.Send(bytes, bytes.Length, endPoint);

                // Also send to fallback port 4242
                var fallbackEndPoint = new IPEndPoint(IPAddress.Broadcast, 4242);
                _broadcaster.Send(bytes, bytes.Length, fallbackEndPoint);
            }
            catch
            {
                // Ignore transient network errors on broadcast
            }
        }

        private async Task ListenForPingAsync(CancellationToken token)
        {
            while (!token.IsCancellationRequested && _broadcaster != null)
            {
                try
                {
                    var result = await _broadcaster.ReceiveAsync(token);
                    string msg = Encoding.UTF8.GetString(result.Buffer);
                    if (msg.StartsWith("WHEELPAD_DISCOVER") || msg.StartsWith("WHEELPAD_PING"))
                    {
                        string replyJson = $"{{\"service\":\"wheelpad\",\"ip\":\"{_localIp}\",\"tcpPort\":{_tcpPort},\"udpPort\":{_udpPort},\"pc\":\"{Environment.MachineName}\"}}";
                        byte[] replyBytes = Encoding.UTF8.GetBytes($"WHEELPAD_REPLY:{replyJson}");
                        await _broadcaster.SendAsync(replyBytes, replyBytes.Length, result.RemoteEndPoint);
                        LogMessage?.Invoke(this, $"[Beacon] Auto-discovery response sent to {result.RemoteEndPoint}");
                    }
                }
                catch (OperationCanceledException)
                {
                    break;
                }
                catch
                {
                    // Ignore
                }
            }
        }

        public void Dispose()
        {
            _timer?.Dispose();
            _cts?.Cancel();
            _broadcaster?.Dispose();
        }
    }
}

