package com.kaoyan.widget;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView web;
    static final int REQ_WORDS = 1001;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(this), "App");
        setContentView(web);
        load();
        // 启动前台服务并确保调度
        try { startForegroundService(new Intent(this, BotService.class)); } catch (Exception e) { }
        try { Scheduler.schedule(this); } catch (Exception e) { }
    }

    void load() {
        try {
            String html = HtmlView.build(this);
            web.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
        } catch (Exception e) { }
    }

    /** 打开系统文件选择器，导入自定义词库。 */
    void pickWordFile() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("text/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i, REQ_WORDS);
        } catch (Exception e) { }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_WORDS && res == RESULT_OK && data != null && data.getData() != null) {
            try {
                java.io.InputStream in = getContentResolver().openInputStream(data.getData());
                java.io.FileOutputStream out = new java.io.FileOutputStream(Words.customFile(this));
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close();
                in.close();
            } catch (Exception e) { }
            load();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }
}