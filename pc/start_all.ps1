$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$lhmExe = Join-Path $root 'LibreHardwareMonitor\LibreHardwareMonitor.exe'
$service = Join-Path $root 'panelink_pc.exe'

function Test-Web {
    try { return (Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8085/metrics' -TimeoutSec 2).StatusCode -eq 200 }
    catch { return $false }
}
function Test-Service {
    return [bool](Get-Process -Name panelink_pc -ErrorAction SilentlyContinue)
}

if (-not (Test-Path $lhmExe)) { Write-Host 'LibreHardwareMonitor folder is missing.'; exit 1 }
if (-not (Test-Path $service)) { Write-Host 'panelink_pc.exe is missing.'; exit 1 }

$web = Test-Web
$svc = Test-Service

if ($web -and $svc) {
    Write-Host 'LibreHardwareMonitor web bridge and PANELINK service are already running.'
    exit 0
}

if (-not $web) {
    Write-Host 'Starting LibreHardwareMonitor...'
    $isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    if ($isAdmin) {
        & (Join-Path $root 'start_lhm.ps1')
    } else {
        $rc = Start-Process powershell -Verb RunAs -Wait -PassThru -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',"`"$root\start_lhm.ps1`"")
        if ($rc.ExitCode -ne 0) { Write-Host ('LHM helper returned ' + $rc.ExitCode) }
    }
    if (-not (Test-Web)) {
        Write-Host 'LibreHardwareMonitor web server is still unavailable.'
        exit 1
    }
}

if (-not (Test-Service)) {
    Write-Host 'Starting PANELINK PC service...'
    Start-Process -FilePath $service -WorkingDirectory $root
    Start-Sleep -Seconds 2
}

if (Test-Service) {
    Write-Host 'PANELINK PC service is running.'
} else {
    Write-Host 'PANELINK PC service did not start.'
    exit 1
}
Write-Host 'Done. Use the tray icon to choose metrics.'
exit 0
