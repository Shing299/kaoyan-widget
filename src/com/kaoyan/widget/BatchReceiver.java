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
            }
        } catch (Throwable e) { android.util.Log.e("KAOYAN","ERR "+e); e.printStackTrace(); }
    }
}