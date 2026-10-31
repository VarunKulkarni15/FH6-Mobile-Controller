using System;
using System.Diagnostics;
using System.IO;
using System.IO.Pipes;
using System.Threading;
using System.Windows;

namespace WheelPad.App;

/// <summary>
/// Interaction logic for App.xaml
/// Provides single-instance enforcement and instant foreground window restore via Named Pipes.
/// </summary>
public partial class App : System.Windows.Application
{
    private const string PipeName = "FH6Controller_Ipc_Pipe_V1";
    private static Thread? _pipeThread;
    private static volatile bool _isRunning = true;

    protected override void OnStartup(StartupEventArgs e)
    {
        // 1. Attempt to signal an existing running instance via Named Pipe
        try
        {
            using var client = new NamedPipeClientStream(".", PipeName, PipeDirection.Out);
            client.Connect(300); // 300ms timeout
            using var writer = new StreamWriter(client) { AutoFlush = true };
            writer.WriteLine("RESTORE_WINDOW");
            // Running instance was notified. Terminate duplicate immediately!
            Environment.Exit(0);
            return;
        }
        catch
        {
            // No active responsive instance detected
        }

        // 2. Kill any frozen/zombie background instances of fh6-controller-windows
        int currentPid = Environment.ProcessId;
        try
        {
            foreach (var proc in Process.GetProcessesByName("fh6-controller-windows"))
            {
                if (proc.Id != currentPid)
                {
                    try { proc.Kill(); proc.WaitForExit(500); } catch { }
                }
            }
        }
        catch { }

        // 3. Start Named Pipe IPC Server to listen for future double-clicks/restores
        StartIpcServer();

        // 4. Ensure registered as native Windows App in Start Menu & Windows Search
        WheelPad.Core.Services.AppRegistrationService.EnsureRegistered();

        base.OnStartup(e);
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
                    if (msg == "RESTORE_WINDOW")
                    {
                        Current?.Dispatcher.InvokeAsync(() =>
                        {
                            if (Current.MainWindow is MainWindow mw)
                            {
                                mw.RestoreFromTray();
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
        _isRunning = false;
        base.OnExit(e);
    }
}
