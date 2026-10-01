package com.kaoyan.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.util.List;

public class WidgetProvider extends AppWidgetProvider {
    static final String ACTION_REFRESH = "com.kaoyan.widget.REFRESH";
    static final String PREFS = "kaoyan_widget";
    static final String KEY = "payload";
    static final String VIEW_URL = "http://127.0.0.1:8765/today.html";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) render(ctx, mgr, id);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            String p = intent.getStringExtra("p");
            if (p != null) {
                try {
                    String json = new String(Base64.decode(p, Base64.DEFAULT), Charset.forName("UTF-8"));
                    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json).apply();
                } catch (Exception e) { }
            }
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, WidgetProvider.class));
            for (int id : ids) render(ctx, mgr, id);
        }
    }

    static void render(Context ctx, AppWidgetManager mgr, int id) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_layout);
        String json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null);
        String title = "考研单词";
        String foot = "点击查看今日全部";
        String[] lines = new String[5];
        int n = 0;
        if (json != null) {
            try {
                JSONObject o = new JSONObject(json);
                if (o.has("title")) title = o.getString("title");
                if (o.has("foot")) foot = o.getString("foot");
                JSONArray arr = o.optJSONArray("words");
                if (arr != null) {
                    for (int i = 0; i < arr.length() && i < 5; i++) {
                        JSONObject w = arr.getJSONObject(i);
                        lines[i] = "[" + w.optString("k", "") + "] "
                            + w.optString("w", "") + w.optString("p", "")
                            + " " + w.optString("m", "");
                        n = i + 1;
                    }
                }
            } catch (Exception e) { }
        }
        int[] slot = {R.id.w1, R.id.w2, R.id.w3, R.id.w4, R.id.w5};
        if (n == 0) {
            v.setTextViewText(R.id.w1, "等待推送…");
            v.setViewVisibility(R.id.w1, android.view.View.VISIBLE);
            for (int i = 1; i < 5; i++) v.setViewVisibility(slot[i], android.view.View.GONE);
        } else {
            for (int i = 0; i < 5; i++) {
                if (i < n) {
                    v.setTextViewText(slot[i], lines[i]);
                    v.setViewVisibility(slot[i], android.view.View.VISIBLE);
                } else {
                    v.setViewVisibility(slot[i], android.view.View.GONE);
                }
            }
        }
        v.setTextViewText(R.id.w_title, title);
        v.setTextViewText(R.id.w_foot, foot);

        Intent open = new Intent(ctx, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.root, pi);

        mgr.updateAppWidget(id, v);
    }

    /** 供 App 内部调用：写 payload 到 prefs 并刷新所有小组件。 */
    public static void updateWidget(Context ctx, String title, List<Words.W> words, JSONArray items) {
        try {
            JSONObject o = new JSONObject();
            o.put("title", title);
            o.put("foot", "点击查看今日全部");
            JSONArray arr = new JSONArray();
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONArray it = items.getJSONArray(i);
                    int idx = it.getInt(0), kind = it.getInt(1);
                    Words.W w = words.get(idx);
                    JSONObject wj = new JSONObject();
                    wj.put("w", w.word);
                    wj.put("p", w.ph.length() > 0 ? ("[" + w.ph + "] ") : "");
                    wj.put("m", w.mean);
                    wj.put("k", kind == 1 ? "复" : "新");
                    arr.put(wj);
                }
            }
            o.put("words", arr);
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply();
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, WidgetProvider.class));
            for (int id : ids) render(ctx, mgr, id);
        } catch (Exception e) { }
    }
}
