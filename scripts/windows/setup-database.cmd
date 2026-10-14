@echo off
REM Double-click to run setup-database.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-database.ps1" %*
if errorlevel 1 pause
