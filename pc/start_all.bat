@echo off
setlocal
cd /d "%~dp0"
if not exist "LibreHardwareMonitor\LibreHardwareMonitor.exe" (
  echo LibreHardwareMonitor folder is missing.
  pause
  exit /b 1
)

%SystemRoot%\System32\tasklist.exe /FI "IMAGENAME eq LibreHardwareMonitor.exe" /NH 2>nul | %SystemRoot%\System32\findstr.exe /I /C:"LibreHardwareMonitor.exe" >nul
if errorlevel 1 (
  echo Starting LibreHardwareMonitor with administrator rights...
  powershell -NoProfile -Command "Start-Process -FilePath '%~dp0LibreHardwareMonitor\LibreHardwareMonitor.exe' -WorkingDirectory '%~dp0LibreHardwareMonitor' -Verb RunAs"
  timeout /t 5 /nobreak >nul
) else (
  echo LibreHardwareMonitor is already running.
)

%SystemRoot%\System32\tasklist.exe /FI "IMAGENAME eq panelink_pc.exe" /NH 2>nul | %SystemRoot%\System32\findstr.exe /I /C:"panelink_pc.exe" >nul
if errorlevel 1 (
  echo Starting PANELINK PC service...
  start "" "%~dp0panelink_pc.exe"
) else (
  echo PANELINK PC service is already running.
)

echo.
echo Started. Use the PANELINK tray icon to choose sent metrics.
timeout /t 3 /nobreak >nul
