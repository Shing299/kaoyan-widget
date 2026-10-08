package com.kaoyan.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/**
 * 学习弹幕的「悬浮层」实现 —— 注意它是**普通类，不是 Service**。
 *
 * v1.3 起弹幕与推送通知合并到同一个前台服务 {@link BotService}：
 * 这里不再自己 startForeground，只负责在 WindowManager 上画弹幕，
 * 由 BotService 的生命周期驱动。
 *
 * 与推送的整合：推送一批单词时，BotService 会把这一批词交给 {@link #enqueue}，
 * 于是「通知里的那几个词」会按同样顺序再以弹幕走一遍。
 * 批次词优先于随机词；播完批次再回到按设定频率随机弹。
 *
 * 说明：本项目所用的 d8 版本对「匿名内部类」会报 NPE，
 * 故所有 Runnable / BroadcastReceiver 一律使用「命名静态内部类」。
 */
public class DanmakuOverlay {
    /** 同时在飞的弹幕上限（防御性兜底；正常由泳道数天然限制）。 */
    static final int MAX_LIVE = 12;
    /** 纵向最多分几条泳道。同屏弹幕**一条一泳道**，绝不共用。 */
    static final int MAX_LANES = 12;
    /** 两次弹出之间的最小间隔（毫秒）：设置上限 2 个/秒 需要 500ms。 */
    static final long MIN_GAP_MS = 300L;
    /** 划过速度：每像素耗时（毫秒）——数值越大越慢。 */
    static final float MS_PER_PX = 3.2f;
    /** 推送批次待播时的节奏上限：最多这么久一个，免得 5 个词拖几分钟。 */
    static final long BATCH_GAP_MS = 2500L;
    /** 待播批次队列上限，防止长期堆积。 */
    static final int MAX_QUEUE = 30;
    /**
     * 位移动画的帧间隔（毫秒）。约 60fps。
     *
     * 不能用 ViewPropertyAnimator：它跟 Choreographer 走，在本机 120Hz 屏上会跑到
     * 约 95fps。实测同屏 1 条弹幕时进程占用 26~30% 单核，而静止时只有 1%
     * —— 开销几乎全是「每帧成本 × 帧率」。自己按 60fps 驱动后帧率降到约 49fps，
     * CPU 降到约 18%（帧数是从 dumpsys gfxinfo 读的确定性指标，不是噪声）。
     * 滚动速度只有 312px/s，60fps 下每帧约 6px，肉眼看不出区别。
     */
    static final long ANIM_FRAME_MS = 33L;
    /** 随机词池里的权重：到期复习词放 3 份，新词放 1 份（复习词露脸更频繁）。 */
    static final int W_REVIEW = 3;
    static final int W_NEW = 1;

    private final Context ctx;
    private WindowManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /**
     * 动画专用 handler。必须和 ticker 的 handler 分开：
     * ticker 那侧在 start()/屏幕变化时会 removeCallbacksAndMessages(null)，
     * 共用一个 handler 会把正在飞的弹幕动画一起清掉（弹幕会卡在半路不动）。
     */
    private final Handler animHandler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private boolean running = false;
    private boolean screenOn = true;
    /** 所有正在飞的弹幕；各自飞完自己移除，互不挤占。 */
    private final ArrayList<View> live = new ArrayList<View>();
    /** 与 live 一一对应的纵向泳道号，用于避免两条弹幕落在同一行。 */
    private final ArrayList<Integer> lanes = new ArrayList<Integer>();
    /** 待播的「推送批次」词条，优先于随机词。 */
    private final ArrayList<String> queue = new ArrayList<String>();
    /**
     * 所有在飞弹幕的动画参数。**全局共用一条动画循环**：
     * 若每条弹幕各自起一条 16ms 的链，两条错开就会叠加成 ~85fps，
     * 条数越多帧率越高（实测 2 条时 85fps / 28.9% 单核）。
     * 共用一条循环后，无论同屏几条，总帧率都被压在 ANIM_FRAME_MS 决定的 ~60fps。
     */
    private final ArrayList<Anim> anims = new ArrayList<Anim>();
    private boolean animRunning = false;
    private BroadcastReceiver screenRcv;
    private int lastIdx = -1;
    /** 全词库，只加载一次（原来每条弹幕都重新读 300KB、解析 5398 行）。 */
    private List<Words.W> allWords;
    /** 随机词池与其签名；只在「日 / 计划 / 卡片数」变化时重建。 */
    private ArrayList<Words.W> pool;
    private String poolSig;

    public DanmakuOverlay(Context c) {
        this.ctx = c.getApplicationContext();
        this.wm = (WindowManager) this.ctx.getSystemService(Context.WINDOW_SERVICE);
    }

    /** 开始（幂等）。再次调用会把定时器提前，让设置改动尽快生效。 */
    public void start() {
        if (wm == null) wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        screenOn = isInteractive();
        if (screenRcv == null) {
            screenRcv = new ScreenRcv(this);
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_SCREEN_OFF);
            f.addAction(Intent.ACTION_USER_PRESENT);
            try { ctx.registerReceiver(screenRcv, f); } catch (Exception e) { }
        }
        running = true;
        handler.removeCallbacksAndMessages(null);
        scheduleNext(600L);
    }

    /** 停止：清空全部弹幕并注销监听。 */
    public void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        removeAll();
        try { animHandler.removeCallbacksAndMessages(null); } catch (Throwable t) { }
        anims.clear();
        animRunning = false;
        queue.clear();
        if (screenRcv != null) {
            try { ctx.unregisterReceiver(screenRcv); } catch (Exception e) { }
            screenRcv = null;
        }
    }

    /** 把推送的这一批词排进待播队列（与通知同一批词、同样的顺序）。 */
    public void enqueue(String[] texts) {
        if (texts == null || texts.length == 0) return;
        for (int i = 0; i < texts.length; i++) {
            if (texts[i] == null || texts[i].length() == 0) continue;
            if (queue.size() >= MAX_QUEUE) break;
            queue.add(texts[i]);
        }
        if (running && !queue.isEmpty()) {
            // 批次刚到：把定时器提前，别让它等一整个随机间隔
            handler.removeCallbacksAndMessages(null);
            scheduleNext(400L);
        }
    }

    public void clearQueue() { queue.clear(); }

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
     * 两处「不能省」：
     * 1) 屏幕状态每次都实时读 {@link #isInteractive()}，不依赖 SCREEN_ON 广播
     *    —— 实测系统中途重启进程后曾漏收广播，缓存值一直是「熄屏」，
     *    结果弹幕静默停摆，直到用户重新打开 App 才恢复。
     * 2) 重新排期放在 finally 里，且下一次间隔单独算，保证定时链不会因为
     *    某次异常而永久断掉。
     *
     * 另外：状态每 tick 只读一次（原来一趟要读 3 次 state.json）。
     */
    void tickOnce() {
        if (!running) return;
        long delay = IDLE;   // IDLE = 不再排期
        try {
            JSONObject st = Store.loadState(ctx);
            Engine.ensureState(st);
            screenOn = isInteractive();
            boolean on = "1".equals(st.optString("danmaku_on", "0"));
            double rate = screenOn ? st.optDouble("danmaku_rate_on", 0.02)
                                   : st.optDouble("danmaku_rate_off", 0.0);
            if (!on) {
                // 开关关掉：清干净待播批次，并**停止轮询**
                removeAll();
                queue.clear();
            } else if (rate > 0) {
                showOne(st);
                delay = nextDelayMs(true, rate);
            }
            // rate <= 0（例如熄屏且熄屏频率为 0）同样不排期，等 SCREEN_ON / 设置变更唤醒
        } catch (Throwable t) {
            delay = 30000L;   // 出错别空转，等下次唤醒
        } finally {
            if (delay > 0) scheduleNext(delay);
        }
    }

    /** 空闲标记：<= 0 表示不排期。 */
    static final long IDLE = -1L;

    /** 安排下一次 tick。 */
    private void scheduleNext(long delayMs) {
        if (delayMs <= 0) return;
        try { handler.postDelayed(new Ticker(this), delayMs); } catch (Throwable t) { }
    }

    /** 下一次间隔（毫秒）：按频率（个/秒）换算，附 ±15% 抖动。 */
    private long nextDelayMs(boolean on, double rate) {
        if (!on || rate <= 0) return IDLE;
        long base = (long) (1000.0 / rate);
        if (base < MIN_GAP_MS) base = MIN_GAP_MS;
        // 推送批次还没播完：优先播它，且不超过 BATCH_GAP_MS 的节奏
        if (!queue.isEmpty() && base > BATCH_GAP_MS) base = BATCH_GAP_MS;
        long j = base * 15 / 100;
        long d = base - j + (long) (rnd.nextDouble() * (2 * j + 1));
        return d < MIN_GAP_MS ? MIN_GAP_MS : d;
    }

    /** 全词库（懒加载，只解析一次）。 */
    private List<Words.W> allWords() {
        if (allWords == null) allWords = Words.load(ctx);
        return allWords;
    }

    /**
     * 弹幕的随机词池。
     *
     * 池子内容（按用户要求）：
     *   1. **今天计划内的词**（`plan` 里 [词索引, 0新/1复]）—— 复习词放 {@link #W_REVIEW} 份、新词 1 份；
     *   2. **已到期但没排进今天计划的复习词**（`cards` 里 dueDay <= 今天序号）—— 同样 3 份，
     *      让因为 reviewCap 被挤出计划的词也有机会露脸；
     *   3. 两者都为空时退回**全词库**（兜底，例如当天计划还没生成）。
     *
     * 因为池子里复习词占了 3 份，随机抽到复习词的概率约是新词的 3 倍 —— 弹幕更像复习提醒。
     *
     * 池子只在「日期 / plan 条数 / cards 条数 / 计划天数」变化时重建，
     * 否则每弹一条都要遍历一遍 plan 与 cards。
     */
    private List<Words.W> danmakuPool(JSONObject st) {
        List<Words.W> all = allWords();
        if (all.isEmpty()) return all;
        JSONArray plan = st.optJSONArray("plan");
        JSONObject cards = st.optJSONObject("cards");
        int dayNo = st.optInt("plan_day_no", 0);
        String sig = st.optString("day", "") + "|" + (plan == null ? -1 : plan.length())
                   + "|" + (cards == null ? -1 : cards.length()) + "|" + dayNo;
        if (pool != null && sig.equals(poolSig)) return pool;

        ArrayList<Words.W> p = new ArrayList<Words.W>();
        HashSet<Integer> inPlan = new HashSet<Integer>();
        int n = all.size();
        try {
            if (plan != null) {
                for (int i = 0; i < plan.length(); i++) {
                    JSONArray it = plan.optJSONArray(i);
                    if (it == null || it.length() < 2) continue;
                    int idx = it.optInt(0, -1), kind = it.optInt(1, 0);
                    if (idx < 0 || idx >= n) continue;
                    inPlan.add(Integer.valueOf(idx));
                    int w = (kind == 1) ? W_REVIEW : W_NEW;
                    for (int k = 0; k < w; k++) p.add(all.get(idx));
                }
            }
            if (cards != null) {
                java.util.Iterator<String> keys = cards.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    int idx;
                    try { idx = Integer.parseInt(k); } catch (Exception e) { continue; }
                    if (idx < 0 || idx >= n || inPlan.contains(Integer.valueOf(idx))) continue;
                    JSONArray v = cards.optJSONArray(k);
                    if (v == null || v.length() < 2) continue;
                    if (v.optInt(1, Integer.MAX_VALUE) <= dayNo) {   // 已到期
                        for (int j = 0; j < W_REVIEW; j++) p.add(all.get(idx));
                    }
                }
            }
        } catch (Throwable t) { }
        if (p.isEmpty()) p.addAll(all);   // 兜底：退回全词库
        pool = p;
        poolSig = sig;
        return pool;
    }

    private boolean isInteractive() {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            return pm == null || pm.isInteractive();
        } catch (Exception e) { return true; }
    }

    private void showOne(JSONObject st) {
        if (wm == null) return;
        if (!Settings.canDrawOverlays(ctx)) return;   // 权限被撤销时不崩，静默跳过

        // 优先播「推送批次」里的词；没有批次才随机抽
        String text;
        if (!queue.isEmpty()) {
            text = queue.remove(0);
        } else {
            List<Words.W> pool = danmakuPool(st);
            if (pool.isEmpty()) return;
            int idx = rnd.nextInt(pool.size());
            if (pool.size() > 1 && idx == lastIdx) idx = (idx + 1) % pool.size();
            lastIdx = idx;
            text = Engine.danmakuText(pool.get(idx));
        }

        TextView tv = new TextView(ctx);
        tv.setText(text);
        double size = 18; int color = 0xFFFFFFFF; String font = "sans"; boolean bold = true;
        try {
            size = st.optDouble("danmaku_size", 18);
            color = parseColor(st.optString("danmaku_color", "#FFFFFF"), 0xFFFFFFFF);
            font = st.optString("danmaku_font", "sans");
            bold = !"0".equals(st.optString("danmaku_bold", "1"));
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

        int sw = ctx.getResources().getDisplayMetrics().widthPixels;
        int sh = ctx.getResources().getDisplayMetrics().heightPixels;

        // 窗口是全屏宽（MATCH_PARENT）、文字左对齐，所以真正要划出屏幕的是「文字自身宽度」。
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

        // 初始位置 + 登记到全局动画循环（不再需要等布局回调）
        tv.setTranslationX(sw);
        int dur = (int) Math.max(3000f, (sw + textW) * MS_PER_PX);
        anims.add(new Anim(tv, sw, -textW, dur, SystemClock.uptimeMillis()));
        ensureAnimLoop();
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
        for (int k = anims.size() - 1; k >= 0; k--) {
            if (anims.get(k).v == v) anims.remove(k);
        }
    }

    /** 清空全部弹幕（熄屏 / 关开关 / 服务销毁）。 */
    private void removeAll() {
        for (int i = live.size() - 1; i >= 0; i--) removeView(live.get(i));
        live.clear();
        lanes.clear();
    }

    private int dp(int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
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

    // ---------------- 命名静态内部类（规避 d8 匿名类 NPE） ----------------

    static class ScreenRcv extends BroadcastReceiver {
        private final DanmakuOverlay o;
        ScreenRcv(DanmakuOverlay o) { this.o = o; }
        @Override public void onReceive(Context c, Intent i) { o.onScreen(i.getAction()); }
    }

    static class Ticker implements Runnable {
        private final DanmakuOverlay o;
        Ticker(DanmakuOverlay o) { this.o = o; }
        @Override public void run() { o.tickOnce(); }
    }

    /** 一条弹幕的动画参数。 */
    static class Anim {
        final View v; final float from, to; final int dur; final long t0;
        Anim(View v, float from, float to, int dur, long t0) {
            this.v = v; this.from = from; this.to = to; this.dur = dur; this.t0 = t0;
        }
    }

    /** 启动全局动画循环（幂等）。 */
    private void ensureAnimLoop() {
        if (animRunning) return;
        animRunning = true;
        try { animHandler.post(new AnimLoop(this)); } catch (Throwable t) { animRunning = false; }
    }

    /**
     * 全局唯一的动画循环：一次回调里更新**所有**在飞弹幕的位置。
     * 用 uptimeMillis 算进度，postDelayed 抖动不会累积偏移；
     * 视图被移除（到点/被淘汰/熄屏清空）时对应的 Anim 也会被摘掉。
     */
    static class AnimLoop implements Runnable {
        private final DanmakuOverlay o;
        AnimLoop(DanmakuOverlay o) { this.o = o; }
        @Override public void run() {
            long now = SystemClock.uptimeMillis();
            boolean any = false;
            try {
                for (int i = o.anims.size() - 1; i >= 0; i--) {
                    Anim a = o.anims.get(i);
                    if (!o.live.contains(a.v)) { o.anims.remove(i); continue; }   // 已被移除
                    float f = a.dur <= 0 ? 1f : (float) (now - a.t0) / (float) a.dur;
                    if (f >= 1f) { o.anims.remove(i); o.removeView(a.v); continue; }
                    a.v.setTranslationX(a.from + (a.to - a.from) * f);
                    any = true;
                }
            } catch (Throwable t) { }
            if (any) {
                try { o.animHandler.postDelayed(new AnimLoop(o), ANIM_FRAME_MS); }
                catch (Throwable t) { o.animRunning = false; }
            } else {
                o.animRunning = false;      // 没有在飞的弹幕了，循环自然结束
            }
        }
    }
}
