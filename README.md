# PANELINK

A landscape, black/white, pixel-grid system panel for Android, plus a small C telemetry
service for a Windows PC.

- Local mode: CPU / per-core, RAM, disk I/O, temperatures, GPU, process list, battery,
  network throughput, root state.
- Remote mode: the top bar stays the phone's status; CPU, RAM, multiple GPUs, multiple
  disks, PC battery (hidden on desktops), fans and PC temperature sensors come from the PC.
  `ROOT` becomes `REMOTE` only while the link is live.
- Fixed top-bar slots: changing digit widths never push neighbouring items sideways.
- No third-party UI/chart libraries; Canvas drawing, DSEG + JetBrains Mono + WenQuanYi
  Bitmap Song 16px pixel font.
- Temperature warning uses amber, alarm uses red; both thresholds are adjustable in
  settings. Fault blinking is continuous and runs at a 0.5 s phase.
- Tapping a numeric setting opens the built-in pixel keypad for direct entry; the exit
  row opens a same-style confirmation sheet. Values and +/- buttons have separate columns.

## Remote protocol

- UDP 51737: `PANELINK_DISCOVER` -> `{"type":"offer","name":"...","port":51737}`
- TCP 51737: newline-delimited JSON state frames (PC -> phone)
- Data-only: the PC never parses or executes phone input; no command channel, no scripts.

The PC service is in [`pc/`](pc/). It is compiled with MSVC by GitHub Actions
(`.github/workflows/build-pc.yml`). Prebuilt binaries are attached to Releases.

## Build Android

```bat
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat :app:assembleDebug
```

The launcher is `com.example.panel.dash.DashActivity`; the UI specimen/demo remains in the
source tree but is not the launcher. The panel is installed and verified on Android 5.1.1
(960x540, rooted) and Android 13 (2480x1116, unrooted).

## Screenshots

See [`docs/`](docs/) and the latest screenshots in the repository history.

## Known limits

- Android 13 test device is not rooted, so its process list can only show the app itself
  and charging current may be unavailable.
- ACPI thermal zones / some fans need the PC service to run elevated; fan counters are
  absent on many laptops. GPU fan is read through NVML when the driver exposes it.
- Windows Firewall must allow inbound UDP/TCP 51737 on the private LAN. Run
  `pc/allow_firewall.bat` as administrator.
- 5G requires API 30+ and a 5G-capable device; API 22 has no NR network type.
