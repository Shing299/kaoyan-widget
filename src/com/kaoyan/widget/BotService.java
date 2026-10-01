package com.kaoyan.widget;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

public class BotService extends Service {
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(1000, Notifier.buildService(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(1000, Notifier.buildService(this));
            }
        } catch (Exception e) { }
        try { Scheduler.schedule(this); } catch (Exception e) { }
        return START_STICKY;
    }
}
