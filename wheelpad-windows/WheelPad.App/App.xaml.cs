using System;
using System.Runtime.InteropServices;
using System.Threading;
using System.Windows;

namespace WheelPad.App;

/// <summary>
/// Interaction logic for App.xaml
/// </summary>
public partial class App : System.Windows.Application
{
    private static Mutex? _mutex;
    private static EventWaitHandle? _restoreEvent;
    private const string AppGuid = "FH6Controller_WheelPad_SingleInstance_Mutex";
    public const string RestoreEventName = "FH6Controller_Restore_Event_Signal";

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr hWnd);

    [DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

    [DllImport("user32.dll", SetLastError = true, CharSet = CharSet.Auto)]
    private static extern IntPtr FindWindow(string? lpClassName, string lpWindowName);

    private const int SW_RESTORE = 9;

    protected override void OnStartup(StartupEventArgs e)
    {
        _mutex = new Mutex(true, AppGuid, out bool createdNew);

        if (!createdNew)
        {
            // Another instance is already running. Signal it to restore immediately!
            try
            {
                using var existingEvent = EventWaitHandle.OpenExisting(RestoreEventName);
                existingEvent.Set();
            }
            catch
            {
                // Fallback to Win32 window message
                IntPtr hWnd = FindWindow(null, "FH6 CONTROLLER // WINDOWS COMPANION");
                if (hWnd != IntPtr.Zero)
                {
                    ShowWindow(hWnd, SW_RESTORE);
                    SetForegroundWindow(hWnd);
                }
            }

            Current.Shutdown();
            return;
        }

        // Create the auto-reset event for signaling window restore
        try
        {
            _restoreEvent = new EventWaitHandle(false, EventResetMode.AutoReset, RestoreEventName);
        }
        catch { }

        base.OnStartup(e);
    }

    protected override void OnExit(ExitEventArgs e)
    {
        _restoreEvent?.Dispose();
        _mutex?.Dispose();
        base.OnExit(e);
    }
}
