package com.kaoyan.widget;

import android.app.Activity;
import android.content.Context;
import android.webkit.JavascriptInterface;
import java.io.File;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class Bridge {
    private Activity a;
    public Bridge(Activity a) { this.a = a; }

    @JavascriptInterface public void pushNow() { a.runOnUiThread(new PushTask(a)); }
    @JavascriptInterface public void reload() { a.runOnUiThread(new LoadTask(a)); }
    @JavascriptInterface public void setExam(String d) { a.runOnUiThread(new ExamTask(a, d)); }
    @JavascriptInterface public void pickWords() { a.runOnUiThread(new PickTask(a)); }
    @JavascriptInterface public void resetWords() { a.runOnUiThread(new ResetTask(a)); }
    @JavascriptInterface public void openSetup() { a.runOnUiThread(new SetupTask(a)); }
    @JavascriptInterface public void setPushTime(String s, String e) { a.runOnUiThread(new PushTimeTask(a, s, e)); }
    @JavascriptInterface public void toggleDanmaku() { a.runOnUiThread(new DanmakuToggleTask(a)); }
    @JavascriptInterface public void requestOverlay() { a.runOnUiThread(new OverlayTask(a)); }
    @JavascriptInterface public void setDanmakuCfg(String on, String off, String size, String color, String font, String bold) { a.runOnUiThread(new DanmakuCfgTask(a, on, off, size, color, font, bold)); }

    static void doPush(Context ctx) {
        try {
            List<Words.W> ws = Words.load(ctx);
            JSONObject st = Store.loadState(ctx);
            Engine.ensureState(st);
            if (st.optString("exam", "").length() < 10) return; // 未完成初始设置不推送
            String today = Engine.today();
            if (!today.equals(st.optString("day", ""))) {
                Engine.buildPlan(st, ws.size(), today);
                Store.saveState(ctx, st);
            }
            String[] dm = Engine.pushNext(ctx, ws, st);
            if (dm != null && dm.length > 0) BotService.showBatch(ctx, dm);
        } catch (Throwable e) { }
    }

    static void doSetExam(Context ctx, String d) {
        try {
            if (d != null && d.length() >= 10) {
                String nd = d.substring(0, 10);
                JSONObject st = Store.loadState(ctx);
                Engine.ensureState(st);
                String old = st.optString("exam", "");
                st.put("exam", nd);
                // 只有初试日期**真的变了**才重排今日计划。
                // 原来无条件把 plan / plan_pos 清零，导致在设置页点一下
                // 「保存并开始」（哪怕日期没动）就会把当天进度打回重来。
                if (!nd.equals(old)) {
                    st.put("plan", new JSONArray());
                    st.put("plan_pos", 0);
                    st.put("day", JSONObject.NULL);
                }
                Store.saveState(ctx, st);
            }
            HtmlView.FORCE_SETUP = false;
        } catch (Throwable e) { }
    }

    static class PushTask implements Runnable {
        Activity a;
        PushTask(Activity a) { this.a = a; }
        public void run() { doPush(a); ((MainActivity) a).load(); }
    }

    static class LoadTask implements Runnable {
        Activity a;
        LoadTask(Activity a) { this.a = a; }
        public void run() { ((MainActivity) a).load(); }
    }

    static class ExamTask implements Runnable {
        Activity a; String d;
        ExamTask(Activity a, String d) { this.a = a; this.d = d; }
        public void run() { doSetExam(a, d); ((MainActivity) a).load(); }
    }

    static class PickTask implements Runnable {
        Activity a;
        PickTask(Activity a) { this.a = a; }
        public void run() { ((MainActivity) a).pickWordFile(); }
    }

    static class ResetTask implements Runnable {
        Activity a;
        ResetTask(Activity a) { this.a = a; }
        public void run() {
            try { File f = Words.customFile(a); if (f.exists()) f.delete(); } catch (Throwable e) { }
            ((MainActivity) a).load();
        }
    }

    static class SetupTask implements Runnable {
        Activity a;
        SetupTask(Activity a) { this.a = a; }
        public void run() { HtmlView.FORCE_SETUP = true; ((MainActivity) a).load(); }
    }

    /** 保存每日推送时段，并按新时段重排今天剩余批次。 */
    static void doSetPushTime(Context ctx, String s, String e) {
        try {
            JSONObject st = Store.loadState(ctx);
            Engine.ensureState(st);
            int sm = Engine.parseHM(s, Engine.DEF_START_MIN);
            int em = Engine.parseHM(e, Engine.DEF_END_MIN);
            if (em <= sm) em = sm + 60;
            st.put("push_start", Engine.fmtHM(sm));
            st.put("push_end", Engine.fmtHM(em));
            Store.saveState(ctx, st);
            Scheduler.schedule(ctx);
        } catch (Throwable t) { }
    }

    static class PushTimeTask implements Runnable {
        Activity a; String s, e;
        PushTimeTask(Activity a, String s, String e) { this.a = a; this.s = s; this.e = e; }
        public void run() { doSetPushTime(a, s, e); ((MainActivity) a).load(); }
    }

    /** 开关学习弹幕；开启时若已有悬浮窗权限则直接启动服务。 */
    static void doToggleDanmaku(Context ctx) {
        try {
            JSONObject st = Store.loadState(ctx);
            Engine.ensureState(st);
            boolean on = !"1".equals(st.optString("danmaku_on", "0"));
            st.put("danmaku_on", on ? "1" : "0");
            Store.saveState(ctx, st);
            // 服务始终在跑（推送调度需要它），开关只决定弹幕画不画；
            // sync 会顺带刷新常驻通知文案，并让弹幕层尽快重读设置
            BotService.sync(ctx);
        } catch (Throwable t) { }
    }

    /** 保存弹幕设置：频率（个/秒，亮屏/熄屏独立）与样式（字号/颜色/字体/加粗）。 */
    static void doSetDanmakuCfg(Context ctx, String onS, String offS,
                                String sizeS, String color, String font, String bold) {
        try {
            JSONObject st = Store.loadState(ctx);
            Engine.ensureState(st);
            st.put("danmaku_rate_on", parseRate(onS, 0.02));
            st.put("danmaku_rate_off", parseRate(offS, 0.0));
            st.put("danmaku_size", parseSize(sizeS, 18));
            st.put("danmaku_color", normColor(color));
            st.put("danmaku_font", ("serif".equals(font) || "mono".equals(font)) ? font : "sans");
            st.put("danmaku_bold", "0".equals(bold) ? "0" : "1");
            Store.saveState(ctx, st);
            BotService.sync(ctx);
        } catch (Throwable t) { }
    }

    /** 解析字号（sp）：8–80。 */
    static int parseSize(String s, int def) {
        try {
            int v = (int) Math.round(Double.parseDouble(s.trim()));
            if (v < 8) v = 8;
            if (v > 80) v = 80;
            return v;
        } catch (Exception e) { return def; }
    }

    /** 规范化颜色为 "#RRGGBB"，非法取白色。 */
    static String normColor(String s) {
        try {
            String t = s == null ? "" : s.trim();
            if (!t.startsWith("#")) t = "#" + t;
            if (t.length() == 4) {
                char r = t.charAt(1), g = t.charAt(2), b = t.charAt(3);
                t = "#" + r + r + g + g + b + b;
            }
            if (!t.matches("#[0-9a-fA-F]{6}")) return "#FFFFFF";
            return t.toUpperCase();
        } catch (Exception e) { return "#FFFFFF"; }
    }

    /** 解析频率：单位「个/秒」，范围 0–2，非法则取默认值。 */
    static double parseRate(String s, double def) {
        try {
            double v = Double.parseDouble(s.trim());
            if (v < 0) v = 0;
            if (v > 2) v = 2;
            return v;
        } catch (Exception e) { return def; }
    }

    static class DanmakuToggleTask implements Runnable {
        Activity a;
        DanmakuToggleTask(Activity a) { this.a = a; }
        public void run() { doToggleDanmaku(a); ((MainActivity) a).load(); }
    }

    static class OverlayTask implements Runnable {
        Activity a;
        OverlayTask(Activity a) { this.a = a; }
        public void run() { Perms.requestOverlay(a); }
    }

    static class DanmakuCfgTask implements Runnable {
        Activity a; String on, off, size, color, font, bold;
        DanmakuCfgTask(Activity a, String on, String off, String size, String color, String font, String bold) {
            this.a = a; this.on = on; this.off = off; this.size = size;
            this.color = color; this.font = font; this.bold = bold;
        }
        public void run() { doSetDanmakuCfg(a, on, off, size, color, font, bold); ((MainActivity) a).load(); }
    }
}