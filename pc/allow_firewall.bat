@echo off
setlocal
net session >nul 2>&1
if errorlevel 1 (
  echo Please right-click this file and choose "Run as administrator".
  pause
  exit /b 1
)
netsh advfirewall firewall add rule name="PANELINK TCP 47823" dir=in action=allow protocol=TCP localport=47823
netsh advfirewall firewall add rule name="PANELINK UDP 47823" dir=in action=allow protocol=UDP localport=47823
echo PANELINK firewall rules added.
pause
