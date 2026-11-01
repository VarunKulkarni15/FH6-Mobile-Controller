@echo off
setlocal enabledelayedexpansion
title Horizon Phone Screen Recorder
echo ========================================================
echo   FORZA HORIZON CONTROLLER - STANDALONE SCREEN RECORDER
echo ========================================================
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

if not exist "recordings" mkdir "recordings"

for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value') do set dt=%%I
set TIMESTAMP=%dt:~0,8%_%dt:~8,6%
set OUTFILE=recordings\controller_recording_%TIMESTAMP%.mp4

echo Output File: %OUTFILE%
echo.
echo ========================================================
echo [RECORDING IN PROGRESS]
echo Close the scrcpy window or press Ctrl+C in this terminal
echo when you want to STOP recording.
echo ========================================================
echo.

"%SCRCPY_EXE%" --window-title "Horizon Controller (Recording...)" --stay-awake --record="%OUTFILE%" --max-fps 60 --video-bit-rate 12M --no-audio

echo.
echo Recording saved to: %OUTFILE%
explorer.exe /select,"%OUTFILE%"
pause

