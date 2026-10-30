/*
 * PANELINK PC telemetry service.
 *
 * Data-only protocol:
 *   UDP 47823 : "PANELINK_DISCOVER" -> {"type":"offer","name":...,"port":...}
 *   TCP 47823 : newline-delimited JSON state frames (PC -> phone)
 *
 * The service never parses or executes anything received from the network. It only
 * answers discovery and streams its own metrics, so the protocol is data-only.
 *
 * Build (MinGW-w64):
 *   gcc -O2 -o panelink_pc.exe panelink_pc.c -lws2_32 -lpdh -ldxgi -lole32 \
 *       -loleaut32 -lwbemuuid -lpowrprof -lntdll -ladvapi32
 */

#define WIN32_LEAN_AND_MEAN
#define _WIN32_WINNT 0x0601
#define _CRT_SECURE_NO_WARNINGS
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <pdh.h>
#include <pdhmsg.h>
#include <dxgi.h>
#include <wbemidl.h>
#include <oleauto.h>
#include <powrprof.h>
#include <stdio.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdlib.h>
#include <wchar.h>
#include <string.h>
#include <time.h>
#include <math.h>

#pragma comment(lib, "ws2_32.lib")
#pragma comment(lib, "pdh.lib")
#pragma comment(lib, "dxgi.lib")
#pragma comment(lib, "dxguid.lib")
#pragma comment(lib, "ole32.lib")
#pragma comment(lib, "oleaut32.lib")
#pragma comment(lib, "wbemuuid.lib")
#pragma comment(lib, "powrprof.lib")
#pragma comment(lib, "advapi32.lib")

#ifndef PROCESSOR_INFORMATION_CLASS
#define PROCESSOR_INFORMATION_CLASS 11
#endif

typedef LONG (WINAPI *NtQuerySystemInformationFn)(ULONG, PVOID, ULONG, PULONG);
#define SYSTEM_PROCESSOR_PERFORMANCE_INFORMATION 8

typedef struct {
    LARGE_INTEGER IdleTime;
    LARGE_INTEGER KernelTime;
    LARGE_INTEGER UserTime;
    LARGE_INTEGER DpcTime;
    LARGE_INTEGER InterruptTime;
    ULONG InterruptCount;
} SPI;

typedef struct {
    ULONGLONG idle;
    ULONGLONG kernel;
    ULONGLONG user;
} CpuTicks;

typedef struct {
    ULONG Number;
    ULONG MaxMhz;
    ULONG CurrentMhz;
    ULONG MhzLimit;
    ULONG MaxIdleState;
    ULONG CurrentIdleState;
} ProcessorPowerInfo;

typedef struct {
    char name[32];
    char letter;
    unsigned long long total;
    unsigned long long used;
    double read_bps;
    double write_bps;
} DiskInfo;

typedef struct {
    char name[128];
    int load;
    int clock_mhz;
    int temp_c;
    int fan_pct;
    unsigned long long mem_used_mb;
    unsigned long long mem_total_mb;
} GpuInfo;

typedef struct {
    int present;
    int pct;
    int charging;
    int current_ma;
} BatteryInfo;

typedef struct {
    char name[64];
    int value;
    int percent;
} FanInfo;

typedef struct {
    char name[64];
    float celsius;
} TempInfo;

static int g_port = 47823;
static int g_interval_ms = 1000;
static int g_send_cpu = 1, g_send_mem = 1, g_send_gpu = 1, g_send_disk = 1;
static int g_send_battery = 1, g_send_fans = 1, g_send_temps = 1;
static char g_name[64] = "PC";
static volatile LONG g_running = 1;

static NtQuerySystemInformationFn pNtQuerySystemInformation;
static CpuTicks g_prev_total;
static CpuTicks *g_prev_cores;
static int g_core_count;
static int *g_freq_mhz;
static float g_cpu_total;
static float *g_cpu_cores;

static PDH_HQUERY g_disk_query;
static PDH_HCOUNTER g_disk_read;
static PDH_HCOUNTER g_disk_write;
static DiskInfo g_disks[8];
static int g_disk_count;

static GpuInfo g_gpus[8];
static int g_gpu_count;

static BatteryInfo g_battery;

static FanInfo g_fans[8];
static int g_fan_count;
static TempInfo g_temps[8];
static int g_temp_count;
static int g_acpi_temp_count;
static int g_acpi_fan_count;
static DWORD g_last_wmi_ms;

static const char *json_escape(const char *src, char *dst, size_t cap) {
    size_t j = 0;
    for (size_t i = 0; src[i] && j + 2 < cap; i++) {
        unsigned char c = (unsigned char) src[i];
        if (c == '"' || c == '\\') {
            dst[j++] = '\\';
            dst[j++] = (char) c;
        } else if (c == '\n' || c == '\r') {
            dst[j++] = ' ';
        } else if (c >= 32) {
            dst[j++] = (char) c;
        }
    }
    dst[j] = 0;
    return dst;
}

static void append(char *buf, size_t cap, const char *fmt, ...) {
    size_t used = strlen(buf);
    if (used >= cap - 1) return;
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf + used, cap - used, fmt, ap);
    va_end(ap);
}

static unsigned long long filetime_to_u64(FILETIME ft) {
    ULARGE_INTEGER v;
    v.LowPart = ft.dwLowDateTime;
    v.HighPart = ft.dwHighDateTime;
    return v.QuadPart;
}

static void read_config(void) {
    char path[MAX_PATH];
    GetModuleFileNameA(NULL, path, MAX_PATH);
    char *slash = strrchr(path, '\\');
    if (slash) strcpy(slash + 1, "panelink.ini");
    g_port = GetPrivateProfileIntA("panelink", "port", 47823, path);
    g_interval_ms = GetPrivateProfileIntA("panelink", "interval_ms", 1000, path);
    if (g_interval_ms < 100) g_interval_ms = 100;
    if (g_interval_ms > 5000) g_interval_ms = 5000;
    GetPrivateProfileStringA("panelink", "name", "", g_name, sizeof(g_name), path);
    if (g_name[0] == 0) {
        DWORD n = sizeof(g_name);
        if (!GetComputerNameA(g_name, &n)) strcpy(g_name, "PC");
    }
    g_send_cpu = GetPrivateProfileIntA("panelink", "send_cpu", 1, path);
    g_send_mem = GetPrivateProfileIntA("panelink", "send_mem", 1, path);
    g_send_gpu = GetPrivateProfileIntA("panelink", "send_gpu", 1, path);
    g_send_disk = GetPrivateProfileIntA("panelink", "send_disk", 1, path);
    g_send_battery = GetPrivateProfileIntA("panelink", "send_battery", 1, path);
    g_send_fans = GetPrivateProfileIntA("panelink", "send_fans", 1, path);
    g_send_temps = GetPrivateProfileIntA("panelink", "send_temps", 1, path);
}

static void cpu_init(void) {
    HMODULE ntdll = GetModuleHandleA("ntdll.dll");
    if (ntdll) pNtQuerySystemInformation =
            (NtQuerySystemInformationFn) GetProcAddress(ntdll, "NtQuerySystemInformation");
    SYSTEM_INFO si;
    GetSystemInfo(&si);
    g_core_count = (int) si.dwNumberOfProcessors;
    if (g_core_count < 1) g_core_count = 1;
    if (g_core_count > 64) g_core_count = 64;
    g_prev_cores = (CpuTicks *) calloc(g_core_count, sizeof(CpuTicks));
    g_cpu_cores = (float *) calloc(g_core_count, sizeof(float));
    g_freq_mhz = (int *) calloc(g_core_count, sizeof(int));
    memset(&g_prev_total, 0, sizeof(g_prev_total));
}

static void sample_cpu(void) {
    if (!pNtQuerySystemInformation) return;
    size_t bytes = (size_t) g_core_count * sizeof(SPI);
    SPI *info = (SPI *) calloc(g_core_count, sizeof(SPI));
    if (!info) return;
    ULONG ret = 0;
    LONG status = pNtQuerySystemInformation(SYSTEM_PROCESSOR_PERFORMANCE_INFORMATION,
                                            info, (ULONG) bytes, &ret);
    if (status != 0) {
        free(info);
        return;
    }
    CpuTicks total = {0, 0, 0};
    double usage_sum = 0;
    for (int i = 0; i < g_core_count; i++) {
        CpuTicks now;
        now.idle = (ULONGLONG) info[i].IdleTime.QuadPart;
        now.kernel = (ULONGLONG) info[i].KernelTime.QuadPart;
        now.user = (ULONGLONG) info[i].UserTime.QuadPart;
        ULONGLONG dt = (now.idle + now.kernel + now.user)
                - (g_prev_cores[i].idle + g_prev_cores[i].kernel + g_prev_cores[i].user);
        ULONGLONG di = now.idle - g_prev_cores[i].idle;
        float usage = 0;
        if (dt > 0) usage = (float) (100.0 * (double) (dt - di) / (double) dt);
        if (usage < 0) usage = 0;
        if (usage > 100) usage = 100;
        g_cpu_cores[i] = usage;
        usage_sum += usage;
        total.idle += now.idle;
        total.kernel += now.kernel;
        total.user += now.user;
        g_prev_cores[i] = now;
    }
    g_cpu_total = (float) (usage_sum / g_core_count);

    ProcessorPowerInfo *ppi =
            (ProcessorPowerInfo *) calloc(g_core_count, sizeof(ProcessorPowerInfo));
    if (ppi) {
        if (CallNtPowerInformation(PROCESSOR_INFORMATION_CLASS, NULL, 0, ppi,
                                   (ULONG) (g_core_count * sizeof(ProcessorPowerInfo))) == 0) {
            for (int i = 0; i < g_core_count; i++) {
                g_freq_mhz[i] = (int) ppi[i].CurrentMhz;
            }
        }
        free(ppi);
    }
    g_prev_total = total;
    free(info);
}

static void disk_init(void) {
    if (PdhOpenQueryW(NULL, 0, &g_disk_query) != ERROR_SUCCESS) return;
    PdhAddEnglishCounterW(g_disk_query, L"\\PhysicalDisk(*)\\Disk Read Bytes/sec", 0, &g_disk_read);
    PdhAddEnglishCounterW(g_disk_query, L"\\PhysicalDisk(*)\\Disk Write Bytes/sec", 0, &g_disk_write);
    PdhCollectQueryData(g_disk_query);
}

static double find_disk_counter(PDH_HCOUNTER counter, char letter) {
    if (!counter) return 0;
    DWORD size = 0, count = 0;
    PdhGetFormattedCounterArrayW(counter, PDH_FMT_DOUBLE, &size, &count, NULL);
    if (size == 0 || count == 0) return 0;
    PDH_FMT_COUNTERVALUE_ITEM_W *items = (PDH_FMT_COUNTERVALUE_ITEM_W *) malloc(size);
    if (!items) return 0;
    double result = 0;
    if (PdhGetFormattedCounterArrayW(counter, PDH_FMT_DOUBLE, &size, &count, items)
        == ERROR_SUCCESS) {
        for (DWORD i = 0; i < count; i++) {
            if (items[i].szName) {
                char name[128];
                WideCharToMultiByte(CP_ACP, 0, items[i].szName, -1, name, sizeof(name), NULL, NULL);
                for (char *q = name; q[0]; q++) {
                    if (q[0] == letter && q[1] == ':') {
                        result = items[i].FmtValue.doubleValue;
                        break;
                    }
                }
                if (result > 0) break;
            }
        }
    }
    free(items);
    return result < 0 ? 0 : result;
}

static void sample_disks(void) {
    g_disk_count = 0;
    if (g_disk_query) PdhCollectQueryData(g_disk_query);
    DWORD mask = GetLogicalDrives();
    for (char letter = 'A'; letter <= 'Z' && g_disk_count < 8; letter++) {
        if (!(mask & (1u << (letter - 'A')))) continue;
        char root[4] = {letter, ':', '\\', 0};
        if (GetDriveTypeA(root) != DRIVE_FIXED) continue;
        ULARGE_INTEGER total, freeb, avail;
        if (!GetDiskFreeSpaceExA(root, &avail, &total, &freeb)) continue;
        DiskInfo *d = &g_disks[g_disk_count++];
        memset(d, 0, sizeof(*d));
        snprintf(d->name, sizeof(d->name), "%c:", letter);
        d->letter = letter;
        d->total = total.QuadPart;
        d->used = total.QuadPart - freeb.QuadPart;
        d->read_bps = find_disk_counter(g_disk_read, letter);
        d->write_bps = find_disk_counter(g_disk_write, letter);
    }
}

/* ---------------------------------------------------------------- GPU / NVML */

typedef void *nvmlDevice_t;
typedef struct { unsigned int gpu; unsigned int memory; } nvmlUtilization_t;
typedef struct { unsigned long long total, free, used; } nvmlMemory_t;
typedef int (*nvmlInitFn)(void);
typedef int (*nvmlShutdownFn)(void);
typedef int (*nvmlGetCountFn)(unsigned int *);
typedef int (*nvmlGetHandleFn)(unsigned int, nvmlDevice_t *);
typedef int (*nvmlGetNameFn)(nvmlDevice_t, char *, unsigned int);
typedef int (*nvmlGetUtilFn)(nvmlDevice_t, nvmlUtilization_t *);
typedef int (*nvmlGetClockFn)(nvmlDevice_t, int, unsigned int *);
typedef int (*nvmlGetTempFn)(nvmlDevice_t, int, unsigned int *);
typedef int (*nvmlGetFanFn)(nvmlDevice_t, unsigned int *);
typedef int (*nvmlGetMemoryFn)(nvmlDevice_t, nvmlMemory_t *);

static HMODULE g_nvml;
static nvmlGetCountFn nvml_count;
static nvmlGetHandleFn nvml_handle;
static nvmlGetNameFn nvml_name;
static nvmlGetUtilFn nvml_util;
static nvmlGetClockFn nvml_clock;
static nvmlGetTempFn nvml_temp;
static nvmlGetFanFn nvml_fan;
static nvmlGetMemoryFn nvml_mem;
static nvmlInitFn nvml_init;
static nvmlShutdownFn nvml_shutdown;

static void nvml_init_lib(void) {
    const char *paths[] = {
            "nvml.dll",
            "C:\\Program Files\\NVIDIA Corporation\\NVSMI\\nvml.dll",
            "C:\\Windows\\System32\\nvml.dll"
    };
    for (int i = 0; i < 3 && !g_nvml; i++) g_nvml = LoadLibraryA(paths[i]);
    if (!g_nvml) return;
    nvml_init = (nvmlInitFn) GetProcAddress(g_nvml, "nvmlInit_v2");
    nvml_shutdown = (nvmlShutdownFn) GetProcAddress(g_nvml, "nvmlShutdown");
    nvml_count = (nvmlGetCountFn) GetProcAddress(g_nvml, "nvmlDeviceGetCount_v2");
    nvml_handle = (nvmlGetHandleFn) GetProcAddress(g_nvml, "nvmlDeviceGetHandleByIndex_v2");
    nvml_name = (nvmlGetNameFn) GetProcAddress(g_nvml, "nvmlDeviceGetName");
    nvml_util = (nvmlGetUtilFn) GetProcAddress(g_nvml, "nvmlDeviceGetUtilizationRates");
    nvml_clock = (nvmlGetClockFn) GetProcAddress(g_nvml, "nvmlDeviceGetClockInfo");
    nvml_temp = (nvmlGetTempFn) GetProcAddress(g_nvml, "nvmlDeviceGetTemperature");
    nvml_fan = (nvmlGetFanFn) GetProcAddress(g_nvml, "nvmlDeviceGetFanSpeed");
    nvml_mem = (nvmlGetMemoryFn) GetProcAddress(g_nvml, "nvmlDeviceGetMemoryInfo");
    if (nvml_init) nvml_init();
}

static int g_nvml_count;

static void sample_nvml(void) {
    if (!nvml_count || !nvml_handle) return;
    unsigned int count = 0;
    if (nvml_count(&count) != 0) return;
    if (count > 6) count = 6;
    g_nvml_count = (int) count;
    for (unsigned int i = 0; i < count && g_gpu_count < 8; i++) {
        nvmlDevice_t dev;
        if (nvml_handle(i, &dev) != 0) continue;
        GpuInfo *gpu = &g_gpus[g_gpu_count++];
        memset(gpu, 0, sizeof(*gpu));
        gpu->load = -1;
        gpu->clock_mhz = -1;
        gpu->temp_c = -1;
        gpu->fan_pct = -1;
        char name[128] = {0};
        if (nvml_name) nvml_name(dev, name, sizeof(name));
        if (name[0]) snprintf(gpu->name, sizeof(gpu->name), "%s", name);
        else snprintf(gpu->name, sizeof(gpu->name), "NVIDIA GPU %u", i);
        nvmlUtilization_t util;
        if (nvml_util && nvml_util(dev, &util) == 0) gpu->load = (int) util.gpu;
        unsigned int mhz = 0;
        if (nvml_clock && nvml_clock(dev, 1, &mhz) == 0) gpu->clock_mhz = (int) mhz;
        unsigned int temp = 0;
        if (nvml_temp && nvml_temp(dev, 0, &temp) == 0) gpu->temp_c = (int) temp;
        unsigned int fan = 0;
        if (nvml_fan && nvml_fan(dev, &fan) == 0) gpu->fan_pct = (int) fan;
        nvmlMemory_t mem;
        if (nvml_mem && nvml_mem(dev, &mem) == 0) {
            gpu->mem_total_mb = mem.total / (1024ull * 1024ull);
            gpu->mem_used_mb = mem.used / (1024ull * 1024ull);
        }
    }
}

static void sample_dxgi_extra(void) {
    IDXGIFactory1 *factory = NULL;
    if (FAILED(CreateDXGIFactory1(&IID_IDXGIFactory1, (void **) &factory))) return;
    for (UINT i = 0; g_gpu_count < 8; i++) {
        IDXGIAdapter1 *adapter = NULL;
        if (factory->lpVtbl->EnumAdapters1(factory, i, &adapter) != S_OK) break;
        DXGI_ADAPTER_DESC1 desc;
        if (adapter->lpVtbl->GetDesc1(adapter, &desc) == S_OK) {
            if ((desc.Flags & DXGI_ADAPTER_FLAG_SOFTWARE) == 0
                && wcsstr(desc.Description, L"Basic Render") == NULL
                && desc.VendorId != 0x10DE) {
                GpuInfo *gpu = &g_gpus[g_gpu_count++];
                memset(gpu, 0, sizeof(*gpu));
                gpu->load = -1;
                gpu->clock_mhz = -1;
                gpu->temp_c = -1;
                gpu->fan_pct = -1;
                WideCharToMultiByte(CP_UTF8, 0, desc.Description, -1, gpu->name,
                                    sizeof(gpu->name), NULL, NULL);
            }
        }
        adapter->lpVtbl->Release(adapter);
    }
    factory->lpVtbl->Release(factory);
}

static void sample_gpus(void) {
    g_gpu_count = 0;
    if (g_send_gpu) {
        sample_nvml();
        sample_dxgi_extra();
    }
}

/* ------------------------------------------------------------------- WMI */

static int wmi_initialized;

static int wmi_connect(IWbemLocator *locator, const wchar_t *ns, IWbemServices **services) {
    BSTR bns = SysAllocString(ns);
    HRESULT hr = locator->lpVtbl->ConnectServer(locator, bns, NULL, NULL, NULL, 0, NULL, NULL,
                                                services);
    SysFreeString(bns);
    if (SUCCEEDED(hr) && services && *services) {
        CoSetProxyBlanket((IUnknown *) *services, RPC_C_AUTHN_WINNT, RPC_C_AUTHZ_NONE, NULL,
                          RPC_C_AUTHN_LEVEL_CALL, RPC_C_IMP_LEVEL_IMPERSONATE, NULL, EOAC_NONE);
        return 1;
    }
    return 0;
}

static void wmi_query_temps(IWbemServices *services) {
    BSTR lang = SysAllocString(L"WQL");
    BSTR query = SysAllocString(L"SELECT * FROM MSAcpi_ThermalZoneTemperature");
    IEnumWbemClassObject *enumerator = NULL;
    HRESULT hr = services->lpVtbl->ExecQuery(services, lang, query,
                                             WBEM_FLAG_FORWARD_ONLY | WBEM_FLAG_RETURN_IMMEDIATELY,
                                             NULL, &enumerator);
    SysFreeString(lang);
    SysFreeString(query);
    if (FAILED(hr) || !enumerator) return;
    IWbemClassObject *obj = NULL;
    ULONG returned = 0;
    while (g_temp_count < 8 && enumerator->lpVtbl->Next(enumerator, 500, 1, &obj, &returned) == S_OK
           && returned == 1) {
        VARIANT var;
        VariantInit(&var);
        if (SUCCEEDED(obj->lpVtbl->Get(obj, L"CurrentTemperature", 0, &var, NULL, NULL))
            && (var.vt == VT_I4 || var.vt == VT_UI4)) {
            double kelvin10 = var.vt == VT_I4 ? (double) var.lVal : (double) var.ulVal;
            float c = (float) (kelvin10 / 10.0 - 273.15);
            if (c > 10 && c < 120) {
                TempInfo *t = &g_temps[g_temp_count++];
                snprintf(t->name, sizeof(t->name), "ACPI%d", g_temp_count);
                t->celsius = c;
            }
        }
        VariantClear(&var);
        obj->lpVtbl->Release(obj);
        obj = NULL;
    }
    enumerator->lpVtbl->Release(enumerator);
}

static void wmi_query_fans(IWbemServices *services) {
    BSTR lang = SysAllocString(L"WQL");
    BSTR query = SysAllocString(L"SELECT * FROM Win32_Fan");
    IEnumWbemClassObject *enumerator = NULL;
    HRESULT hr = services->lpVtbl->ExecQuery(services, lang, query, WBEM_FLAG_FORWARD_ONLY,
                                             NULL, &enumerator);
    SysFreeString(lang);
    SysFreeString(query);
    if (FAILED(hr) || !enumerator) return;
    IWbemClassObject *obj = NULL;
    ULONG returned = 0;
    while (g_fan_count < 8 && enumerator->lpVtbl->Next(enumerator, 500, 1, &obj, &returned) == S_OK
           && returned == 1) {
        VARIANT var;
        VariantInit(&var);
        if (SUCCEEDED(obj->lpVtbl->Get(obj, L"DesiredSpeed", 0, &var, NULL, NULL))
            && (var.vt == VT_I4 || var.vt == VT_UI4)) {
            long speed = var.vt == VT_I4 ? var.lVal : (long) var.ulVal;
            if (speed > 0) {
                FanInfo *f = &g_fans[g_fan_count++];
                snprintf(f->name, sizeof(f->name), "SYS FAN%d", g_fan_count);
                f->value = (int) speed;
                f->percent = 0;
            }
        }
        VariantClear(&var);
        obj->lpVtbl->Release(obj);
        obj = NULL;
    }
    enumerator->lpVtbl->Release(enumerator);
}

static void sample_wmi(void) {
    DWORD now = GetTickCount();
    if (g_last_wmi_ms && now - g_last_wmi_ms < 5000) return;
    g_last_wmi_ms = now;

    g_temp_count = 0;
    g_fan_count = 0;
    HRESULT hr = CoInitializeEx(NULL, COINIT_MULTITHREADED);
    int did_init = SUCCEEDED(hr);
    if (!wmi_initialized) {
        CoInitializeSecurity(NULL, -1, NULL, NULL, RPC_C_AUTHN_LEVEL_DEFAULT,
                             RPC_C_IMP_LEVEL_IMPERSONATE, NULL, EOAC_NONE, NULL);
        wmi_initialized = 1;
    }
    IWbemLocator *locator = NULL;
    hr = CoCreateInstance(&CLSID_WbemLocator, NULL, CLSCTX_INPROC_SERVER, &IID_IWbemLocator,
                          (void **) &locator);
    if (SUCCEEDED(hr) && locator) {
        IWbemServices *wmi = NULL;
        if (wmi_connect(locator, L"ROOT\\WMI", &wmi)) {
            wmi_query_temps(wmi);
            wmi->lpVtbl->Release(wmi);
        }
        wmi = NULL;
        if (wmi_connect(locator, L"ROOT\\CIMV2", &wmi)) {
            wmi_query_fans(wmi);
            wmi->lpVtbl->Release(wmi);
        }
        locator->lpVtbl->Release(locator);
    }
    if (did_init) CoUninitialize();
    g_acpi_temp_count = g_temp_count;
    g_acpi_fan_count = g_fan_count;
}

static void sample_battery(void) {
    SYSTEM_POWER_STATUS status;
    memset(&g_battery, 0, sizeof(g_battery));
    g_battery.current_ma = -1;
    if (!GetSystemPowerStatus(&status)) return;
    if (status.BatteryFlag == 128 || status.BatteryLifePercent == 255) return;
    g_battery.present = 1;
    g_battery.pct = status.BatteryLifePercent;
    g_battery.charging = status.ACLineStatus == 1;
}

static void fill_extra_sensors(void) {
    g_temp_count = g_acpi_temp_count;
    g_fan_count = g_acpi_fan_count;
    for (int i = 0; i < g_gpu_count && g_temp_count < 8; i++) {
        if (g_gpus[i].temp_c > 0) {
            TempInfo *t = &g_temps[g_temp_count++];
            snprintf(t->name, sizeof(t->name), "GPU%d", i);
            t->celsius = (float) g_gpus[i].temp_c;
        }
        if (g_gpus[i].fan_pct > 0 && g_fan_count < 8) {
            FanInfo *f = &g_fans[g_fan_count++];
            snprintf(f->name, sizeof(f->name), "GPU%d FAN", i);
            f->value = g_gpus[i].fan_pct;
            f->percent = 1;
        }
    }
}

static void collect_state(void) {
    sample_cpu();
    sample_disks();
    sample_gpus();
    sample_battery();
    sample_wmi();
    fill_extra_sensors();
}

static void build_json(char *buf, size_t cap) {
    char esc[160];
    buf[0] = 0;
    append(buf, cap, "{\"type\":\"state\",\"name\":\"%s\",\"time\":%llu",
           json_escape(g_name, esc, sizeof(esc)), (unsigned long long) time(NULL));
    if (g_send_cpu) {
        append(buf, cap, ",\"cpu\":{\"usage\":%.1f,\"cores\":[", g_cpu_total);
        for (int i = 0; i < g_core_count; i++) {
            append(buf, cap, "%s%.1f", i ? "," : "", g_cpu_cores[i]);
        }
        append(buf, cap, "],\"freqs\":[");
        for (int i = 0; i < g_core_count; i++) {
            append(buf, cap, "%s%d", i ? "," : "", g_freq_mhz[i]);
        }
        int maxf = 0;
        for (int i = 0; i < g_core_count; i++) if (g_freq_mhz[i] > maxf) maxf = g_freq_mhz[i];
        append(buf, cap, "],\"freq\":%d}", maxf);
    }
    if (g_send_mem) {
        MEMORYSTATUSEX mem;
        mem.dwLength = sizeof(mem);
        GlobalMemoryStatusEx(&mem);
        append(buf, cap, ",\"mem\":{\"used\":%llu,\"total\":%llu}",
               (unsigned long long) (mem.ullTotalPhys - mem.ullAvailPhys),
               (unsigned long long) mem.ullTotalPhys);
    }
    if (g_send_gpu) {
        append(buf, cap, ",\"gpus\":[");
        for (int i = 0; i < g_gpu_count; i++) {
            GpuInfo *g = &g_gpus[i];
            append(buf, cap,
                   "%s{\"name\":\"%s\",\"load\":%d,\"clock\":%d,\"temp\":%d,\"fan\":%d,"
                   "\"mem_used_mb\":%llu,\"mem_total_mb\":%llu}",
                   i ? "," : "", json_escape(g->name, esc, sizeof(esc)), g->load, g->clock_mhz,
                   g->temp_c, g->fan_pct, g->mem_used_mb, g->mem_total_mb);
        }
        append(buf, cap, "]");
    }
    if (g_send_disk) {
        append(buf, cap, ",\"disks\":[");
        for (int i = 0; i < g_disk_count; i++) {
            DiskInfo *d = &g_disks[i];
            append(buf, cap,
                   "%s{\"name\":\"%s\",\"used\":%llu,\"total\":%llu,\"read\":%.0f,\"write\":%.0f}",
                   i ? "," : "", d->name, d->used, d->total, d->read_bps, d->write_bps);
        }
        append(buf, cap, "]");
    }
    if (g_send_battery) {
        append(buf, cap,
               ",\"battery\":{\"present\":%s,\"pct\":%d,\"charging\":%s,\"current\":%d}",
               g_battery.present ? "true" : "false", g_battery.pct,
               g_battery.charging ? "true" : "false", g_battery.current_ma);
    }
    if (g_send_fans) {
        append(buf, cap, ",\"fans\":[");
        for (int i = 0; i < g_fan_count; i++) {
            append(buf, cap, "%s{\"name\":\"%s\",\"value\":%d,\"percent\":%s}",
                   i ? "," : "", json_escape(g_fans[i].name, esc, sizeof(esc)),
                   g_fans[i].value, g_fans[i].percent ? "true" : "false");
        }
        append(buf, cap, "]");
    }
    if (g_send_temps) {
        append(buf, cap, ",\"temps\":[");
        for (int i = 0; i < g_temp_count; i++) {
            append(buf, cap, "%s{\"name\":\"%s\",\"c\":%.1f}",
                   i ? "," : "", json_escape(g_temps[i].name, esc, sizeof(esc)),
                   g_temps[i].celsius);
        }
        append(buf, cap, "]");
    }
    append(buf, cap, "}\n");
}

/* --------------------------------------------------------------- network */

static DWORD WINAPI discovery_thread(LPVOID unused) {
    SOCKET sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (sock == INVALID_SOCKET) return 1;
    int reuse = 1;
    setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, (const char *) &reuse, sizeof(reuse));
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_ANY);
    addr.sin_port = htons((u_short) g_port);
    if (bind(sock, (struct sockaddr *) &addr, sizeof(addr)) != 0) {
        closesocket(sock);
        return 1;
    }
    char buffer[256];
    while (g_running) {
        struct sockaddr_in from;
        int from_len = sizeof(from);
        int n = recvfrom(sock, buffer, sizeof(buffer) - 1, 0, (struct sockaddr *) &from, &from_len);
        if (n <= 0) continue;
        buffer[n] = 0;
        if (strncmp(buffer, "PANELINK_DISCOVER", 17) == 0) {
            char reply[256];
            snprintf(reply, sizeof(reply), "{\"type\":\"offer\",\"name\":\"%s\",\"port\":%d}\n",
                     g_name, g_port);
            sendto(sock, reply, (int) strlen(reply), 0, (struct sockaddr *) &from, from_len);
        }
    }
    closesocket(sock);
    return 0;
}

static SOCKET listen_socket(void) {
    SOCKET sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (sock == INVALID_SOCKET) return INVALID_SOCKET;
    int reuse = 1;
    setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, (const char *) &reuse, sizeof(reuse));
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_ANY);
    addr.sin_port = htons((u_short) g_port);
    if (bind(sock, (struct sockaddr *) &addr, sizeof(addr)) != 0) {
        closesocket(sock);
        return INVALID_SOCKET;
    }
    if (listen(sock, 2) != 0) {
        closesocket(sock);
        return INVALID_SOCKET;
    }
    return sock;
}

static void serve_client(SOCKET client) {
    char hello[256];
    snprintf(hello, sizeof(hello), "{\"type\":\"hello\",\"name\":\"%s\",\"interval_ms\":%d}\n",
             g_name, g_interval_ms);
    send(client, hello, (int) strlen(hello), 0);
    char json[32768];
    while (g_running) {
        collect_state();
        build_json(json, sizeof(json));
        int sent = send(client, json, (int) strlen(json), 0);
        if (sent <= 0) break;
        Sleep(g_interval_ms);
    }
    closesocket(client);
}

int main(void) {
    SetConsoleOutputCP(CP_UTF8);
    read_config();
    WSADATA wsa;
    if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) {
        fprintf(stderr, "WSAStartup failed\n");
        return 1;
    }
    cpu_init();
    disk_init();
    nvml_init_lib();
    sample_battery();

    printf("PANELINK PC service  name=%s port=%d interval=%dms\n", g_name, g_port, g_interval_ms);
    printf("Discovery: UDP %d  State: TCP %d  (data-only JSON)\n", g_port, g_port);

    CreateThread(NULL, 0, discovery_thread, NULL, 0, NULL);
    SOCKET server = listen_socket();
    if (server == INVALID_SOCKET) {
        fprintf(stderr, "Cannot listen on port %d\n", g_port);
        return 1;
    }

    while (g_running) {
        struct sockaddr_in client_addr;
        int len = sizeof(client_addr);
        SOCKET client = accept(server, (struct sockaddr *) &client_addr, &len);
        if (client == INVALID_SOCKET) continue;
        char ip[64];
        inet_ntop(AF_INET, &client_addr.sin_addr, ip, sizeof(ip));
        printf("Client %s connected\n", ip);
        fflush(stdout);
        serve_client(client);
        printf("Client disconnected\n");
        fflush(stdout);
    }
    closesocket(server);
    WSACleanup();
    return 0;
}
