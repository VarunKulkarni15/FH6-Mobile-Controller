@echo off
title Horizon Controller - OBS Mirroring Stream
echo ========================================================
echo   FORZA HORIZON CONTROLLER - OBS LOW-LATENCY MIRROR
echo ========================================================
echo.
echo Launching scrcpy with zero-lag USB mirror...
echo Phone screen will appear in a dedicated window for OBS.
echo.

set SCRCPY_EXE=C:\Users\varun\AppData\Local\Microsoft\WinGet\Packages\Genymobile.scrcpy_Microsoft.Winget.Source_8wekyb3d8bbwe\scrcpy-win64-v5.0.1\scrcpy.exe

if not exist "%SCRCPY_EXE%" (
    where scrcpy >nul 2>&1
    if %ERRORLEVEL% equ 0 (
        set SCRCPY_EXE=scrcpy
    ) else (
        echo [ERROR] scrcpy executable not found.
        pause
        exit /b 1
    )
)

"%SCRCPY_EXE%" --window-title "Horizon Controller" --stay-awake --max-fps 120 --video-bit-rate 16M --no-audio


