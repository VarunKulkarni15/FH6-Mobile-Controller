using System;
using System.Diagnostics;
using System.IO;
using System.IO.Pipes;
using System.Runtime.InteropServices;
using System.Threading;
using System.Windows;

namespace WheelPad.App;

public partial class App : System.Windows.Application
{
    private const string PipeName = "FH6Controller_Ipc_Pipe_V1";
    private static Mutex? _singleInstanceMutex;
    private static Thread? _pipeThread;
    private static volatile bool _isRunning = true;

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr hWnd);

    [DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

    private const int SW_RESTORE = 9;

    public static void Log(string msg)
    {
        try
        {
            string dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "FH6Controller");
            Directory.CreateDirectory(dir);
            string path = Path.Combine(dir, "companion.log");
            File.AppendAllText(path, $"[{DateTime.UtcNow:HH:mm:ss.fff}] [PID:{Environment.ProcessId}] {msg}\n");
        }
        catch { }
    }

    protected override void OnStartup(StartupEventArgs e)
    {
        Log("=== App.OnStartup starting ===");

        AppDomain.CurrentDomain.UnhandledException += (s, args) =>
        {
            Log($"[CRASH] AppDomain Exception: {args.ExceptionObject}");
        };

        DispatcherUnhandledException += (s, args) =>
        {
            Log($"[CRASH] Dispatcher Exception: {args.Exception}");
            args.Handled = true;
        };

        int currentPid = Environment.ProcessId;

        // Guarantee clean single instance: terminate any lingering or zombie companion instances
        try
        {
            var oldNames = new[] { "HRZN-Controller", "fh6-controller-windows", "WheelPad.Server", "WheelPad.App" };
            foreach (var name in oldNames)
            {
                foreach (var proc in Process.GetProcessesByName(name))
                {
                    if (proc.Id != currentPid)
                    {
                        Log($"Terminating prior companion instance {proc.ProcessName} (PID {proc.Id}) to guarantee exclusive port access...");
                        try 
                        { 
                            proc.Kill(); 
                            proc.WaitForExit(500); 
                        } 
                        catch { }
                    }
                }
            }
        }
        catch { }

        // Start IPC server to listen for future launches
        StartIpcServer();

        // Register Windows Search shortcut and protocol handlers
        WheelPad.Core.Services.AppRegistrationService.EnsureRegistered();

        Log("Calling base.OnStartup(e)...");
        base.OnStartup(e);
        Log("base.OnStartup(e) completed.");
    }

    private static void StartIpcServer()
    {
        _pipeThread = new Thread(() =>
        {
            while (_isRunning)
            {
                NamedPipeServerStream? server = null;
                try
                {
                    server = new NamedPipeServerStream(
                        PipeName,
                        PipeDirection.In,
                        NamedPipeServerStream.MaxAllowedServerInstances,
                        PipeTransmissionMode.Byte
                    );

                    server.WaitForConnection();

                    using var reader = new StreamReader(server);
                    string? msg = reader.ReadLine();
                    Log($"IPC received message: {msg}");
                    if (msg == "RESTORE_WINDOW")
                    {
                        Current?.Dispatcher.InvokeAsync(() =>
                        {
                            if (Current.MainWindow is MainWindow mw)
                            {
                                mw.RestoreFromTray();
                            }
                            else
                            {
                                var win = new MainWindow();
                                win.Show();
                            }
                        });
                    }
                }
                catch
                {
                    Thread.Sleep(200);
                }
                finally
                {
                    try { server?.Dispose(); } catch { }
                }
            }
        })
        {
            IsBackground = true,
            Name = "FH6Controller_IPC_Listener"
        };
        _pipeThread.Start();
    }

    protected override void OnExit(ExitEventArgs e)
    {
        Log("App.OnExit called.");
        _isRunning = false;
        base.OnExit(e);
    }
}
