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
                Engine.pushNext(ctx, words, st);
                beat(ctx);
            }
        } catch (Throwable e) { android.util.Log.e("KAOYAN","ERR "+e); e.printStackTrace(); }
    }

    /**
     * 弹幕心跳：每天每批推送闹钟都会走到这里。
     * 若弹幕开着但服务已被系统冻结/停掉，这一步会把它重新拉起来
     * （原来只能等用户重新打开 App，弹幕会一直静默）。
     */
    static void beat(Context ctx) {
        try { DanmakuService.syncFromState(ctx); } catch (Throwable t) { }
    }
}