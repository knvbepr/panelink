# PANEL — 横屏系统面板

DEMO 之后落的真实项目版本：横屏全屏系统面板，单 Activity、纯 Java 自绘 View、无第三方 UI/图表库。  
启动入口为 `DashActivity`；之前的 `MainActivity` 设计样张保留为非启动的内部参考页。

## 运行

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.knvbepr.panelink/.dash.DashActivity
```

- 测试机：OPPO A33 / Android 5.1.1（API 22）/ 540×960 / 240dpi，横屏逻辑分辨率 960×540。
- `minSdk 21`、`targetSdk 21`；同一套布局按比例与像素字体档位适配其他分辨率。
- 权限：`INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE`、`READ_PHONE_STATE`。
- 默认全屏隐藏系统状态栏与导航栏；Menu 键呼出设置。

## 布局

- 顶栏（约 40px）：时间（DSEG）、实时上下行速率、WiFi（连接 + 可访问性勾/叉）、蜂窝代际（2/3/4/5G）、有线、USB、电池百分比/充电状态/充电电流、ROOT 状态、设置图标。
- 左上：CPU 折线图。点按切换“总占用 / 分核”；底部按核心显示实时频率。
- 左下：进程列表。点击 `PID / 进程 / CPU / MEM` 表头切换排序（再次点击切换升/降序）；可拖动滚动。
- 右上：RAM 占用条 + 已用/总量。
- RAM 下方：磁盘占用 + 实时读/写速率条。
- 再下方：温度瓦片（电池 / 充电器 / 电源等可用传感器；超过告警阈值变琥珀，超过故障阈值红色闪烁）。
- 右下：GPU 占用条 + 频率。
- 进程面板高度随“设置 → 进程行数”变化，可挤占 CPU 区域，但至少保留 40% 给 CPU；放不下时列表滚动。

## 数据来源

| 指标 | 来源 |
|---|---|
| CPU 总/分核 | `/proc/stat`，1s 差值 |
| CPU 频率 | `/sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq` |
| RAM | `/proc/meminfo`（优先 MemAvailable） |
| 磁盘用量 | `StatFs("/data")` |
| 磁盘读写 | `/proc/diskstats`（mmcblk0 / sda，512B/sector 差值） |
| GPU | `/sys/class/kgsl/kgsl-3d0/gpubusy`（0 0 视为空闲 0%）、`gpuclk` |
| 温度 | `/sys/class/power_supply/{battery,usb,bms}/temp`；`thermal_zone*` 读取失败时自动跳过 |
| 电池 | `ACTION_BATTERY_CHANGED` + `/sys/class/power_supply/battery/current_now`（mA） |
| WiFi | `WifiManager` 连接状态 + RSSI；可访问性用 114.114.114.114 / 223.5.5.5 的 TCP 探测，10s 探测一次，连续 2 次失败才显示叉（避免闪烁） |
| 蜂窝 | `TelephonyManager.getNetworkType()`；API 22 无 NR，5G 常量仅在系统上报时显示 |
| 有线/USB | `ConnectivityManager.TYPE_ETHERNET` / `UsbManager` + 充电插入类型 |
| 网络速率 | `/proc/net/dev` 全接口字节差值 |
| 进程 | `/proc/[pid]/stat` + `/proc/[pid]/cmdline`（名称缓存）；CPU% 取 2s 窗口，避免老设备开销过大 |

## 设置（Menu 或右上角设置图标）

- 全屏显示：开/关（立即应用，持久化）。
- 刷新间隔：0.1s ~ 5.0s，步进 0.1s，默认 1.0s。
- 进程行数：3 ~ 20，默认 8。
- 电流倍率：0.50 ~ 2.00（校准 `current_now`）。
- 电流偏移：-1000 ~ +1000 mA。
- 温度偏移：-20.0 ~ +20.0 °C。
- 温度告警：30 ~ 120 °C（达到即红色闪烁）。
- 恢复默认、关闭。

## Root 行为

- 启动时用 `su -c id` 检测，顶栏显示 `ROOT` / `NO ROOT`。
- 绝大多数数据直接读取，不依赖 root；读不到的节点（如本机的 `thermal_zone*`，即使 root 也被 SELinux 拒绝）显示为“无效”或空槽位，不崩溃。
- 若设备无 root，进程列表读不到时显示“需要 ROOT”；GPU/温度不可读时显示“无效”。

## 已知限制

- **5G**：Android 5.1（API 22）没有 NR 网络类型，本机永远显示到 4G；代码已预留 `NETWORK_TYPE_NR` 映射。
- **温度**：OPPO A33 的 thermal_zone 节点对本应用和 root 都拒绝访问，因此只显示电池/充电器/电源等可读传感器；其余为 `--`。
- 进程采样为 2s（指标仍 1s），以控制老设备 CPU 占用；设置中的 0.1s 只影响指标/UI 刷新。
- 目标横屏 960×540 上为像素级字体；更高密度按 16px 的整数倍选择字号，保证不发虚。

## 文件

- 入口/全屏/设置：`app/src/main/java/com/example/panel/dash/DashActivity.java`
- 绘制面板：`.../dash/{CpuPanelView,RamPanelView,DiskPanelView,TempPanelView,GpuPanelView,ProcessPanelView,TopBarView}.java`
- 采样：`SystemCollector.java`、`DashPoller.java`
- 设置：`SettingsOverlayView.java`、`DashSettings.java`
- 图标：`PixelIcons.java`（整数坐标、关闭抗锯齿、全部矩形/线段，无圆角）
- 字体：`app/src/main/assets/fonts/`（DSEG7、JetBrains Mono、文泉驿点阵宋体 16px 子集）
- 设计样张规范：`UI_SPEC.md`；样张页面：`MainActivity`（非启动）

## 截图

- `screenshots/final3_rot.png`：最终面板（960×540）
- `screenshots/fs_off_rot.png`：全屏关闭 + 设置页
- `screenshots/fault1_rot.png` / `fault2_rot.png`：温度故障红色闪烁两相
- `screenshots/cputap_rot.png`：CPU 分核模式
