# PANELINK PC service

Small Windows x64 console service. It reads CPU, RAM, GPU (NVML + DXGI), disks, battery,
fans and temperature sensors, and streams them as newline-delimited JSON over TCP.

## Run

1. Put `panelink_pc.exe` and `panelink.ini` in the same folder. Copy
   `panelink.ini.example` to `panelink.ini` if needed.
2. Right-click `allow_firewall.bat` -> **Run as administrator** once, so the phone can
   reach UDP/TCP 51737 on a private LAN.
3. Run `panelink_pc.exe`.
4. In the Android settings enable **远程模式**. It broadcasts `PANELINK_DISCOVER` over UDP
   and connects to the first offer. You can also enter the PC IP manually.

Without admin rights the core metrics still work. ACPI thermal zones and some fan
counters need an elevated process; NVIDIA GPU data comes from `nvml.dll` and works
without elevation.

## Config (`panelink.ini`)

| key | default | meaning |
|---|---|---|
| port | 51737 | UDP discovery + TCP state port |
| interval_ms | 1000 | PC sampling interval, 100..5000 |
| name | computer name | name shown on the phone |
| send_cpu/mem/gpu/disk/battery/fans/temps | 1 | select fields to send |

The phone never sends executable commands. UDP only answers `PANELINK_DISCOVER`; TCP only
streams JSON. There is no script/command channel, so the protocol is data-only.
