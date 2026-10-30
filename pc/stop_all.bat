@echo off
setlocal
net session >nul 2>&1
if errorlevel 1 (
  echo Requesting administrator rights to stop both processes...
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)

%SystemRoot%\System32\taskkill.exe /F /IM panelink_pc.exe >nul 2>&1
%SystemRoot%\System32\taskkill.exe /F /IM LibreHardwareMonitor.exe >nul 2>&1

echo.
echo PANELINK PC service and LibreHardwareMonitor have been stopped.
timeout /t 2 /nobreak >nul
