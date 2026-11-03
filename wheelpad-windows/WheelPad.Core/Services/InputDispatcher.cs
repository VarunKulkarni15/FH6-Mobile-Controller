using System.Diagnostics;
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
        public GameDetector? GameDetector { get; set; }
        private readonly ConcurrentDictionary<int, bool> _activeKeysDown = new();
        private readonly ConcurrentDictionary<string, bool> _toggleStates = new();
        private readonly object _stateLock = new();

                        [DllImport("user32.dll")]
        private static extern IntPtr GetForegroundWindow();

        [DllImport("user32.dll")]
        private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);

        private bool IsGameForeground()
        {
            try
            {
                IntPtr fg = GetForegroundWindow();
                if (fg == IntPtr.Zero) return false;
                GetWindowThreadProcessId(fg, out uint fgPid);
                if (GameDetector != null && fgPid == (uint)GameDetector.CurrentProcessId) return true;
                var proc = Process.GetProcessById((int)fgPid);
                string pName = proc.ProcessName.ToLowerInvariant();
                return pName.Contains("forza") || pName.Contains("fh") || pName.Contains("applicationframehost");
            }
            catch { return false; }
        }

        public InputDispatcher(ProfileManager profileManager, IVirtualXboxController? xboxController = null)
        {
            _profileManager = profileManager;
            _xboxController = xboxController;
            _xboxController?.Connect();
        }

        public void DispatchAnalogInput(string actionId, float normalizedValue)
        {
            normalizedValue = Math.Clamp(normalizedValue, -1.0f, 1.0f);

            // Driving input detection: If player uses throttle/gas or brake while paused, game is driving
            if ((actionId == "throttle" || actionId == "brake") && normalizedValue > 0.15f)
            {
                if (GameDetector?.IsGamePaused == true)
                {
                    GameDetector?.SetPauseState(false, "PAUSE");
                }
            }

            // Fast path for direct analog axes
            if (_xboxController != null && _xboxController.IsConnected)
            {
                if (actionId == "left_stick_x" || (actionId == "steer" && (GameDetector?.IsGamePaused == true)))
                {
                    float mag = Math.Abs(normalizedValue);
                    if (mag < 0.04f)
                    {
                        _xboxController.SetAxis(XboxButton.LeftThumbX, 0);
                    }
                    else
                    {
                        float sign = Math.Sign(normalizedValue);
                        float scaled = (float)(Math.Pow(mag, 1.6) * 0.45f);
                        short raw = (short)(sign * scaled * 32767f);
                        _xboxController.SetAxis(XboxButton.LeftThumbX, raw);
                    }
                    return;
                }
                else if (actionId == "left_stick_y")
                {
                    float mag = Math.Abs(normalizedValue);
                    if (mag < 0.04f)
                    {
                        _xboxController.SetAxis(XboxButton.LeftThumbY, 0);
                    }
                    else
                    {
                        float sign = Math.Sign(normalizedValue);
                        float scaled = (float)(Math.Pow(mag, 1.6) * 0.45f);
                        short raw = (short)(sign * scaled * 32767f);
                        _xboxController.SetAxis(XboxButton.LeftThumbY, raw);
                    }
                    return;
                }
                else if (actionId == "right_stick_x")
                {
                    short raw = (short)(normalizedValue * 32767f);
                    _xboxController.SetAxis(XboxButton.RightThumbX, raw);
                    if (normalizedValue < -0.35f) SendWindowsKey(37, true); else SendWindowsKey(37, false);
                    if (normalizedValue > 0.35f) SendWindowsKey(39, true); else SendWindowsKey(39, false);
                    return;
                }
                else if (actionId == "right_stick_y")
                {
                    short raw = (short)(normalizedValue * 32767f);
                    _xboxController.SetAxis(XboxButton.RightThumbY, raw);
                    if (normalizedValue > 0.35f) SendWindowsKey(38, true); else SendWindowsKey(38, false);
                    if (normalizedValue < -0.35f) SendWindowsKey(40, true); else SendWindowsKey(40, false);
                    return;
                }
            }

            var action = FindAction(actionId);
            if (action == null) return;

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
                    else if (action.XboxTarget == XboxButton.LeftThumbY)
                    {
                        short raw = (short)(normalizedValue * 32767f);
                        _xboxController.SetAxis(XboxButton.LeftThumbY, raw);
                    }
                    else if (action.XboxTarget == XboxButton.RightThumbX)
                    {
                        short raw = (short)(normalizedValue * 32767f);
                        _xboxController.SetAxis(XboxButton.RightThumbX, raw);
                    }
                    else if (action.XboxTarget == XboxButton.RightThumbY)
                    {
                        short raw = (short)(normalizedValue * 32767f);
                        _xboxController.SetAxis(XboxButton.RightThumbY, raw);
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
            if (actionId == "exit_menu_silent" || actionId == "drive_rig_mode")
            {
                if (isPressed)
                {
                    GameDetector?.SetPauseState(false, "PAUSE");
                }
                return;
            }

            if (actionId == "auto_drive")
            {
                if (isPressed)
                {
                    TriggerAutoDriveSequence();
                }
                return;
            }

            if (actionId == "look_back")
            {
                // In Forza Horizon: Right Stick Down + PC Arrow Down (VK_DOWN = 40)
                if (_xboxController != null && _xboxController.IsConnected)
                {
                    _xboxController.SetAxis(XboxButton.RightThumbY, isPressed ? (short)-32767 : (short)0);
                }
                SendWindowsKey(40, isPressed);
                return;
            }

            if (actionId == "look_left")
            {
                // In Forza Horizon: Right Stick Left + PC Arrow Left (VK_LEFT = 37)
                if (_xboxController != null && _xboxController.IsConnected)
                {
                    _xboxController.SetAxis(XboxButton.RightThumbX, isPressed ? (short)-32767 : (short)0);
                }
                SendWindowsKey(37, isPressed);
                return;
            }

            if (actionId == "look_right")
            {
                // In Forza Horizon: Right Stick Right + PC Arrow Right (VK_RIGHT = 39)
                if (_xboxController != null && _xboxController.IsConnected)
                {
                    _xboxController.SetAxis(XboxButton.RightThumbX, isPressed ? (short)32767 : (short)0);
                }
                SendWindowsKey(39, isPressed);
                return;
            }

            if (actionId == "look_forward")
            {
                // In Forza Horizon: Right Stick Up + PC Arrow Up (VK_UP = 38)
                if (_xboxController != null && _xboxController.IsConnected)
                {
                    _xboxController.SetAxis(XboxButton.RightThumbY, isPressed ? (short)32767 : (short)0);
                }
                SendWindowsKey(38, isPressed);
                return;
            }

// Dual dispatch: Xbox Controller + PC Keyboard for all Forza Horizon Menu & Map Actions
            bool isFg = IsGameForeground();
            if (actionId == "pause" || actionId == "btn_start" || actionId == "btn_menu")
            {
                if (isPressed)
                {
                    // Debounce rapid toggle pulses from UDP/TCP to eliminate glitching loop
                    if (GameDetector != null && (DateTime.UtcNow.Ticks - GameDetector.LastMenuToggleTicks) < TimeSpan.FromMilliseconds(350).Ticks)
                    {
                        return;
                    }
                    bool newPausedState = !(GameDetector?.IsGamePaused ?? false);
                    GameDetector?.SetPauseState(newPausedState, "PAUSE");
                    if (_xboxController != null && _xboxController.IsConnected)
                    {
                        _xboxController.SetButton(XboxButton.Start, true);
                        Task.Delay(60).ContinueWith(_ => _xboxController.SetButton(XboxButton.Start, false));
                    }
                    else if (isFg)
                    {
                        SendWindowsKey(27, true);
                        Task.Delay(60).ContinueWith(_ => SendWindowsKey(27, false));
                    }
                }
                return;
            }
            if (actionId == "map" || actionId == "btn_back" || actionId == "btn_view")
            {
                if (isPressed)
                {
                    // Debounce rapid toggle pulses from UDP/TCP to eliminate glitching loop
                    if (GameDetector != null && (DateTime.UtcNow.Ticks - GameDetector.LastMenuToggleTicks) < TimeSpan.FromMilliseconds(350).Ticks)
                    {
                        return;
                    }
                    bool newPaused = true;
                    if (GameDetector?.IsGamePaused == true && GameDetector.CurrentMenuMode == "MAP")
                    {
                        newPaused = false;
                    }
                    GameDetector?.SetPauseState(newPaused, newPaused ? "MAP" : "PAUSE");
                    if (_xboxController != null && _xboxController.IsConnected)
                    {
                        _xboxController.SetButton(XboxButton.Back, true);
                        Task.Delay(60).ContinueWith(_ => _xboxController.SetButton(XboxButton.Back, false));
                    }
                    else if (isFg)
                    {
                        SendWindowsKey(0x4D, true);
                        Task.Delay(60).ContinueWith(_ => SendWindowsKey(0x4D, false));
                    }
                }
                return;
            }
            if (actionId == "btn_b")
            {
                if (isPressed)
                {
                    GameDetector?.HandleMenuBack();
                }
                if (_xboxController != null && _xboxController.IsConnected)
                {
                    _xboxController.SetButton(XboxButton.B, isPressed);
                }
                else if (isFg)
                {
                    SendWindowsKey(27, isPressed); // ESC / Back fallback
                }
                return;
            }
            if (actionId == "btn_a")
            {
                if (isPressed)
                {
                    GameDetector?.HandleMenuSelect();
                }
                if (_xboxController != null && _xboxController.IsConnected)
                {
                    _xboxController.SetButton(XboxButton.A, isPressed);
                }
                else if (isFg)
                {
                    SendWindowsKey(13, isPressed); // Enter / Set Route fallback
                }
                return;
            }
            if (actionId == "btn_x")
            {
                if (_xboxController != null && _xboxController.IsConnected) _xboxController.SetButton(XboxButton.X, isPressed);
                else if (isFg) SendWindowsKey(0x58, isPressed); // X / Fast Travel
                return;
            }
            if (actionId == "btn_y" || actionId == "rewind")
            {
                if (_xboxController != null && _xboxController.IsConnected) _xboxController.SetButton(XboxButton.Y, isPressed);
                else if (isFg)
                {
                    SendWindowsKey(0x59, isPressed); // Y / Reveal Map
                    SendWindowsKey(0x52, isPressed); // R / Rewind
                }
                return;
            }
            if (actionId == "btn_lb" || actionId == "clutch")
            {
                if (_xboxController != null && _xboxController.IsConnected) _xboxController.SetButton(XboxButton.LeftBumper, isPressed);
                else if (isFg) SendWindowsKey(33, isPressed); // Page Up
                return;
            }
            if (actionId == "camera" || actionId == "btn_rb")
            {
                if (_xboxController != null && _xboxController.IsConnected) _xboxController.SetButton(XboxButton.RightBumper, isPressed);
                else if (isFg) SendWindowsKey(34, isPressed); // Page Down
                return;
            }
            if (actionId == "camera" || actionId == "btn_rb")
            {
                if (_xboxController != null && _xboxController.IsConnected) _xboxController.SetButton(XboxButton.RightBumper, isPressed);
                if (isFg) SendWindowsKey(34, isPressed); // Page Down
                return;
            }
            if (actionId == "dpad_up")
            {
                if (_xboxController != null && _xboxController.IsConnected) _xboxController.SetButton(XboxButton.DpadUp, isPressed);
                SendWindowsKey(38, isPressed); // Up Arrow
                return;
            }
            if (actionId == "dpad_down")
            {
                if (_xboxController != null && _xboxController.IsConnected) _xboxController.SetButton(XboxButton.DpadDown, isPressed);
                SendWindowsKey(40, isPressed); // Down Arrow
                return;
            }
            if (actionId == "dpad_left")
            {
                if (_xboxController != null && _xboxController.IsConnected) _xboxController.SetButton(XboxButton.DpadLeft, isPressed);
                SendWindowsKey(37, isPressed); // Left Arrow
                return;
            }
            if (actionId == "dpad_right")
            {
                if (_xboxController != null && _xboxController.IsConnected) _xboxController.SetButton(XboxButton.DpadRight, isPressed);
                SendWindowsKey(39, isPressed); // Right Arrow
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
            // 2. Dispatch Keyboard target ONLY if Xbox Controller is not handling this or if strictly mapped as KeyboardKey
            else if (action.BindingType == BindingType.KeyboardKey
                || (_xboxController == null || !_xboxController.IsConnected))
            {
                if (action.KeyboardKeyCode > 0)
                {
                    SendWindowsKey(action.KeyboardKeyCode, shouldActuate);
                }
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
                "btn_b" or "shift_up" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, XboxTarget = XboxButton.B },
                "btn_x" or "activate" or "shift_down" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, XboxTarget = XboxButton.X },
                "rewind" or "btn_y" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, XboxTarget = XboxButton.Y },
                "btn_lb" or "clutch" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, XboxTarget = XboxButton.LeftBumper },
                "camera" or "btn_rb" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, XboxTarget = XboxButton.RightBumper },
                "btn_ls" or "convertible" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, XboxTarget = XboxButton.LeftThumbClick },
                "btn_rs" or "horn" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, XboxTarget = XboxButton.RightThumbClick },
                "look_back" => new GameAction { Id = "look_back", InputType = InputType.DigitalButton, XboxTarget = XboxButton.RightThumbY },
                "map" or "btn_back" or "btn_view" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, XboxTarget = XboxButton.Back },
                "pause" or "btn_start" or "btn_menu" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, XboxTarget = XboxButton.Start },
                "dpad_up" or "photo_mode" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, XboxTarget = XboxButton.DpadUp },
                "dpad_down" or "anna" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, XboxTarget = XboxButton.DpadDown },
                "dpad_left" or "forza_link" or "radio_prev" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, XboxTarget = XboxButton.DpadLeft },
                "dpad_right" or "radio_next" => new GameAction { Id = actionId, InputType = InputType.DigitalButton, XboxTarget = XboxButton.DpadRight },
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
                    // 1. Activate ANNA via virtual Xbox controller D-pad Down
                    if (_xboxController != null && _xboxController.IsConnected)
                    {
                        _xboxController.SetButton(XboxButton.DpadDown, true);
                        await Task.Delay(120);
                        _xboxController.SetButton(XboxButton.DpadDown, false);

                        await Task.Delay(200);

                        // 2. Select Option 2 via virtual Xbox controller D-pad Left
                        _xboxController.SetButton(XboxButton.DpadLeft, true);
                        await Task.Delay(120);
                        _xboxController.SetButton(XboxButton.DpadLeft, false);
                    }
                    else
                    {
                        // Fallback only if virtual Xbox controller is not connected
                        SendWindowsKey(0x43, isDown: true);
                        await Task.Delay(100);
                        SendWindowsKey(0x43, isDown: false);
                        await Task.Delay(200);
                        SendWindowsKey(0x32, isDown: true);
                        await Task.Delay(100);
                        SendWindowsKey(0x32, isDown: false);
                    }
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
        private static extern void mouse_event(uint dwFlags, int dx, int dy, uint dwData, UIntPtr dwExtraInfo);
        private const uint MOUSEEVENTF_MOVE = 0x0001;

        [DllImport("user32.dll")]
        private static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo);

        private void SendWindowsKey(int virtualKeyCode, bool isDown)
        {
            if (virtualKeyCode <= 0) return;

            // Never dispatch a key-up event if the key is not currently depressed
            if (!isDown && !_activeKeysDown.ContainsKey(virtualKeyCode))
            {
                return;
            }

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

