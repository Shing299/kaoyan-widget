package com.kaoyan.widget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONObject;

public class Notifier {
    static final String CH = "kaoyan";
    static final int RECALL_ID = 1001;

    static void ensureChannel(Context ctx) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(CH) == null) {
                NotificationChannel c = new NotificationChannel(CH, "考研单词", NotificationManager.IMPORTANCE_HIGH);
                c.enableLights(true);
                c.setShowBadge(true);
                nm.createNotificationChannel(c);
            }
        } catch (Exception e) { }
    }

    static PendingIntent openIntent(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(ctx, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void postBatch(Context ctx, String title, String content) {
        try {
            ensureChannel(ctx);
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            Notification n = new Notification.Builder(ctx, CH)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(content.replace("\n", "  "))
                .setStyle(new Notification.BigTextStyle().bigText(content))
                .setContentIntent(openIntent(ctx))
                .setAutoCancel(true)
                .setGroup("kaoyan_words")
                .build();
            nm.notify((int) (System.currentTimeMillis() % 2000000000), n);
        } catch (Exception e) { }
    }

    public static void dismissRecall(Context ctx) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(RECALL_ID);
        } catch (Exception e) { }
    }
 
    /**
     * 唯一的常驻通知（推送调度与学习弹幕共用一个前台服务）。
     * 一行里同时交代「推送时段」和「弹幕开关」，不再挂两条通知。
     */
    public static Notification buildService(Context ctx) {
        ensureChannel(ctx);
        String win = "08:00–22:00";
        boolean dm = false;
        try {
            JSONObject st = Store.loadState(ctx);
            Engine.ensureState(st);
            win = Engine.fmtHM(Engine.pushStartMin(st)) + "–" + Engine.fmtHM(Engine.pushEndMin(st));
            dm = "1".equals(st.optString("danmaku_on", "0"));
        } catch (Exception e) { }
        return new Notification.Builder(ctx, CH)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("考研单词 · 运行中")
            .setContentText(win + " 分批推送｜学习弹幕" + (dm ? "已开启" : "已关闭"))
            .setContentIntent(openIntent(ctx))
            .setOngoing(true)
            .build();
    }
}