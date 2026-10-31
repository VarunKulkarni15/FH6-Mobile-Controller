using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Threading;
using WheelPad.Core.Models;

namespace WheelPad.Core.Services
{
    public interface IVirtualXboxController
    {
        bool IsConnected { get; }
        void Connect();
        void Disconnect();
        void SetAxis(XboxButton axis, short value);
        void SetTrigger(XboxButton trigger, byte value);
        void SetButton(XboxButton button, bool pressed);
        void ResetNeutral();
    }

    public class InputDispatchedEventArgs : EventArgs
    {
        public float Steering { get; set; }
        public float Throttle { get; set; }
        public float Brake { get; set; }
        public bool Handbrake { get; set; }
    }

    public class InputDispatcher : IDisposable
    {
        public event EventHandler<InputDispatchedEventArgs>? InputDispatched;

        public void NotifyState(float steering, float throttle, float brake, bool handbrake)
        {
            InputDispatched?.Invoke(this, new InputDispatchedEventArgs
            {
                Steering = steering,
                Throttle = throttle,
                Brake = brake,
                Handbrake = handbrake
            });
        }

        private readonly ProfileManager _profileManager;
        private readonly IVirtualXboxController? _xboxController;
        private readonly ConcurrentDictionary<int, bool> _activeKeysDown = new();
        private readonly ConcurrentDictionary<string, bool> _toggleStates = new();
        private readonly object _stateLock = new();

        public InputDispatcher(ProfileManager profileManager, IVirtualXboxController? xboxController = null)
        {
            _profileManager = profileManager;
            _xboxController = xboxController;
            _xboxController?.Connect();
        }

        public void DispatchAnalogInput(string actionId, float normalizedValue)
        {
            var action = FindAction(actionId);
            if (action == null) return;

            normalizedValue = Math.Clamp(normalizedValue, -1.0f, 1.0f);

            if (action.BindingType is BindingType.XboxController or BindingType.Composite)
            {
                if (_xboxController != null && _xboxController.IsConnected)
                {
                    if (action.XboxTarget == XboxButton.LeftThumbX)
                    {
                        var cfg = _profileManager.ActiveProfile.SteeringConfig;
                        short rawAxis = ApplySteeringCurve(normalizedValue, cfg);
                        _xboxController.SetAxis(XboxButton.LeftThumbX, rawAxis);
                    }
                    else if (action.XboxTarget == XboxButton.RightTrigger)
                    {
                        byte triggerVal = (byte)(Math.Clamp(normalizedValue, 0.0f, 1.0f) * 255f);
                        _xboxController.SetTrigger(XboxButton.RightTrigger, triggerVal);
                    }
                    else if (action.XboxTarget == XboxButton.LeftTrigger)
                    {
                        byte triggerVal = (byte)(Math.Clamp(normalizedValue, 0.0f, 1.0f) * 255f);
                        _xboxController.SetTrigger(XboxButton.LeftTrigger, triggerVal);
                    }
                }
            }
        }

        public void DispatchDigitalInput(string actionId, bool isPressed)
        {
            if (actionId == "auto_drive")
            {
                if (isPressed)
                {
                    TriggerAutoDriveSequence();
                }
                return;
            }

            var action = FindAction(actionId);
            if (action == null) return;

            // Handle behavior
            bool shouldActuate = isPressed;

            if (action.Behavior == InputBehavior.Toggle)
            {
                if (!isPressed) return; // Only trigger on leading edge
                bool current = _toggleStates.GetOrAdd(actionId, false);
                shouldActuate = !current;
                _toggleStates[actionId] = shouldActuate;
            }

            // 1. Dispatch Xbox Controller target if mapped
            if ((action.BindingType == BindingType.XboxController || action.BindingType == BindingType.Composite)
                && action.XboxTarget != XboxButton.None && _xboxController != null && _xboxController.IsConnected)
            {
                _xboxController.SetButton(action.XboxTarget, shouldActuate);
            }

            // 2. Dispatch Keyboard target if mapped (e.g. Handbrake -> Space)
            if ((action.BindingType == BindingType.KeyboardKey || action.BindingType == BindingType.Composite)
                && action.KeyboardKeyCode > 0)
            {
                SendWindowsKey(action.KeyboardKeyCode, shouldActuate);
            }
        }

        public void ResetAllToNeutral()
        {
            lock (_stateLock)
            {
                // 1. Reset virtual Xbox controller
                _xboxController?.ResetNeutral();

                // 2. Release any currently depressed keyboard keys
                foreach (var vk in _activeKeysDown.Keys)
                {
                    SendWindowsKey(vk, isDown: false);
                }
                _activeKeysDown.Clear();
                _toggleStates.Clear();
            }
        }

        private GameAction? FindAction(string actionId)
        {
            var found = _profileManager.ActiveProfile?.Actions.Find(a => a.Id == actionId);
            if (found != null) return found;

            return actionId switch
            {
                "steer" => new GameAction { Id = "steer", InputType = InputType.AnalogAxis, XboxTarget = XboxButton.LeftThumbX },
                "throttle" => new GameAction { Id = "throttle", InputType = InputType.AnalogTrigger, XboxTarget = XboxButton.RightTrigger },
                "brake" => new GameAction { Id = "brake", InputType = InputType.AnalogTrigger, XboxTarget = XboxButton.LeftTrigger },
                "handbrake" or "btn_a" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, XboxTarget = XboxButton.A },
                "btn_b" => new GameAction { Id = "btn_b", InputType = InputType.DigitalButton, XboxTarget = XboxButton.B },
                "btn_x" => new GameAction { Id = "btn_x", InputType = InputType.DigitalButton, XboxTarget = XboxButton.X },
                "rewind" or "btn_y" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, XboxTarget = XboxButton.Y },
                "btn_lb" => new GameAction { Id = "btn_lb", InputType = InputType.DigitalButton, XboxTarget = XboxButton.LeftBumper },
                "camera" or "btn_rb" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, XboxTarget = XboxButton.RightBumper },
                "btn_ls" or "horn" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, XboxTarget = XboxButton.LeftThumbClick },
                "btn_rs" or "look_back" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, XboxTarget = XboxButton.RightThumbClick },
                "map" or "btn_back" or "btn_view" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, XboxTarget = XboxButton.Back },
                "pause" or "btn_start" or "btn_menu" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, XboxTarget = XboxButton.Start },
                "dpad_up" => new GameAction { Id = "dpad_up", InputType = InputType.DigitalButton, XboxTarget = XboxButton.DpadUp },
                "dpad_down" => new GameAction { Id = "dpad_down", InputType = InputType.DigitalButton, XboxTarget = XboxButton.DpadDown },
                "dpad_left" => new GameAction { Id = "dpad_left", InputType = InputType.DigitalButton, XboxTarget = XboxButton.DpadLeft },
                "dpad_right" => new GameAction { Id = "dpad_right", InputType = InputType.DigitalButton, XboxTarget = XboxButton.DpadRight },
                "guide" => new GameAction { Id = "guide", InputType = InputType.DigitalButton, XboxTarget = XboxButton.Guide },
                "auto_drive" => new GameAction { Id = "auto_drive", Name = "Auto Drive", InputType = InputType.DigitalButton },
                _ => null
            };
        }

        public void TriggerAutoDriveSequence()
        {
            Task.Run(async () =>
            {
                try
                {
                    // 1. Press C (0x43)
                    SendWindowsKey(0x43, isDown: true);
                    await Task.Delay(60);
                    SendWindowsKey(0x43, isDown: false);

                    await Task.Delay(100);

                    // 2. Press NumPad 2 (0x62)
                    SendWindowsKey(0x62, isDown: true);
                    await Task.Delay(60);
                    SendWindowsKey(0x62, isDown: false);
                }
                catch { }
            });
        }

        private static short ApplySteeringCurve(float input, SteeringConfig cfg)
        {
            float abs = Math.Abs(input);
            if (abs < cfg.DeadzonePercent)
            {
                return 0;
            }

            // Rescale range past deadzone
            float normalized = (abs - cfg.DeadzonePercent) / (1.0f - cfg.DeadzonePercent);

            // Apply exponential gamma curve
            float curved = MathF.Pow(normalized, cfg.GammaCurve);
            float signed = Math.Sign(input) * curved;

            int raw = (int)(signed * 32767f);
            return (short)Math.Clamp(raw, -32768, 32767);
        }

        #region Windows SendInput Win32 P/Invoke

        [StructLayout(LayoutKind.Sequential)]
        private struct KEYBDINPUT
        {
            public ushort wVk;
            public ushort wScan;
            public uint dwFlags;
            public uint time;
            public IntPtr dwExtraInfo;
        }

        [StructLayout(LayoutKind.Explicit)]
        private struct INPUT
        {
            [FieldOffset(0)] public uint type;
            [FieldOffset(8)] public KEYBDINPUT ki;
        }

        private const uint INPUT_KEYBOARD = 1;
        private const uint KEYEVENTF_KEYUP = 0x0002;
        private const uint KEYEVENTF_SCANCODE = 0x0008;

        [DllImport("user32.dll", SetLastError = true)]
        private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

        [DllImport("user32.dll")]
        private static extern uint MapVirtualKeyW(uint uCode, uint uMapType);

        private void SendWindowsKey(int virtualKeyCode, bool isDown)
        {
            if (virtualKeyCode <= 0) return;

            ushort vk = (ushort)virtualKeyCode;
            ushort scanCode = (ushort)MapVirtualKeyW(vk, 0);

            var input = new INPUT
            {
                type = INPUT_KEYBOARD,
                ki = new KEYBDINPUT
                {
                    wVk = vk,
                    wScan = scanCode,
                    dwFlags = isDown ? 0 : KEYEVENTF_KEYUP,
                    time = 0,
                    dwExtraInfo = IntPtr.Zero
                }
            };

            SendInput(1, new[] { input }, Marshal.SizeOf(typeof(INPUT)));

            if (isDown)
            {
                _activeKeysDown[virtualKeyCode] = true;
            }
            else
            {
                _activeKeysDown.TryRemove(virtualKeyCode, out _);
            }
        }

        #endregion

        public void Dispose()
        {
            ResetAllToNeutral();
            _xboxController?.Disconnect();
        }
    }
}

