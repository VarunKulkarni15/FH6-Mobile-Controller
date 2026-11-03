@echo off
title Horizon Controller Hotkey Recorder [Ctrl+R]
cd /d "%~dp0"
python record-controller-hotkey.py
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Python not found or recorder closed with error.
    pause
)
