package com.kaoyan.widget;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 学习弹幕：屏幕点亮且正在使用手机时，不时从屏幕上划过一条
 * 「单词 + 释义」悬浮弹幕；熄屏时不弹出。
 * 需要「悬浮窗」权限（SYSTEM_ALERT_WINDOW）。
 *
 * 注意：弹幕**不受「推送时段」限制**（开关打开即全天生效）。
 * 推送时段只管通知批次；早期版本用推送时段去闸弹幕，
 * 结果用户设好频率、时段一过却什么都不弹，且界面上毫无提示。
 *
 * v1.2 修复：多条弹幕可以同时存在、各自飞完自己移除
 * （旧实现只有一条，新弹幕一来就把上一条删掉，看起来像「刚动一点就飞走」）。
 *
 * 说明：本项目所用的 d8 版本对「匿名内部类」会报 NPE，故所有 Runnable /
 * BroadcastReceiver 一律使用「命名静态内部类」实现。
 */
public class DanmakuService extends Service {
    static final int NOTI_ID = 1002;
    public static final String ACTION_STOP = "com.kaoyan.widget.DANMAKU_STOP";

    /** 同时在飞的弹幕上限（防御性兜底；正常由泳道数天然限制）。 */
    static final int MAX_LIVE = 12;
    /**
     * 纵向最多分几条「泳道」。同屏弹幕**一条一泳道**，绝不共用。
     * 取 12 是为了让设置里的上限「2 个/秒」也能铺开：2 个/秒 × 约 4.2s 行程 ≈ 8.4 条并发。
     */
    static final int MAX_LANES = 12;
    /** 两次弹出之间的最小间隔（毫秒）：设置里的上限 2 个/秒 需要 500ms。 */
    static final long MIN_GAP_MS = 300L;
    /** 划过速度：每像素耗时（毫秒）——数值越大越慢。 */
    static final float MS_PER_PX = 3.2f;

    private WindowManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private boolean running = false;
    private boolean screenOn = true;
    /** 所有正在飞的弹幕；各自飞完自己移除，互不挤占。 */
    private final ArrayList<View> live = new ArrayList<View>();
    /** 与 live 一一对应的纵向泳道号，用于避免两条弹幕落在同一行。 */
    private final ArrayList<Integer> lanes = new ArrayList<Integer>();
    private BroadcastReceiver screenRcv;
    private int lastIdx = -1;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTI_ID, Notifier.buildDanmaku(this),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTI_ID, Notifier.buildDanmaku(this),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTI_ID, Notifier.buildDanmaku(this));
            }
        } catch (Exception e) { }

        if (!canOverlay()) { stopSelf(); return START_NOT_STICKY; }

        if (wm == null) wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        screenOn = isInteractive();
        if (screenRcv == null) {
            screenRcv = new ScreenRcv(this);
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_SCREEN_OFF);
            f.addAction(Intent.ACTION_USER_PRESENT);
            try { registerReceiver(screenRcv, f); } catch (Exception e) { }
        }
        running = true;
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Ticker(this), 2000);
        return START_STICKY;
    }

    /** 屏幕状态回调：熄屏时清空全部弹幕；亮屏时立刻重新武装定时器。 */
    void onScreen(String a) {
        if (Intent.ACTION_SCREEN_OFF.equals(a)) { screenOn = false; removeAll(); }
        else if (Intent.ACTION_SCREEN_ON.equals(a) || Intent.ACTION_USER_PRESENT.equals(a)) {
            screenOn = true;
            if (running) { handler.removeCallbacksAndMessages(null); scheduleNext(600L); }
        }
    }

    /**
     * 定时器：到点判断是否弹出，并安排下一次。
     *
     * 注意两处「不能省」：
     * 1) 屏幕状态每次都实时读 {@link #isInteractive()}，不依赖 SCREEN_ON 广播
     *    —— 实测系统中途重启进程后曾漏收广播，缓存值一直是「熄屏」，
     *    结果弹幕静默停摆，直到用户重新打开 App 才恢复。
     * 2) 重新排期放在 finally 里，且下一次间隔单独算，保证定时链不会因为
     *    某次异常而永久断掉。
     */
    void tickOnce() {
        if (!running) return;
        try {
            screenOn = isInteractive();
            // 只看「开关 + 亮屏/熄屏频率」，不再用推送时段卡弹幕
            if (isOn() && currentRate() > 0) showOne();
        } catch (Throwable t) {
        } finally {
            scheduleNext(0L);
        }
    }

    /** 安排下一次 tick；delayMs > 0 时用指定间隔，否则按当前频率计算。 */
    private void scheduleNext(long delayMs) {
        long d = delayMs;
        if (d <= 0) {
            d = 5000L;
            try { d = nextDelayMs(); } catch (Throwable t) { }
        }
        try { handler.postDelayed(new Ticker(this), d); } catch (Throwable t) { }
    }

    /** 当前适用的弹出频率（个/秒）：亮屏与熄屏分别独立设置。 */
    private double currentRate() {
        try {
            JSONObject st = Store.loadState(this);
            return screenOn ? st.optDouble("danmaku_rate_on", 0.02)
                            : st.optDouble("danmaku_rate_off", 0.0);
        } catch (Exception e) { return screenOn ? 0.02 : 0.0; }
    }

    /** 下一次间隔（毫秒）：按当前频率（个/秒）换算，附 ±15% 抖动。 */
    private long nextDelayMs() {
        double rate = currentRate();
        if (rate <= 0) return 5000L;   // 该状态下不弹，但仍保持轮询，便于状态切换后立即生效
        long base = (long) (1000.0 / rate);
        if (base < MIN_GAP_MS) base = MIN_GAP_MS;
        long j = base * 15 / 100;
        long d = base - j + (long) (rnd.nextDouble() * (2 * j + 1));
        return d < MIN_GAP_MS ? MIN_GAP_MS : d;
    }

    private boolean isOn() {
        try { return "1".equals(Store.loadState(this).optString("danmaku_on", "0")); }
        catch (Exception e) { return false; }
    }

    private boolean isInteractive() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm == null || pm.isInteractive();
        } catch (Exception e) { return true; }
    }

    private void showOne() {
        if (wm == null) return;
        List<Words.W> ws = Words.load(this);
        if (ws.isEmpty()) return;
        int idx = rnd.nextInt(ws.size());
        if (ws.size() > 1 && idx == lastIdx) idx = (idx + 1) % ws.size();
        lastIdx = idx;
        Words.W w = ws.get(idx);
        String text = w.word + (w.ph.length() > 0 ? " [" + w.ph + "]" : "") + "  " + w.mean;

        TextView tv = new TextView(this);
        tv.setText(text);
        double size = 18; int color = 0xFFFFFFFF; String font = "sans"; boolean bold = true;
        try {
            JSONObject ds = Store.loadState(this);
            size = ds.optDouble("danmaku_size", 18);
            color = parseColor(ds.optString("danmaku_color", "#FFFFFF"), 0xFFFFFFFF);
            font = ds.optString("danmaku_font", "sans");
            bold = !"0".equals(ds.optString("danmaku_bold", "1"));
        } catch (Exception e) { }
        tv.setTextSize((float) size);
        tv.setTextColor(color);
        android.graphics.Typeface tf = android.graphics.Typeface.SANS_SERIF;
        if ("serif".equals(font)) tf = android.graphics.Typeface.SERIF;
        else if ("mono".equals(font)) tf = android.graphics.Typeface.MONOSPACE;
        tv.setTypeface(tf, bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        tv.setShadowLayer(dp(2), 0, 0, shadowFor(color));   // 透明底：用阴影描边保证可读
        int padH = dp(2), padV = dp(2);
        tv.setPadding(padH, padV, padH, padV);
        tv.setSingleLine(true);   // 单行 + 横向滚动：超长文本不会被省略号截断，而是整条划过去

        int sw = getResources().getDisplayMetrics().widthPixels;
        int sh = getResources().getDisplayMetrics().heightPixels;

        // 窗口是全屏宽（MATCH_PARENT）、文字左对齐，所以真正要划出屏幕的是「文字自身宽度」。
        // 旧实现用 tv.getWidth()（= 屏宽）当作文字宽度，导致文字早已滑出屏幕、动画还在空跑。
        int textW = (int) Math.ceil(tv.getPaint().measureText(text)) + padH * 2 + dp(6);
        android.graphics.Paint.FontMetricsInt fm = tv.getPaint().getFontMetricsInt();
        int textH = (fm.descent - fm.ascent) + padV * 2;

        int laneTotal = laneCount(sh, textH);
        int lane = freeLane(laneTotal);
        // 没有空泳道就放弃这一条：宁可少弹，也不要两条叠在同一行
        //（叠字会造成「单词频闪」，比少弹难看得多）
        if (lane < 0) return;

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.y = laneY(lane, sh, textH, laneTotal);

        // 防御性兜底：泳道数已经天然限制了并发，正常不会走到这里。
        // 注意不要「新的一条来了就把还在半路的一条删掉」——那正是「刚动一点就飞走」的老毛病。
        while (live.size() >= MAX_LIVE) removeView(live.get(0));
        try {
            wm.addView(tv, lp);
        } catch (Exception e) { return; }
        live.add(tv);
        lanes.add(Integer.valueOf(lane));

        tv.post(new PostStart(this, tv, sw, textW));
    }

    /** 可用泳道数量：随字号自适应，最多 MAX_LANES 条。 */
    private int laneCount(int sh, int textH) {
        int band = (int) (sh * 0.62);
        int laneH = Math.max(textH + dp(8), dp(24));
        int n = band / laneH;
        if (n < 1) n = 1;
        if (n > MAX_LANES) n = MAX_LANES;
        return n;
    }

    /** 泳道对应的窗口 y 坐标（让文字在泳道内垂直居中）。 */
    private int laneY(int lane, int sh, int textH, int laneTotal) {
        int top = (int) (sh * 0.12);
        int band = (int) (sh * 0.62);
        int laneH = band / laneTotal;
        int y = top + lane * laneH + Math.max(0, (laneH - textH) / 2);
        if (y < 0) y = 0;
        return y;
    }

    /**
     * 挑一条当前**没被占用**的泳道；一条空位都没有时返回 -1。
     * 绝不复用已占用的泳道：两条同泳道弹幕同速平移，文字会一直叠在一起，
     * 视觉上就是「单词频闪」。
     */
    private int freeLane(int laneTotal) {
        ArrayList<Integer> free = new ArrayList<Integer>();
        for (int i = 0; i < laneTotal; i++) {
            if (!lanes.contains(Integer.valueOf(i))) free.add(Integer.valueOf(i));
        }
        if (free.isEmpty()) return -1;
        return free.get(rnd.nextInt(free.size())).intValue();
    }

    /**
     * 移除单个弹幕（并同步清掉它的泳道占用）。
     * 顺序要紧：**先**从 WindowManager 摘掉窗口，**再**把泳道标记为空闲。
     * 反过来的话会有一瞬间「泳道显示空闲、旧窗口还没消失」，
     * 新弹幕正好挑中这条泳道就出现同泳道叠字。
     */
    private void removeView(View v) {
        if (v == null) return;
        try { v.animate().cancel(); } catch (Throwable t) { }
        if (wm != null) { try { wm.removeView(v); } catch (Exception e) { } }
        int i = live.indexOf(v);
        if (i >= 0) {
            live.remove(i);
            if (i < lanes.size()) lanes.remove(i);
        }
    }

    /** 清空全部弹幕（熄屏 / 服务销毁）。 */
    private void removeAll() {
        for (int i = live.size() - 1; i >= 0; i--) removeView(live.get(i));
        live.clear();
        lanes.clear();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 把 "#RRGGBB" / "#AARRGGBB" 解析为颜色；失败取默认。 */
    static int parseColor(String s, int def) {
        try {
            String t = s == null ? "" : s.trim();
            if (t.startsWith("#")) t = t.substring(1);
            if (t.length() == 6) t = "FF" + t;
            if (t.length() != 8) return def;
            return (int) Long.parseLong(t, 16);
        } catch (Exception e) { return def; }
    }

    /** 依据文字明暗自动选描边色：深色字用白描边，浅色字用黑描边。 */
    static int shadowFor(int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        double lum = 0.299 * r + 0.587 * g + 0.114 * b;
        return lum > 140 ? 0xF0000000 : 0xF0FFFFFF;
    }

    private boolean canOverlay() {
        try { return Settings.canDrawOverlays(this); } catch (Exception e) { return false; }
    }

    /**
     * 按 state.json 里的开关重新对齐弹幕服务。
     * 供 BootReceiver / BatchReceiver 当「心跳」调用：系统杀进程、冻结后台之后，
     * 下一次闹钟就会把弹幕服务重新拉起来，不必等用户再打开 App。
     */
    public static void syncFromState(Context ctx) {
        try { sync(ctx, "1".equals(Store.loadState(ctx).optString("danmaku_on", "0"))); }
        catch (Exception e) { }
    }

    /** 统一入口：根据开关与权限启动/停止弹幕服务。 */
    public static void sync(Context ctx, boolean on) {
        try {
            Intent i = new Intent(ctx, DanmakuService.class);
            if (on && Settings.canDrawOverlays(ctx)) ctx.startForegroundService(i);
            else ctx.stopService(i);
        } catch (Exception e) { }
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        removeAll();
        if (screenRcv != null) {
            try { unregisterReceiver(screenRcv); } catch (Exception e) { }
            screenRcv = null;
        }
        super.onDestroy();
    }

    // ---------------- 命名静态内部类（规避 d8 匿名类 NPE） ----------------

    static class ScreenRcv extends BroadcastReceiver {
        private final DanmakuService s;
        ScreenRcv(DanmakuService s) { this.s = s; }
        @Override public void onReceive(Context c, Intent i) { s.onScreen(i.getAction()); }
    }

    static class Ticker implements Runnable {
        private final DanmakuService s;
        Ticker(DanmakuService s) { this.s = s; }
        @Override public void run() { s.tickOnce(); }
    }

    static class PostStart implements Runnable {
        private final DanmakuService s;
        private final View tv;
        private final int sw;
        private final int textW;
        PostStart(DanmakuService s, View tv, int sw, int textW) {
            this.s = s; this.tv = tv; this.sw = sw; this.textW = textW;
        }
        @Override public void run() {
            try {
                int total = sw + textW;            // 从屏幕右侧外 → 文字完全移出左侧
                int dur = (int) Math.max(3000f, total * MS_PER_PX);
                tv.setTranslationX(sw);
                tv.animate().translationX(-textW).setDuration(dur)
                  .withEndAction(new EndAction(s, tv)).start();
            } catch (Throwable t) { }
        }
    }

    static class EndAction implements Runnable {
        private final DanmakuService s;
        private final View tv;
        EndAction(DanmakuService s, View tv) { this.s = s; this.tv = tv; }
        @Override public void run() { s.removeView(tv); }
    }
}
