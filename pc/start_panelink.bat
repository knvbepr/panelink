@echo off
setlocal
cd /d "%~dp0"
%SystemRoot%\System32\tasklist.exe /FI "IMAGENAME eq panelink_pc.exe" /NH 2>nul | %SystemRoot%\System32\findstr.exe /I /C:"panelink_pc.exe" >nul
if errorlevel 1 (
  echo Starting PANELINK PC service only...
  echo CPU voltage/power will be unavailable until LibreHardwareMonitor is started.
  start "" "%~dp0panelink_pc.exe"
) else (
  echo PANELINK PC service is already running.
)
timeout /t 2 /nobreak >nul
