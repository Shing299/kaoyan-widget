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
 
    public static Notification buildService(Context ctx) {
        ensureChannel(ctx);
        String win = "08:00–22:00";
        try {
            JSONObject st = Store.loadState(ctx);
            Engine.ensureState(st);
            win = Engine.fmtHM(Engine.pushStartMin(st)) + "–" + Engine.fmtHM(Engine.pushEndMin(st));
        } catch (Exception e) { }
        return new Notification.Builder(ctx, CH)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("考研单词推送运行中")
            .setContentText(win + " 随机推送 · 点击查看今日单词")
            .setContentIntent(openIntent(ctx))
            .setOngoing(true)
            .build();
    }

    /** 学习弹幕前台服务通知。 */
    public static Notification buildDanmaku(Context ctx) {
        ensureChannel(ctx);
        String win = "08:00–22:00";
        try {
            JSONObject st = Store.loadState(ctx);
            Engine.ensureState(st);
            win = Engine.fmtHM(Engine.pushStartMin(st)) + "–" + Engine.fmtHM(Engine.pushEndMin(st));
        } catch (Exception e) { }
        return new Notification.Builder(ctx, CH)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("学习弹幕已开启")
            .setContentText(win + " 内使用手机时划过单词 · 熄屏不弹")
            .setContentIntent(openIntent(ctx))
            .setOngoing(true)
            .build();
    }
}