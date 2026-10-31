@echo off
title Forza Horizon Controller Suite - Windows Installer
echo ============================================================
echo   Forza Horizon Controller Suite // PC Companion Installer
echo ============================================================
echo.

set "TARGET_DIR=%LOCALAPPDATA%\Programs\FH6Controller"
set "TARGET_EXE=%TARGET_DIR%\fh6-controller-windows.exe"
set "SRC_EXE=%~dp0fh6-controller-windows.exe"

if not exist "%SRC_EXE%" (
    echo [ERROR] Could not find fh6-controller-windows.exe in this folder.
    pause
    exit /b 1
)

echo [1/3] Removing download block (Windows SmartScreen unblock)...
powershell -NoProfile -Command "Unblock-File '%SRC_EXE%'" 2>nul

echo [2/3] Installing application files to %TARGET_DIR%...
if not exist "%TARGET_DIR%" mkdir "%TARGET_DIR%"
copy /Y "%~dp0*.*" "%TARGET_DIR%\" >nul

echo [3/3] Registering in Windows Start Menu and Windows Search...
powershell -NoProfile -Command "$ws = New-Object -ComObject WScript.Shell; $s = $ws.CreateShortcut([IO.Path]::Combine([Environment]::GetFolderPath('Programs'), 'Forza Horizon Controller.lnk')); $s.TargetPath = '%TARGET_EXE%'; $s.WorkingDirectory = '%TARGET_DIR%'; $s.Description = 'Forza Horizon Controller Suite for PC'; $s.IconLocation = '%TARGET_EXE%,0'; $s.Save()"

echo.
echo ============================================================
echo [SUCCESS] Forza Horizon Controller Suite installed!
echo You can now search for 'Forza Horizon Controller' in the Windows Start Menu.
echo ============================================================
echo.
echo Starting application...
start "" "%TARGET_EXE%"
exit /b 0
