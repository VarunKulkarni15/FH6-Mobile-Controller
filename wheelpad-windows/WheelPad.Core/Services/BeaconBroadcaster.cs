using System;
using System.Collections.Generic;
using System.Net;
using System.Net.NetworkInformation;
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
                _broadcaster.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
                _broadcaster.Client.Bind(new IPEndPoint(IPAddress.Any, BeaconPort));

                // Send broadcast every 600ms across all active interfaces for ultra-fast discovery
                _timer = new Timer(SendBeaconCallback, null, 100, 600);

                // Listen for unicast/broadcast discovery ping from phone
                _ = ListenForPingAsync(_cts.Token);

                LogMessage?.Invoke(this, $"[Beacon] Auto-discovery beacon active on port {BeaconPort} for IP {_localIp}");
            }
            catch (Exception ex)
            {
                LogMessage?.Invoke(this, $"[Beacon] Could not bind beacon port {BeaconPort}: {ex.Message}");
            }
        }

        private List<(IPAddress ip, IPAddress broadcast)> GetActiveInterfaces()
        {
            var list = new List<(IPAddress ip, IPAddress broadcast)>();
            try
            {
                foreach (var nic in NetworkInterface.GetAllNetworkInterfaces())
                {
                    if (nic.OperationalStatus != OperationalStatus.Up ||
                        nic.NetworkInterfaceType == NetworkInterfaceType.Loopback)
                        continue;

                    string desc = nic.Description.ToLower();
                    string name = nic.Name.ToLower();
                    if (desc.Contains("virtualbox") || desc.Contains("vmware") || desc.Contains("hyper-v") ||
                        desc.Contains("vethernet") || desc.Contains("wsl") || name.Contains("virtualbox") ||
                        name.Contains("vethernet") || name.Contains("wsl"))
                        continue;

                    var ipProps = nic.GetIPProperties();
                    foreach (var u in ipProps.UnicastAddresses)
                    {
                        if (u.Address.AddressFamily == AddressFamily.InterNetwork)
                        {
                            var ip = u.Address;
                            string ipStr = ip.ToString();
                            if (ipStr.StartsWith("127.") || ipStr.StartsWith("169.254.") || ipStr.StartsWith("192.168.56."))
                                continue;

                            IPAddress broadcast = IPAddress.Broadcast;
                            if (u.IPv4Mask != null)
                            {
                                byte[] ipBytes = ip.GetAddressBytes();
                                byte[] maskBytes = u.IPv4Mask.GetAddressBytes();
                                byte[] bcastBytes = new byte[4];
                                for (int i = 0; i < 4; i++)
                                {
                                    bcastBytes[i] = (byte)(ipBytes[i] | (maskBytes[i] ^ 255));
                                }
                                broadcast = new IPAddress(bcastBytes);
                            }
                            list.Add((ip, broadcast));
                        }
                    }
                }
            }
            catch { }
            return list;
        }

        private void SendBeaconCallback(object? state)
        {
            try
            {
                if (_broadcaster == null) return;

                var interfaces = GetActiveInterfaces();
                if (interfaces.Count == 0)
                {
                    // Fallback to standard broadcast
                    string json = $"{{\"service\":\"wheelpad\",\"ip\":\"{_localIp}\",\"tcpPort\":{_tcpPort},\"udpPort\":{_udpPort},\"pc\":\"{Environment.MachineName}\"}}";
                    byte[] bytes = Encoding.UTF8.GetBytes($"WHEELPAD_BEACON:{json}");
                    _broadcaster.Send(bytes, bytes.Length, new IPEndPoint(IPAddress.Broadcast, BeaconPort));
                    _broadcaster.Send(bytes, bytes.Length, new IPEndPoint(IPAddress.Broadcast, 4242));
                    return;
                }

                foreach (var iface in interfaces)
                {
                    string json = $"{{\"service\":\"wheelpad\",\"ip\":\"{iface.ip}\",\"tcpPort\":{_tcpPort},\"udpPort\":{_udpPort},\"pc\":\"{Environment.MachineName}\"}}";
                    byte[] bytes = Encoding.UTF8.GetBytes($"WHEELPAD_BEACON:{json}");

                    // Broadcast to interface-specific broadcast address (Wi-Fi, Bluetooth, USB)
                    _broadcaster.Send(bytes, bytes.Length, new IPEndPoint(iface.broadcast, BeaconPort));
                    _broadcaster.Send(bytes, bytes.Length, new IPEndPoint(iface.broadcast, 4242));

                    // Also broadcast to standard 255.255.255.255
                    _broadcaster.Send(bytes, bytes.Length, new IPEndPoint(IPAddress.Broadcast, BeaconPort));
                    _broadcaster.Send(bytes, bytes.Length, new IPEndPoint(IPAddress.Broadcast, 4242));
                }
            }
            catch { }
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
                        // Find matching adapter IP for sender's subnet
                        string replyIp = _localIp;
                        var interfaces = GetActiveInterfaces();
                        var senderBytes = result.RemoteEndPoint.Address.GetAddressBytes();

                        foreach (var iface in interfaces)
                        {
                            var ifaceBytes = iface.ip.GetAddressBytes();
                            if (senderBytes.Length == 4 && ifaceBytes.Length == 4)
                            {
                                // Match first 2 or 3 octets
                                if (senderBytes[0] == ifaceBytes[0] && senderBytes[1] == ifaceBytes[1])
                                {
                                    replyIp = iface.ip.ToString();
                                    break;
                                }
                            }
                        }

                        string replyJson = $"{{\"service\":\"wheelpad\",\"ip\":\"{replyIp}\",\"tcpPort\":{_tcpPort},\"udpPort\":{_udpPort},\"pc\":\"{Environment.MachineName}\"}}";
                        byte[] replyBytes = Encoding.UTF8.GetBytes($"WHEELPAD_REPLY:{replyJson}");
                        await _broadcaster.SendAsync(replyBytes, replyBytes.Length, result.RemoteEndPoint);
                        LogMessage?.Invoke(this, $"[Beacon] Auto-discovery response sent to {result.RemoteEndPoint} (reporting IP {replyIp})");
                    }
                }
                catch (OperationCanceledException)
                {
                    break;
                }
                catch { }
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
