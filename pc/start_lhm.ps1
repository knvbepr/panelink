$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$exe = Join-Path $root 'LibreHardwareMonitor\LibreHardwareMonitor.exe'
if (-not (Test-Path $exe)) { Write-Host 'LibreHardwareMonitor folder is missing.'; exit 1 }

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Start-Process powershell -Verb RunAs -Wait -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',"`"$PSCommandPath`"")
    exit
}

function Test-Web {
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8085/metrics' -TimeoutSec 2
        return $r.StatusCode -eq 200
    } catch { return $false }
}

function Focus-Lhm {
    $p = Get-Process -Name LibreHardwareMonitor -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
    if (-not $p) { return $false }
    try {
        Add-Type -AssemblyName Microsoft.VisualBasic
        [Microsoft.VisualBasic.Interaction]::AppActivate($p.Id)
    } catch { }
    try {
        Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class PanelinkFg {
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr hWnd);
}
"@
        $p.Refresh()
        [PanelinkFg]::ShowWindow($p.MainWindowHandle, 9) | Out-Null
        [PanelinkFg]::SetForegroundWindow($p.MainWindowHandle) | Out-Null
        [PanelinkFg]::BringWindowToTop($p.MainWindowHandle) | Out-Null
    } catch { }
    return $true
}

if (-not (Test-Web)) {
    Write-Host 'Starting LibreHardwareMonitor as administrator...'
    Get-Process -Name LibreHardwareMonitor -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Process -FilePath $exe -WorkingDirectory (Split-Path $exe)
    for ($i = 0; $i -lt 20; $i++) {
        Start-Sleep -Milliseconds 500
        if (Test-Web) { break }
    }
}

if (-not (Test-Web)) {
    Write-Host 'LHM web server did not start. Check the LHM window for a driver or .NET error.'
    Focus-Lhm | Out-Null
    exit 1
}

if (Focus-Lhm) {
    Write-Host 'LibreHardwareMonitor is running in the foreground.'
} else {
    Write-Host 'LibreHardwareMonitor is running, but no window handle was found.'
}
exit 0
