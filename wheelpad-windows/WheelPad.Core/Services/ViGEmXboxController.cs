using System;
using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Targets;
using Nefarius.ViGEm.Client.Targets.Xbox360;
using WheelPad.Core.Models;

namespace WheelPad.Core.Services
{
    public class ViGEmXboxController : IVirtualXboxController, IDisposable
    {
        private ViGEmClient? _client;
        private IXbox360Controller? _controller;
        private bool _isConnected;
        private readonly object _lock = new();

        public bool IsConnected => _isConnected && _controller != null;
        public string? DriverError { get; private set; }

        public void Connect()
        {
            lock (_lock)
            {
                if (_isConnected) return;

                try
                {
                    _client = new ViGEmClient();
                    _controller = _client.CreateXbox360Controller();
                    _controller.Connect();
                    _isConnected = true;
                    DriverError = null;
                    Console.WriteLine("[ViGEmXboxController] Virtual Xbox 360 controller connected successfully via ViGEmBus.");
                }
                catch (Exception ex)
                {
                    DriverError = ex.Message;
                    _isConnected = false;
                    Console.Error.WriteLine($"[ViGEmXboxController] Could not initialize ViGEmBus controller: {ex.Message}");
                    Console.Error.WriteLine("[ViGEmXboxController] Running in software emulation / loopback mode.");
                }
            }
        }

        public void Disconnect()
        {
            lock (_lock)
            {
                if (!_isConnected) return;

                try
                {
                    ResetNeutral();
                    _controller?.Disconnect();
                    _controller = null;
                    _client?.Dispose();
                    _client = null;
                }
                catch (Exception ex)
                {
                    Console.Error.WriteLine($"[ViGEmXboxController] Disconnect error: {ex.Message}");
                }
                finally
                {
                    _isConnected = false;
                }
            }
        }

        public void SetAxis(XboxButton axis, short value)
        {
            if (_controller == null || !_isConnected) return;

            try
            {
                if (axis == XboxButton.LeftThumbX)
                {
                    _controller.SetAxisValue(Xbox360Axis.LeftThumbX, value);
                }
                else if (axis == XboxButton.RightThumbX)
                {
                    _controller.SetAxisValue(Xbox360Axis.RightThumbX, value);
                }
            }
            catch { }
        }

        public void SetTrigger(XboxButton trigger, byte value)
        {
            if (_controller == null || !_isConnected) return;

            try
            {
                if (trigger == XboxButton.RightTrigger)
                {
                    _controller.SetSliderValue(Xbox360Slider.RightTrigger, value);
                }
                else if (trigger == XboxButton.LeftTrigger)
                {
                    _controller.SetSliderValue(Xbox360Slider.LeftTrigger, value);
                }
            }
            catch { }
        }

        private static readonly Xbox360Button[] AllButtons =
        [
            Xbox360Button.A, Xbox360Button.B, Xbox360Button.X, Xbox360Button.Y,
            Xbox360Button.LeftShoulder, Xbox360Button.RightShoulder,
            Xbox360Button.Back, Xbox360Button.Start, Xbox360Button.Guide,
            Xbox360Button.LeftThumb, Xbox360Button.RightThumb,
            Xbox360Button.Up, Xbox360Button.Down, Xbox360Button.Left, Xbox360Button.Right
        ];

        public void SetButton(XboxButton button, bool pressed)
        {
            if (_controller == null || !_isConnected) return;

            try
            {
                var xButton = MapToXbox360Button(button);
                if (xButton != null)
                {
                    _controller.SetButtonState(xButton, pressed);
                }
            }
            catch { }
        }

        public void ResetNeutral()
        {
            if (_controller == null || !_isConnected) return;

            try
            {
                _controller.SetAxisValue(Xbox360Axis.LeftThumbX, 0);
                _controller.SetAxisValue(Xbox360Axis.LeftThumbY, 0);
                _controller.SetAxisValue(Xbox360Axis.RightThumbX, 0);
                _controller.SetAxisValue(Xbox360Axis.RightThumbY, 0);
                _controller.SetSliderValue(Xbox360Slider.RightTrigger, 0);
                _controller.SetSliderValue(Xbox360Slider.LeftTrigger, 0);

                foreach (var btn in AllButtons)
                {
                    _controller.SetButtonState(btn, false);
                }
            }
            catch { }
        }

        private static Xbox360Button? MapToXbox360Button(XboxButton btn)
        {
            return btn switch
            {
                XboxButton.A => Xbox360Button.A,
                XboxButton.B => Xbox360Button.B,
                XboxButton.X => Xbox360Button.X,
                XboxButton.Y => Xbox360Button.Y,
                XboxButton.LeftBumper => Xbox360Button.LeftShoulder,
                XboxButton.RightBumper => Xbox360Button.RightShoulder,
                XboxButton.Back => Xbox360Button.Back,
                XboxButton.Start => Xbox360Button.Start,
                XboxButton.Guide => Xbox360Button.Guide,
                XboxButton.LeftThumbClick => Xbox360Button.LeftThumb,
                XboxButton.RightThumbClick => Xbox360Button.RightThumb,
                XboxButton.DpadUp => Xbox360Button.Up,
                XboxButton.DpadDown => Xbox360Button.Down,
                XboxButton.DpadLeft => Xbox360Button.Left,
                XboxButton.DpadRight => Xbox360Button.Right,
                _ => null
            };
        }

        public void Dispose()
        {
            Disconnect();
        }
    }
}

