using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Threading;
using System.Threading.Tasks;
using WheelPad.Core.Models;

namespace WheelPad.Core.Services
{
    public class UdpFastStreamReceiver : IDisposable
    {
        public const int DefaultUdpPort = 4242;
        public const ushort PacketMagic = 0x5750; // 'WP'
        public const int WatchdogTimeoutMs = 500; // Increased to 500ms to tolerate wireless jitter while preserving safety

        private readonly InputDispatcher _inputDispatcher;
        private readonly int _port;
        private UdpClient? _udpClient;
        private CancellationTokenSource? _cts;
        private readonly Timer _watchdogTimer;
        private long _lastPacketTimeTicks;
        private uint _lastSequenceNumber;

        public event EventHandler<int>? PacketsPerSecondUpdated;
        public event EventHandler<string>? LogMessage;

        private int _packetCounter;
        private readonly Timer _statsTimer;

        public UdpFastStreamReceiver(InputDispatcher inputDispatcher, int port = DefaultUdpPort)
        {
            _inputDispatcher = inputDispatcher;
            _port = port;

            _watchdogTimer = new Timer(WatchdogCallback, null, Timeout.Infinite, Timeout.Infinite);
            _statsTimer = new Timer(StatsCallback, null, 1000, 1000);
        }

        public void Start()
        {
            _cts = new CancellationTokenSource();
            _udpClient = new UdpClient(new IPEndPoint(IPAddress.Any, _port));

            LogMessage?.Invoke(this, $"[UdpReceiver] FastStream listening on UDP port {_port}...");
            _watchdogTimer.Change(WatchdogTimeoutMs, WatchdogTimeoutMs);

            _ = ReceiveLoopAsync(_cts.Token);
        }

        private async Task ReceiveLoopAsync(CancellationToken token)
        {
            while (!token.IsCancellationRequested && _udpClient != null)
            {
                try
                {
                    var result = await _udpClient.ReceiveAsync(token);
                    ProcessPacket(result.Buffer);
                }
                catch (OperationCanceledException)
                {
                    break;
                }
                catch (Exception ex)
                {
                    LogMessage?.Invoke(this, $"[UdpReceiver] Receive error: {ex.Message}");
                }
            }
        }

        private void ProcessPacket(byte[] data)
        {
            // Minimum binary packet size is 24 bytes
            if (data.Length < 24) return;

            ushort magic = BitConverter.ToUInt16(data, 0);
            if (magic != PacketMagic) return;

            // Verify checksum
            ushort expectedCrc = BitConverter.ToUInt16(data, 22);
            ushort computedCrc = ComputeCrc16(data, 0, 22);
            if (expectedCrc != computedCrc) return;

            uint seq = BitConverter.ToUInt32(data, 2);
            // Ignore out-of-order stale packets if seq wrapped or is older
            if (seq < _lastSequenceNumber && (_lastSequenceNumber - seq) < 10000)
            {
                return;
            }
            _lastSequenceNumber = seq;

            Interlocked.Exchange(ref _lastPacketTimeTicks, DateTime.UtcNow.Ticks);
            Interlocked.Increment(ref _packetCounter);

            // Read analog inputs
            short steerRaw = BitConverter.ToInt16(data, 14); // -32768 to 32767
            byte throttle = data[16];                         // 0 to 255
            byte brake = data[17];                            // 0 to 255
            byte handbrakePressure = data[18];               // 0 to 255

            // Read 24-bit button bitmask
            uint buttons = (uint)(data[19] | (data[20] << 8) | (data[21] << 16));

            // Dispatch inputs
            float normalizedSteer = steerRaw / 32767f;
            float normalizedThrottle = throttle / 255f;
            float normalizedBrake = brake / 255f;

            _inputDispatcher.DispatchAnalogInput("steer", normalizedSteer);
            _inputDispatcher.DispatchAnalogInput("throttle", normalizedThrottle);
            _inputDispatcher.DispatchAnalogInput("brake", normalizedBrake);

            // Separate handbrake & A button dispatch
            bool handbrakePressed = handbrakePressure > 30 || ((buttons & 0x01) != 0);
            _inputDispatcher.DispatchDigitalInput("handbrake", handbrakePressed);
            _inputDispatcher.DispatchDigitalInput("btn_a", handbrakePressed);

            // Dispatch full Xbox 360 controller button matrix
            bool rewindPressed = (buttons & (1 << 1)) != 0;
            _inputDispatcher.DispatchDigitalInput("rewind", rewindPressed);
            _inputDispatcher.DispatchDigitalInput("btn_y", rewindPressed);

            bool cameraPressed = (buttons & (1 << 2)) != 0;
            _inputDispatcher.DispatchDigitalInput("camera", cameraPressed);
            _inputDispatcher.DispatchDigitalInput("btn_rb", cameraPressed);

            bool mapPressed = (buttons & (1 << 3)) != 0;
            _inputDispatcher.DispatchDigitalInput("map", mapPressed);
            _inputDispatcher.DispatchDigitalInput("btn_back", mapPressed);
            _inputDispatcher.DispatchDigitalInput("btn_view", mapPressed);

            bool pausePressed = (buttons & (1 << 4)) != 0;
            _inputDispatcher.DispatchDigitalInput("pause", pausePressed);
            _inputDispatcher.DispatchDigitalInput("btn_start", pausePressed);
            _inputDispatcher.DispatchDigitalInput("btn_menu", pausePressed);

            _inputDispatcher.DispatchDigitalInput("btn_x", (buttons & (1 << 5)) != 0);
            _inputDispatcher.DispatchDigitalInput("btn_b", (buttons & (1 << 6)) != 0);
            _inputDispatcher.DispatchDigitalInput("btn_lb", (buttons & (1 << 7)) != 0);
            _inputDispatcher.DispatchDigitalInput("btn_ls", (buttons & (1 << 8)) != 0);
            _inputDispatcher.DispatchDigitalInput("horn", (buttons & (1 << 8)) != 0);
            _inputDispatcher.DispatchDigitalInput("btn_rs", (buttons & (1 << 9)) != 0);
            _inputDispatcher.DispatchDigitalInput("look_back", (buttons & (1 << 9)) != 0);
            _inputDispatcher.DispatchDigitalInput("dpad_up", (buttons & (1 << 10)) != 0);
            _inputDispatcher.DispatchDigitalInput("dpad_down", (buttons & (1 << 11)) != 0);
            _inputDispatcher.DispatchDigitalInput("dpad_left", (buttons & (1 << 12)) != 0);
            _inputDispatcher.DispatchDigitalInput("dpad_right", (buttons & (1 << 13)) != 0);
            _inputDispatcher.DispatchDigitalInput("guide", (buttons & (1 << 14)) != 0);
            _inputDispatcher.DispatchDigitalInput("auto_drive", (buttons & (1 << 15)) != 0);

            _inputDispatcher.NotifyState(normalizedSteer, normalizedThrottle, normalizedBrake, handbrakePressed);
        }

        private void WatchdogCallback(object? state)
        {
            long lastTicks = Interlocked.Read(ref _lastPacketTimeTicks);
            if (lastTicks == 0) return;

            var elapsed = TimeSpan.FromTicks(DateTime.UtcNow.Ticks - lastTicks);
            if (elapsed.TotalMilliseconds > WatchdogTimeoutMs)
            {
                // Timeout exceeded! Zero inputs immediately for safety
                _inputDispatcher.ResetAllToNeutral();
                Interlocked.Exchange(ref _lastPacketTimeTicks, 0);
            }
        }

        private void StatsCallback(object? state)
        {
            int pps = Interlocked.Exchange(ref _packetCounter, 0);
            PacketsPerSecondUpdated?.Invoke(this, pps);
        }

        private static ushort ComputeCrc16(byte[] bytes, int offset, int length)
        {
            ushort crc = 0xFFFF;
            for (int i = offset; i < offset + length; i++)
            {
                crc ^= (ushort)(bytes[i] << 8);
                for (int j = 0; j < 8; j++)
                {
                    if ((crc & 0x8000) != 0)
                        crc = (ushort)((crc << 1) ^ 0x1021);
                    else
                        crc <<= 1;
                }
            }
            return crc;
        }

        public void Stop()
        {
            _cts?.Cancel();
            _udpClient?.Close();
            _udpClient = null;
            _watchdogTimer.Change(Timeout.Infinite, Timeout.Infinite);
            _statsTimer.Change(Timeout.Infinite, Timeout.Infinite);
        }

        public void Dispose()
        {
            Stop();
            _watchdogTimer.Dispose();
            _statsTimer.Dispose();
            _cts?.Dispose();
        }
    }
}

