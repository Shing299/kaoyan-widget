package com.kaoyan.widget;

import android.app.Activity;
import android.content.Context;
import android.webkit.JavascriptInterface;
import java.io.File;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class Bridge {
    private Activity a;
    public Bridge(Activity a) { this.a = a; }

    @JavascriptInterface public void pushNow() { a.runOnUiThread(new PushTask(a)); }
    @JavascriptInterface public void reload() { a.runOnUiThread(new LoadTask(a)); }
    @JavascriptInterface public void setExam(String d) { a.runOnUiThread(new ExamTask(a, d)); }
    @JavascriptInterface public void pickWords() { a.runOnUiThread(new PickTask(a)); }
    @JavascriptInterface public void resetWords() { a.runOnUiThread(new ResetTask(a)); }
    @JavascriptInterface public void openSetup() { a.runOnUiThread(new SetupTask(a)); }
    @JavascriptInterface public void setPushTime(String s, String e) { a.runOnUiThread(new PushTimeTask(a, s, e)); }

    static void doPush(Context ctx) {
        try {
            List<Words.W> ws = Words.load(ctx);
            JSONObject st = Store.loadState(ctx);
            Engine.ensureState(st);
            if (st.optString("exam", "").length() < 10) return; // 未完成初始设置不推送
            String today = Engine.today();
            if (!today.equals(st.optString("day", ""))) {
                Engine.buildPlan(st, ws.size(), today);
                Store.saveState(ctx, st);
            }
            Engine.pushNext(ctx, ws, st);
        } catch (Throwable e) { }
    }

    static void doSetExam(Context ctx, String d) {
        try {
            if (d != null && d.length() >= 10) {
                JSONObject st = Store.loadState(ctx);
                Engine.ensureState(st);
                st.put("exam", d.substring(0, 10));
                // 按新词量重排今日计划
                st.put("plan", new JSONArray());
                st.put("plan_pos", 0);
                st.put("day", JSONObject.NULL);
                Store.saveState(ctx, st);
            }
            HtmlView.FORCE_SETUP = false;
        } catch (Throwable e) { }
    }

    static class PushTask implements Runnable {
        Activity a;
        PushTask(Activity a) { this.a = a; }
        public void run() { doPush(a); ((MainActivity) a).load(); }
    }

    static class LoadTask implements Runnable {
        Activity a;
        LoadTask(Activity a) { this.a = a; }
        public void run() { ((MainActivity) a).load(); }
    }

    static class ExamTask implements Runnable {
        Activity a; String d;
        ExamTask(Activity a, String d) { this.a = a; this.d = d; }
        public void run() { doSetExam(a, d); ((MainActivity) a).load(); }
    }

    static class PickTask implements Runnable {
        Activity a;
        PickTask(Activity a) { this.a = a; }
        public void run() { ((MainActivity) a).pickWordFile(); }
    }

    static class ResetTask implements Runnable {
        Activity a;
        ResetTask(Activity a) { this.a = a; }
        public void run() {
            try { File f = Words.customFile(a); if (f.exists()) f.delete(); } catch (Throwable e) { }
            ((MainActivity) a).load();
        }
    }

    static class SetupTask implements Runnable {
        Activity a;
        SetupTask(Activity a) { this.a = a; }
        public void run() { HtmlView.FORCE_SETUP = true; ((MainActivity) a).load(); }
    }

    /** 保存每日推送时段，并按新时段重排今天剩余批次。 */
    static void doSetPushTime(Context ctx, String s, String e) {
        try {
            JSONObject st = Store.loadState(ctx);
            Engine.ensureState(st);
            int sm = Engine.parseHM(s, Engine.DEF_START_MIN);
            int em = Engine.parseHM(e, Engine.DEF_END_MIN);
            if (em <= sm) em = sm + 60;
            st.put("push_start", Engine.fmtHM(sm));
            st.put("push_end", Engine.fmtHM(em));
            Store.saveState(ctx, st);
            Scheduler.schedule(ctx);
        } catch (Throwable t) { }
    }

    static class PushTimeTask implements Runnable {
        Activity a; String s, e;
        PushTimeTask(Activity a, String s, String e) { this.a = a; this.s = s; this.e = e; }
        public void run() { doSetPushTime(a, s, e); ((MainActivity) a).load(); }
    }
}