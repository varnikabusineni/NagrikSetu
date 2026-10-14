@echo off
REM Double-click to run start-backend.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-backend.ps1" %*
if errorlevel 1 pause
