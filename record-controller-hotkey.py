import os
import sys
import time
import ctypes
from ctypes import wintypes
import subprocess
from datetime import datetime
import winsound

# Configuration
SAVE_DIR = r"C:\Users\varun\Videos\OBS"
SCRCPY_EXE = r"C:\Users\varun\AppData\Local\Microsoft\WinGet\Packages\Genymobile.scrcpy_Microsoft.Winget.Source_8wekyb3d8bbwe\scrcpy-win64-v5.0.1\scrcpy.exe"
WINDOW_TITLE = "Horizon Controller (Recording)"

if not os.path.exists(SCRCPY_EXE):
    # Fallback to PATH
    SCRCPY_EXE = "scrcpy"

os.makedirs(SAVE_DIR, exist_ok=True)

user32 = ctypes.windll.user32
VK_CONTROL = 0x11
VK_R = 0x52
WM_CLOSE = 0x0010

def is_key_down(vk):
    return (user32.GetAsyncKeyState(vk) & 0x8000) != 0

def play_start_sound():
    try:
        winsound.Beep(1200, 100)
        time.sleep(0.04)
        winsound.Beep(1800, 180)
    except Exception:
        pass

def play_stop_sound():
    try:
        winsound.Beep(1500, 100)
        time.sleep(0.04)
        winsound.Beep(800, 220)
    except Exception:
        pass

def print_banner():
    os.system("cls" if os.name == "nt" else "clear")
    print("=" * 68)
    print("   FORZA HORIZON CONTROLLER - GLOBAL HOTKEY RECORDER (120 FPS)")
    print("=" * 68)
    print(f"   Output Directory : {SAVE_DIR}")
    print("   Hotkey           : [Ctrl + R] anywhere (in-game or desktop)")
    print("   Exit Program     : [Ctrl + C] in this window")
    print("=" * 68)
    print("   Status           : STANDBY (Waiting for Ctrl+R to start recording...)")
    print("=" * 68)

def main():
    print_banner()

    is_recording = False
    proc = None
    current_filepath = None
    last_toggle_time = 0

    try:
        while True:
            ctrl_pressed = is_key_down(VK_CONTROL)
            r_pressed = is_key_down(VK_R)

            now = time.time()
            if ctrl_pressed and r_pressed and (now - last_toggle_time > 1.2):
                last_toggle_time = now

                if not is_recording:
                    # START RECORDING
                    timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
                    current_filepath = os.path.join(SAVE_DIR, f"controller_{timestamp}.mp4")

                    play_start_sound()
                    print(f"\n[REC *] RECORDING STARTED! Saving to:")
                    print(f"        {current_filepath}")
                    print(f"        (Press [Ctrl + R] again to stop)\n")

                    cmd = [
                        SCRCPY_EXE,
                        f"--record={current_filepath}",
                        f"--window-title={WINDOW_TITLE}",
                        "--stay-awake",
                        "--max-fps=120",
                        "--video-bit-rate=16M",
                        "--no-audio"
                    ]

                    proc = subprocess.Popen(cmd)
                    is_recording = True

                else:
                    # STOP RECORDING
                    print(f"\n[STOP] Stopping recording and finalizing MP4 trailer...")

                    if proc:
                        # Give scrcpy graceful WM_CLOSE to finalize MP4 file
                        hwnd = user32.FindWindowW(None, WINDOW_TITLE)
                        if hwnd:
                            user32.PostMessageW(hwnd, WM_CLOSE, 0, 0)
                        else:
                            proc.terminate()

                        try:
                            proc.wait(timeout=5)
                        except subprocess.TimeoutExpired:
                            proc.kill()

                        proc = None

                    play_stop_sound()

                    if os.path.exists(current_filepath) and os.path.getsize(current_filepath) > 0:
                        size_mb = os.path.getsize(current_filepath) / (1024 * 1024)
                        print(f"[SAVED] Successfully saved ({size_mb:.2f} MB):")
                        print(f"        {current_filepath}")
                    else:
                        print(f"[INFO] Recording session ended.")

                    is_recording = False
                    print("\nStatus: STANDBY (Waiting for [Ctrl + R] to record next run...)")

            # Check if scrcpy window was manually closed by user
            if is_recording and proc and proc.poll() is not None:
                play_stop_sound()
                print("\n[INFO] Scrcpy window was closed by user. Recording saved.")
                is_recording = False
                proc = None
                print("Status: STANDBY (Waiting for [Ctrl + R] to record next run...)")

            time.sleep(0.05)

    except KeyboardInterrupt:
        if is_recording and proc:
            hwnd = user32.FindWindowW(None, WINDOW_TITLE)
            if hwnd:
                user32.PostMessageW(hwnd, WM_CLOSE, 0, 0)
            else:
                proc.terminate()
            proc.wait(timeout=3)
        print("\nExiting recorder. Goodbye!")

if __name__ == "__main__":
    main()
