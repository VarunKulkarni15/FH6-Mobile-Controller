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
                "throttle" => new GameAction { Id = "throttle", InputType = InputType.AnalogTrigger, XboxTarget = XboxButton.RightTrigger, BindingType = BindingType.Composite, KeyboardKeyCode = 0x57 }, // W
                "brake" => new GameAction { Id = "brake", InputType = InputType.AnalogTrigger, XboxTarget = XboxButton.LeftTrigger, BindingType = BindingType.Composite, KeyboardKeyCode = 0x53 }, // S
                "handbrake" or "btn_a" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, BindingType = BindingType.Composite, XboxTarget = XboxButton.A, KeyboardKeyCode = 0x20 }, // Space (E-Brake)
                "btn_b" => new GameAction { Id = "btn_b", InputType = InputType.DigitalButton, BindingType = BindingType.Composite, XboxTarget = XboxButton.B, KeyboardKeyCode = 0x45 }, // E (Shift Up)
                "btn_x" => new GameAction { Id = "btn_x", InputType = InputType.DigitalButton, BindingType = BindingType.Composite, XboxTarget = XboxButton.X, KeyboardKeyCode = 0x0D }, // Enter (Activate)
                "rewind" or "btn_y" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, BindingType = BindingType.Composite, XboxTarget = XboxButton.Y, KeyboardKeyCode = 0x52 }, // R (Rewind)
                "btn_lb" => new GameAction { Id = "btn_lb", InputType = InputType.DigitalButton, BindingType = BindingType.Composite, XboxTarget = XboxButton.LeftBumper, KeyboardKeyCode = 0x10 }, // Shift (Clutch)
                "camera" or "btn_rb" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, BindingType = BindingType.Composite, XboxTarget = XboxButton.RightBumper, KeyboardKeyCode = 0x09 }, // Tab (Switch Camera)
                "btn_ls" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, BindingType = BindingType.Composite, XboxTarget = XboxButton.LeftThumbClick, KeyboardKeyCode = 0x47 }, // G (Toggle Convertible)
                "btn_rs" or "horn" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, BindingType = BindingType.Composite, XboxTarget = XboxButton.RightThumbClick, KeyboardKeyCode = 0x48 }, // H (Horn)
                "map" or "btn_back" or "btn_view" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, BindingType = BindingType.Composite, XboxTarget = XboxButton.Back, KeyboardKeyCode = 0x4D }, // M (Map)
                "pause" or "btn_start" or "btn_menu" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, BindingType = BindingType.Composite, XboxTarget = XboxButton.Start, KeyboardKeyCode = 0x1B }, // Esc (Pause)
                "dpad_up" => new GameAction { Id = "dpad_up", InputType = InputType.DigitalButton, BindingType = BindingType.Composite, XboxTarget = XboxButton.DpadUp, KeyboardKeyCode = 0x50 }, // P (Photo Mode)
                "dpad_down" => new GameAction { Id = "dpad_down", InputType = InputType.DigitalButton, BindingType = BindingType.Composite, XboxTarget = XboxButton.DpadDown, KeyboardKeyCode = 0x43 }, // C (Activate ANNA)
                "dpad_left" => new GameAction { Id = "dpad_left", InputType = InputType.DigitalButton, BindingType = BindingType.Composite, XboxTarget = XboxButton.DpadLeft, KeyboardKeyCode = 0x56 }, // V (Forza LINK)
                "dpad_right" => new GameAction { Id = "dpad_right", InputType = InputType.DigitalButton, BindingType = BindingType.Composite, XboxTarget = XboxButton.DpadRight, KeyboardKeyCode = 0xBB }, // = (Radio Next)
                "guide" => new GameAction { Id = "guide", InputType = InputType.DigitalButton, XboxTarget = XboxButton.Guide },
                "auto_drive" => new GameAction { Id = "auto_drive", Name = "Auto Drive", InputType = InputType.DigitalButton },
                _ => null
            };
        }

        private int _isAutoDriveRunning = 0;

        public void TriggerAutoDriveSequence()
        {
            if (Interlocked.CompareExchange(ref _isAutoDriveRunning, 1, 0) != 0)
            {
                return;
            }

            Task.Run(async () =>
            {
                try
                {
                    // 1. Activate ANNA:
                    // Keyboard: C (0x43)
                    // Controller: D-Pad Down
                    SendWindowsKey(0x43, isDown: true);
                    _xboxController?.SetButton(XboxButton.DpadDown, true);
                    await Task.Delay(100);
                    SendWindowsKey(0x43, isDown: false);
                    _xboxController?.SetButton(XboxButton.DpadDown, false);

                    await Task.Delay(150);

                    // 2. Select Option 2 (AutoDrive):
                    // Keyboard: '2' (0x32) & NumPad 2 (0x62)
                    // Controller: D-Pad Left (ANNA Option 2 in Forza Horizon)
                    SendWindowsKey(0x62, isDown: true);
                    SendWindowsKey(0x32, isDown: true);
                    _xboxController?.SetButton(XboxButton.DpadLeft, true);
                    await Task.Delay(100);
                    SendWindowsKey(0x62, isDown: false);
                    SendWindowsKey(0x32, isDown: false);
                    _xboxController?.SetButton(XboxButton.DpadLeft, false);
                }
                catch { }
                finally
                {
                    await Task.Delay(600);
                    Interlocked.Exchange(ref _isAutoDriveRunning, 0);
                }
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

        [DllImport("user32.dll")]
        private static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo);

        private void SendWindowsKey(int virtualKeyCode, bool isDown)
        {
            if (virtualKeyCode <= 0) return;

            ushort vk = (ushort)virtualKeyCode;
            ushort scanCode = (ushort)MapVirtualKeyW(vk, 0);

            if (scanCode == 0)
            {
                scanCode = vk switch
                {
                    0x43 => 0x2E, // 'C'
                    0x62 => 0x50, // NumPad 2
                    0x32 => 0x03, // '2'
                    0x20 => 0x39, // Space
                    _ => 0
                };
            }

            uint flags = (isDown ? 0u : KEYEVENTF_KEYUP);
            if (scanCode != 0)
            {
                flags |= KEYEVENTF_SCANCODE;
            }

            var input = new INPUT
            {
                type = INPUT_KEYBOARD,
                ki = new KEYBDINPUT
                {
                    wVk = vk,
                    wScan = scanCode,
                    dwFlags = flags,
                    time = 0,
                    dwExtraInfo = IntPtr.Zero
                }
            };

            SendInput(1, new[] { input }, Marshal.SizeOf(typeof(INPUT)));

            try
            {
                keybd_event((byte)vk, (byte)scanCode, isDown ? 0u : 0x0002u, UIntPtr.Zero);
            }
            catch { }

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

