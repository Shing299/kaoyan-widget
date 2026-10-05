package com.kaoyan.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONObject;
import java.util.List;

public class BatchReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String a = intent.getAction();
        try {
            if (Scheduler.ACTION_ROLL.equals(a)) {
                Scheduler.schedule(ctx);
                beat(ctx);
                return;
            }
            if (Scheduler.ACTION_FIRE.equals(a)) {
                JSONObject st = Store.loadState(ctx);
                Engine.ensureState(st);
                if (st.optString("exam", "").length() < 10) return;
                List<Words.W> words = Words.load(ctx);
                String today = Engine.today();
                if (!today.equals(st.optString("day", ""))) {
                    Engine.buildPlan(st, words.size(), today);
                    Store.saveState(ctx, st);
                }
                // 一次推送、两种呈现：同一批词既进通知，也交给弹幕划过
                String[] dm = Engine.pushNext(ctx, words, st);
                if (dm != null && dm.length > 0) BotService.showBatch(ctx, dm);
                beat(ctx);
            }
        } catch (Throwable e) { android.util.Log.e("KAOYAN","ERR "+e); e.printStackTrace(); }
    }

    /**
     * 心跳：每天每批推送闹钟都会走到这里。
     * 服务（推送调度 + 弹幕）被系统冻结/停掉后，这一步会把它重新拉起来
     * （原来只能等用户重新打开 App，弹幕会一直静默）。
     */
    static void beat(Context ctx) {
        try { BotService.sync(ctx); } catch (Throwable t) { }
    }
}