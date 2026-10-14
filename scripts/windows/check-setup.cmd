@echo off
REM Double-click to run check-setup.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0check-setup.ps1" %*
if errorlevel 1 pause
