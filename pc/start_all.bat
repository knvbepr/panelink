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

powershell -NoProfile -Command "try { (Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8085/metrics' -TimeoutSec 2).StatusCode | Out-Null; exit 0 } catch { exit 1 }"
if errorlevel 1 (
  echo Starting LibreHardwareMonitor (administrator)...
  %SystemRoot%\System32\taskkill.exe /F /IM LibreHardwareMonitor.exe >nul 2>&1
  start "" "%~dp0LibreHardwareMonitor\LibreHardwareMonitor.exe"
  echo Waiting for sensor web server...
  powershell -NoProfile -Command "\$ok=\$false; for(\$i=0;\$i -lt 15;\$i++){ try { (Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8085/metrics' -TimeoutSec 2).StatusCode | Out-Null; \$ok=\$true; break } catch { Start-Sleep -Seconds 1 } }; if(\$ok){ exit 0 } else { exit 1 }"
  if errorlevel 1 (
    echo LHM did not expose its web server. Check the LibreHardwareMonitor window.
    pause
  )
) else (
  echo LibreHardwareMonitor web bridge is already running.
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
