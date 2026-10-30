package com.example.panel.ui;

import android.content.Context;
import android.graphics.Typeface;

import java.util.HashMap;
import java.util.Map;

/**
 * Runtime copy deck for the specimen. The Chinese edition is a design mock-up, not a
 * shipping localisation: labels are translated at attach time so the specimen stays a
 * single layout. Production screens should move these strings into values-zh.
 */
public final class PanelI18n {

    private static final Map<String, String> ZH = new HashMap<>();

    private static boolean chinese;

    static {
        // Masthead and frame
        put("HMI SPECIMEN / 001", "HMI 样张 / 001");
        put("AERONAUTICAL INSTRUMENTATION", "航空仪表 / 工业 HMI");
        put("PANEL // UI SPECIMEN 001", "PANEL // 界面样张 001");
        put("END OF SHEET", "样张结束");

        // 01 buttons
        put("BUTTONS", "按钮");
        put("TAP / NO RIPPLE", "点按 / 无涟漪");
        put("PRIMARY", "主操作");
        put("EXECUTE", "执行");
        put("SECONDARY", "次操作");
        put("STANDBY", "待机");
        put("DISABLED", "禁用");
        put("UNAVAILABLE", "不可用");
        put("DANGER", "危险");
        put("ABORT", "中止");
        put("PRESSED = WHITE 8% OVERLAY / NO RIPPLE / NO ELEVATION", "按下 = 白色 8% 覆盖 / 无涟漪 / 无阴影");

        // 02 badges
        put("STATUS BADGES", "状态徽章");
        put("OUTLINE / SOLID", "描边 / 实心");
        put("NORMAL", "正常");
        put("PASS", "通过");
        put("LOCKED", "已锁定");
        put("CAUTION", "注意");
        put("FAULT", "故障");
        put("OUTLINE = NORMAL / SOLID + #000 TEXT = SPECIAL / FAULT BLINKS CONTINUOUSLY",
                "描边 = 常规 / 实心 + #000 文字 = 特别 / 故障持续闪烁");

        // 03 telemetry
        put("TELEMETRY", "遥测");
        put("DSEG7 / ZERO-PAD", "DSEG7 / 补零");
        put("HDG", "航向");
        put("ALT", "高度");
        put("IAS", "空速");
        put("V/S", "升降率");
        put("OAT / HIGH", "外界温度 / 过高");
        put("FUEL", "燃油");
        put("TRK", "航迹");
        put("LINK", "链路");
        put("FIXED WIDTH ZERO-PADDING / NO DATA = --- / RED = OUT OF RANGE",
                "固定宽度补零 / 无数据 = --- / 红色 = 超限");

        // 04 table
        put("TABLE", "表格");
        put("2DP HEAD / 1DP ROWS", "2DP 表头 / 1DP 行");
        put("SYSTEM", "系统");
        put("READING", "读数");
        put("UNIT", "单位");
        put("STATE", "状态");
        put("ENG 1", "发动机 1");
        put("APU", "辅助动力");
        put("HYD 1", "液压 1");
        put("FUEL 2", "燃油 2");
        put("NORM", "正常");
        put("STBY", "待机");
        put("ROW VALUES SET IN DSEG7 / STATE IS ALWAYS SPELLED OUT",
                "行内数值使用 DSEG7 / 状态必须写明文字");

        // 05 inputs / switches
        put("INPUTS / SWITCH", "输入 / 开关");
        put("FOCUS / ERROR", "聚焦 / 错误");
        put("CALLSIGN", "呼号");
        put("DEFAULT", "默认");
        put("GROSS WEIGHT", "总重");
        put("ERROR", "错误");
        put("MAX 120000 KG / VALUE REJECTED", "最大 120000 KG / 数值被拒绝");
        put("AUTH", "授权");
        put("FOCUSED / SPECIMEN", "聚焦 / 样张");
        put("FOCUS = STROKE 100% / ERROR = RED STROKE + RED TEXT / NO RIPPLE",
                "聚焦 = 100% 描边 / 错误 = 红描边 + 红字 / 无涟漪");
        put("EXTERNAL POWER", "外部电源");
        put("ARM SYSTEMS", "系统预位");
        put("SQUARE SWITCH / 48DP TARGET / STATE JUMPS WITH NO ANIMATION",
                "方形开关 / 48DP 触控 / 状态跳变无动画");
        put("ON", "开");
        put("OFF", "关");

        // 06 alarms
        put("ALARM BANNERS", "告警横幅");
        put("BLINK / CONTINUOUS", "闪烁 / 持续");
        put("WARNING // BLEED AIR LEAK — ENG 2", "警告 // 引气泄漏 — 发动机 2");
        put("CAUTION // FUEL IMBALANCE — TRIM 1.4", "注意 // 燃油不平衡 — 配平 1.4");
        put("INFO // DATA LINK DEGRADED", "提示 // 数据链降级");
        put("ONLY FAULT CLASS BLINKS: 0.5S STEP / CONTINUOUS / BLACK TEXT ON STATUS FILL",
                "仅故障类闪烁：0.5S 步进 / 持续 / 色块黑字");

        // 07 charts
        put("CHARTS", "图表");
        put("STATIC / NO LIB", "静态 / 无第三方库");
        put("CPU LOAD", "CPU 占用");
        put("LAST 14 SAMPLES", "最近 14 个采样");
        put("RAM USAGE", "内存占用");
        put("4 BUCKETS / TOTAL 058%", "4 个分区 / 总计 058%");
        put("DASHED RED THRESHOLD 080% / BAR THRESHOLDS 070% WARN 090% CRIT / ZERO ANIMATION",
                "红色虚线阈值 080% / 条形阈值 070% 警告 090% 严重 / 零动画");
        put("KERNEL", "内核");
        put("APPS", "应用");
        put("CACHE", "缓存");
        put("MEDIA", "媒体");
        put("OK", "正常");
        put("WARN", "注意");
        put("CRIT", "严重");

        // Tokens
        put("DESIGN TOKENS", "设计令牌");
        put("0 RADIUS / 1DP", "0 圆角 / 1DP");
        put("COLOR", "颜色");
        put("BLACK", "黑");
        put("WHITE", "白");
        put("GREEN", "绿");
        put("AMBER", "琥珀");
        put("RED", "红");
        put("BASE SURFACE", "基础背景");
        put("TEXT / HAIRLINE", "文字 / 细线");
        put("NORMAL / LOCKED", "正常 / 锁定");
        put("FAULT / DANGER", "故障 / 危险");
        put("LINE", "线条");
        put("TYPE", "字体");
        put("NUMERIC", "数字");
        put("TEXT", "文本");
        put("JETBRAINS MONO", "文泉驿点阵宋体 16PX");
        put("SPACE 08 / 16 / 24 / STROKE 1DP / TOUCH 48DP / RADIUS 0DP / ELEVATION 0DP",
                "间距 08 / 16 / 24 / 描边 1DP / 触控 48DP / 圆角 0DP / 高度 0DP");
        put("ACCEPTANCE", "验收");
        put("[X] ZERO RADIUS / ZERO RIPPLE / ZERO DECORATION\n"
                        + "[X] EVERY NUMERAL IN DSEG7 / ZERO-PADDED\n"
                        + "[X] SOLID + BLACK TEXT ONLY FOR SPECIAL STATES\n"
                        + "[X] BLINK ONLY FAULT BADGE + WARNING BANNER (CONTINUOUS)\n"
                        + "[X] CHARTS STATIC / DASHED STATUS THRESHOLD\n"
                        + "[X] PALETTE = BLACK + WHITE + GREEN / AMBER / RED",
                "[X] 零圆角 / 零涟漪 / 零装饰\n"
                        + "[X] 所有数字使用 DSEG7 / 固定补零\n"
                        + "[X] 实心色块 + 黑字仅用于特别状态\n"
                        + "[X] 仅故障徽章与告警横幅持续闪烁\n"
                        + "[X] 图表静态 / 状态色虚线阈值\n"
                        + "[X] 配色 = 黑 + 白 + 绿 / 琥珀 / 红");
    }

    private PanelI18n() {
    }

    private static void put(String english, String chineseText) {
        ZH.put(english, chineseText);
    }

    public static void setChinese(boolean enabled) {
        chinese = enabled;
    }

    public static boolean isChinese() {
        return chinese;
    }

    public static String t(Context context, String english) {
        if (!chinese || english == null) {
            return english;
        }
        String translated = ZH.get(english);
        return translated != null ? translated : english;
    }

    public static Typeface uiTypeface(Context context) {
        return chinese ? PanelTypefaces.pixel(context) : PanelTypefaces.mono(context);
    }
}
