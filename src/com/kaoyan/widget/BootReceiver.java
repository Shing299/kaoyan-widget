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
    }
}