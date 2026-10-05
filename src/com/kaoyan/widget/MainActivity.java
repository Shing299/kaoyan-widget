package com.kaoyan.widget;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView web;
    private boolean showingSetup = false;
    private boolean examSet = false;
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
        // 首次安装后申请通知权限 + 引导自启动 / 电池优化
        try { Perms.firstRun(this); } catch (Exception e) { }
    }

    /** 通知权限申请结果：无论允许与否，随后弹出后台运行 / 自启动引导。 */
    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == Perms.REQ_NOTIF) {
            try { Perms.showGuide(this); } catch (Exception e) { }
        }
    }

    void load() {
        try {
            examSet = Store.loadState(this).optString("exam", "").length() >= 10;
            String html = HtmlView.build(this);
            showingSetup = HtmlView.FORCE_SETUP || !examSet;
            web.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
            web.clearHistory();
            web.scrollTo(0, 0);
        } catch (Exception e) { }
    }

    /** 从通知/小组件再次进入时复用同一实例，刷新到最新推送。 */
    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        HtmlView.FORCE_SETUP = false;
        load();
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
        // 在设置页按返回 -> 退回主页；主页按返回 -> 直接回桌面，不再翻 WebView 旧历史
        if (showingSetup && examSet) {
            HtmlView.FORCE_SETUP = false;
            load();
        } else {
            super.onBackPressed();
        }
    }
}