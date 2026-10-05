package com.kaoyan.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            Intent s = new Intent(ctx, BotService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(s);
            else ctx.startService(s);
        } catch (Exception e) { }
        Scheduler.schedule(ctx);
        // 开机后如果弹幕是开着的，也一并恢复（syncFromState 会自行检查开关与悬浮窗权限）
        try { DanmakuService.syncFromState(ctx); } catch (Exception e) { }
    }
}