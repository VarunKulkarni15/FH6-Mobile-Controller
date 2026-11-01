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
        public const int WatchdogTimeoutMs = 1500; // 1.5s tolerance for wireless jitter so throttle/brake never randomly drop

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
        private uint _lastButtons = 0xFFFFFFFF;
        private bool _lastHandbrake = false;

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
            // Minimum binary packet size is 24 bytes (legacy) or 30 bytes (extended dual sticks)
            if (data.Length < 24) return;

            ushort magic = BitConverter.ToUInt16(data, 0);
            if (magic != PacketMagic) return;

            bool isExtended = data.Length >= 30;
            int crcOffset = isExtended ? 28 : 22;

            // Verify checksum
            ushort expectedCrc = BitConverter.ToUInt16(data, crcOffset);
            ushort computedCrc = ComputeCrc16(data, 0, crcOffset);
            if (expectedCrc != computedCrc) return;

            uint seq = BitConverter.ToUInt32(data, 2);
            // Allow packets that are newer, or allow if sequence wrapped past 0xFFFFFFFF
            long delta = (long)seq - (long)_lastSequenceNumber;
            if (delta < 0 && delta > -30 && _lastSequenceNumber > 0)
            {
                // Minor out-of-order packet within 30 frames; drop stale duplicate
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

            if (isExtended)
            {
                short leftThumbYRaw = BitConverter.ToInt16(data, 22);
                short rightThumbXRaw = BitConverter.ToInt16(data, 24);
                short rightThumbYRaw = BitConverter.ToInt16(data, 26);

                _inputDispatcher.DispatchAnalogInput("left_stick_y", leftThumbYRaw / 32767f);
                _inputDispatcher.DispatchAnalogInput("right_stick_x", rightThumbXRaw / 32767f);
                _inputDispatcher.DispatchAnalogInput("right_stick_y", rightThumbYRaw / 32767f);
            }

            // Separate handbrake & A button dispatch
            bool handbrakePressed = handbrakePressure > 30 || ((buttons & 0x01) != 0);

            // Only dispatch digital buttons when their state changes (avoids flooding 120Hz redundant calls)
            if (buttons != _lastButtons || handbrakePressed != _lastHandbrake)
            {
                uint changed = (buttons ^ _lastButtons);
                _lastButtons = buttons;
                _lastHandbrake = handbrakePressed;

                _inputDispatcher.DispatchDigitalInput("handbrake", handbrakePressed);
                _inputDispatcher.DispatchDigitalInput("btn_a", handbrakePressed);

                if ((changed & (1 << 1)) != 0)
                {
                    bool s = (buttons & (1 << 1)) != 0;
                    _inputDispatcher.DispatchDigitalInput("rewind", s);
                    _inputDispatcher.DispatchDigitalInput("btn_y", s);
                }
                if ((changed & (1 << 2)) != 0)
                {
                    bool s = (buttons & (1 << 2)) != 0;
                    _inputDispatcher.DispatchDigitalInput("camera", s);
                    _inputDispatcher.DispatchDigitalInput("btn_rb", s);
                }
                if ((changed & (1 << 3)) != 0)
                {
                    bool s = (buttons & (1 << 3)) != 0;
                    _inputDispatcher.DispatchDigitalInput("map", s);
                    _inputDispatcher.DispatchDigitalInput("btn_back", s);
                    _inputDispatcher.DispatchDigitalInput("btn_view", s);
                }
                if ((changed & (1 << 4)) != 0)
                {
                    bool s = (buttons & (1 << 4)) != 0;
                    _inputDispatcher.DispatchDigitalInput("pause", s);
                    _inputDispatcher.DispatchDigitalInput("btn_start", s);
                    _inputDispatcher.DispatchDigitalInput("btn_menu", s);
                }
                if ((changed & (1 << 5)) != 0)
                {
                    bool s = (buttons & (1 << 5)) != 0;
                    _inputDispatcher.DispatchDigitalInput("btn_x", s);
                    _inputDispatcher.DispatchDigitalInput("activate", s);
                    _inputDispatcher.DispatchDigitalInput("shift_down", s);
                }
                if ((changed & (1 << 6)) != 0)
                {
                    bool s = (buttons & (1 << 6)) != 0;
                    _inputDispatcher.DispatchDigitalInput("btn_b", s);
                    _inputDispatcher.DispatchDigitalInput("shift_up", s);
                }
                if ((changed & (1 << 7)) != 0)
                {
                    bool s = (buttons & (1 << 7)) != 0;
                    _inputDispatcher.DispatchDigitalInput("btn_lb", s);
                    _inputDispatcher.DispatchDigitalInput("clutch", s);
                }
                if ((changed & (1 << 8)) != 0)
                {
                    bool s = (buttons & (1 << 8)) != 0;
                    _inputDispatcher.DispatchDigitalInput("btn_ls", s);
                    _inputDispatcher.DispatchDigitalInput("convertible", s);
                }
                if ((changed & (1 << 9)) != 0)
                {
                    bool s = (buttons & (1 << 9)) != 0;
                    _inputDispatcher.DispatchDigitalInput("btn_rs", s);
                    _inputDispatcher.DispatchDigitalInput("horn", s);
                }
                if ((changed & (1 << 10)) != 0)
                {
                    bool s = (buttons & (1 << 10)) != 0;
                    _inputDispatcher.DispatchDigitalInput("dpad_up", s);
                    _inputDispatcher.DispatchDigitalInput("photo_mode", s);
                }
                if ((changed & (1 << 11)) != 0)
                {
                    bool s = (buttons & (1 << 11)) != 0;
                    _inputDispatcher.DispatchDigitalInput("dpad_down", s);
                    _inputDispatcher.DispatchDigitalInput("anna", s);
                }
                if ((changed & (1 << 12)) != 0)
                {
                    bool s = (buttons & (1 << 12)) != 0;
                    _inputDispatcher.DispatchDigitalInput("dpad_left", s);
                    _inputDispatcher.DispatchDigitalInput("forza_link", s);
                }
                if ((changed & (1 << 13)) != 0)
                {
                    bool s = (buttons & (1 << 13)) != 0;
                    _inputDispatcher.DispatchDigitalInput("dpad_right", s);
                    _inputDispatcher.DispatchDigitalInput("radio_next", s);
                }
                if ((changed & (1 << 14)) != 0) _inputDispatcher.DispatchDigitalInput("guide", (buttons & (1 << 14)) != 0);
                if ((changed & (1 << 15)) != 0) _inputDispatcher.DispatchDigitalInput("auto_drive", (buttons & (1 << 15)) != 0);
                if ((changed & (1 << 16)) != 0)
                {
                    bool s = (buttons & (1 << 16)) != 0;
                    _inputDispatcher.DispatchDigitalInput("look_back", s);
                }
            }

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

