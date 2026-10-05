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
        // 开机恢复：BotService 同时负责推送调度与学习弹幕，
        // 弹幕开关由它自己每 tick 读 state 决定，这里不用再单独拉弹幕服务
        Scheduler.schedule(ctx);
        try { BotService.sync(ctx); } catch (Exception e) { }
    }
}