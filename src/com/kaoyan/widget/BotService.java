package com.kaoyan.widget;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * 唯一的常驻前台服务：**定时推送单词 + 学习弹幕**。
 *
 * v1.3 起把原来的 BotService（推送调度）与 DanmakuService（弹幕悬浮层）合并成一个：
 *  - 通知栏只留**一条**常驻通知（原来「推送运行中」+「学习弹幕已开启」两条）；
 *  - 只有一条 START_STICKY 服务需要保活，少一份内存与唤醒开销；
 *  - 弹幕开关只控制「画不画」，不再单独起停服务
 *    （推送调度始终需要这个服务，所以它本来就该一直活着）。
 *
 * 与推送的整合（一次推送、两种呈现）：闹钟到点推送一批单词时，
 * {@link BatchReceiver} 会把这一批词通过 {@link #ACTION_SHOW_BATCH} 交给本服务，
 * 由 {@link DanmakuOverlay} 按同样顺序以弹幕再走一遍 —— 通知里看到的就是弹幕里看到的。
 */
public class BotService extends Service {
    static final int NOTI_ID = 1000;
    /** 把一批词交给弹幕（推送后调用）。 */
    public static final String ACTION_SHOW_BATCH = "com.kaoyan.widget.SHOW_BATCH";
    public static final String EXTRA_LINES = "lines";

    private DanmakuOverlay overlay;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTI_ID, Notifier.buildService(this),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        | ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTI_ID, Notifier.buildService(this),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTI_ID, Notifier.buildService(this));
            }
        } catch (Exception e) { }

        // 弹幕悬浮层：start() 是幂等的，每次进来都把定时器提前，
        // 好让刚改的设置（开关/频率/样式）尽快生效
        if (overlay == null) overlay = new DanmakuOverlay(this);
        overlay.start();

        boolean showBatch = intent != null && ACTION_SHOW_BATCH.equals(intent.getAction());
        if (showBatch) {
            overlay.enqueue(intent.getStringArrayExtra(EXTRA_LINES));
        }

        // 只有「确保服务在跑」的那次启动才重排闹钟。
        // 每推一批都会 startService 一次，若那时也重排，就是每分钟把 20 多个
        // 精确闹钟全部 cancel + 重新 set 一遍（纯属白耗，还会把当天剩余时间重新随机）。
        // 已排好的闹钟本来就是「醒来推下一批」，不依赖当前 plan_pos，所以不重排也是对的。
        if (!showBatch) {
            try { Scheduler.schedule(this); } catch (Exception e) { }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (overlay != null) { overlay.stop(); overlay = null; }
        super.onDestroy();
    }

    /**
     * 确保服务在跑（推送调度与弹幕都靠它），并按当前开关刷新常驻通知文案。
     * 幂等：服务已在跑时只是再走一次 onStartCommand。
     */
    public static void sync(Context ctx) {
        try { ctx.startForegroundService(new Intent(ctx, BotService.class)); }
        catch (Exception e) { }
    }

    /** 推送一批词时，让同一批词也以弹幕划过。 */
    public static void showBatch(Context ctx, String[] lines) {
        try {
            if (lines == null || lines.length == 0) return;
            Intent i = new Intent(ctx, BotService.class);
            i.setAction(ACTION_SHOW_BATCH);
            i.putExtra(EXTRA_LINES, lines);
            ctx.startForegroundService(i);
        } catch (Exception e) { }
    }
}
