@echo off
REM Double-click to run reset-demo-data.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0reset-demo-data.ps1" %*
if errorlevel 1 pause
