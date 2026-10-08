package com.kaoyan.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.Random;

public class Scheduler {
    public static final String ACTION_FIRE = "com.kaoyan.widget.FIRE";
    public static final String ACTION_ROLL = "com.kaoyan.widget.ROLL";
    static final int RC_BASE = 2000;
    static final int RC_ROLL = 1999;
    static final int MAX_BATCH = 60;

    static PendingIntent pi(Context c, int rc, String action) {
        Intent i = new Intent(c, BatchReceiver.class);
        i.setAction(action);
        return PendingIntent.getBroadcast(c, rc, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void setAlarm(Context c, long at, int rc, String action) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent p = pi(c, rc, action);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
            }
        } catch (Exception e) {
            try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p); } catch (Exception e2) { }
        }
    }

    public static void cancelAll(Context c) {
        cancelFires(c);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi(c, RC_ROLL, ACTION_ROLL));
    }

    /**
     * 只撤销剩余的「推送批次」闹钟，**保留**每日 ROLL 闹钟。
     * 当天计划推完后调用：否则剩下十几个闹钟还会照常把设备唤醒，
     * 醒来却发现没词可推（白耗电）。
     */
    public static void cancelFires(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        for (int i = 0; i <= MAX_BATCH; i++) am.cancel(pi(c, RC_BASE + i, ACTION_FIRE));
    }

    /** 确保今天有计划，并为剩余批次安排闹钟。返回安排批次数（-1 出错）。 */
    public static int schedule(Context c) {
        try {
            cancelAll(c);
            JSONObject st = Store.loadState(c);
            Engine.ensureState(st);
            String today = Engine.today();
            if (!today.equals(st.optString("day", ""))) {
                Engine.buildPlan(st, Words.load(c).size(), today);
                Store.saveState(c, st);
            }
            long now = System.currentTimeMillis();
            JSONArray plan = st.getJSONArray("plan");
            int pos = st.getInt("plan_pos");
            int remaining = plan.length() - pos;
            int batches = (remaining + Engine.WORDS_PER_PUSH - 1) / Engine.WORDS_PER_PUSH;
            if (batches <= 0) { setRoll(c); return 0; }

            Calendar cal = Calendar.getInstance();
            int sMin = Engine.pushStartMin(st);
            int eMin = Engine.pushEndMin(st);
            if (eMin <= sMin) eMin = sMin + 60;
            Calendar end = (Calendar) cal.clone();
            end.set(Calendar.HOUR_OF_DAY, eMin / 60);
            end.set(Calendar.MINUTE, eMin % 60); end.set(Calendar.SECOND, 0); end.set(Calendar.MILLISECOND, 0);
            Calendar start = (Calendar) cal.clone();
            start.set(Calendar.HOUR_OF_DAY, sMin / 60);
            start.set(Calendar.MINUTE, sMin % 60); start.set(Calendar.SECOND, 0); start.set(Calendar.MILLISECOND, 0);

            long winStart = Math.max(now, start.getTimeInMillis());
            long endMs = end.getTimeInMillis();
            long[] times = new long[batches];
            Random rnd = new Random();
            if (now >= endMs) {
                for (int i = 0; i < batches; i++) times[i] = now + 20000L * (i + 1);
            } else {
                double span = Math.max(30000.0, (double) (endMs - winStart));
                double step = span / batches;
                for (int i = 0; i < batches; i++)
                    times[i] = winStart + (long) (step * (i + 0.15 + rnd.nextDouble() * 0.75));
            }
            for (int i = 0; i < batches && i < MAX_BATCH; i++)
                setAlarm(c, times[i], RC_BASE + i, ACTION_FIRE);
            setRoll(c);
            return batches;
        } catch (Exception e) { return -1; }
    }

    static void setRoll(Context c) {
        try {
            JSONObject st = Store.loadState(c);
            int sMin = Engine.pushStartMin(st);
            Calendar t = Calendar.getInstance();
            t.set(Calendar.HOUR_OF_DAY, sMin / 60);
            t.set(Calendar.MINUTE, sMin % 60);
            t.set(Calendar.SECOND, 0); t.set(Calendar.MILLISECOND, 0);
            long at = t.getTimeInMillis();
            if (at <= System.currentTimeMillis()) at += 86400000L;
            setAlarm(c, at, RC_ROLL, ACTION_ROLL);
        } catch (Exception e) { }
    }
}