@echo off
REM Double-click to run start-frontend.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-frontend.ps1" %*
if errorlevel 1 pause
