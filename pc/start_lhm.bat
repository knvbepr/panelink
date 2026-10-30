@echo off
setlocal
net session >nul 2>&1
if errorlevel 1 (
  echo Requesting administrator rights...
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
cd /d "%~dp0"
if not exist "LibreHardwareMonitor\LibreHardwareMonitor.exe" (
  echo LibreHardwareMonitor folder is missing.
  pause
  exit /b 1
)
%SystemRoot%\System32\taskkill.exe /F /IM LibreHardwareMonitor.exe >nul 2>&1
start "" "%~dp0LibreHardwareMonitor\LibreHardwareMonitor.exe"
echo LibreHardwareMonitor started in administrator mode.
echo Web bridge: http://127.0.0.1:8085/metrics
timeout /t 3 /nobreak >nul
