package com.kaoyan.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/**
 * 首次安装后的权限引导：
 *   1) 运行时申请通知权限（Android 13+ POST_NOTIFICATIONS）
 *   2) 弹窗引导用户去系统里打开「自启动 / 后台运行」，并关闭电池优化
 *
 * 说明：Android 没有「自启动」的标准运行时权限接口，只能跳转到厂商设置页；
 * 这里对 ColorOS / OPPO / vivo / 华为 / 小米 等常见路径逐一尝试，找不到再回退到应用详情页。
 */
public class Perms {
    static final int REQ_NOTIF = 2001;
    private static final String SP = "kaoyan_perm";
    private static final String K_ASKED = "asked";

    /** 首次运行：申请通知权限，并弹出后台/自启动引导（每个安装只自动弹一次）。 */
    static void firstRun(Activity a) {
        try {
            SharedPreferences sp = a.getSharedPreferences(SP, Context.MODE_PRIVATE);
            if (sp.getBoolean(K_ASKED, false)) return;
            sp.edit().putBoolean(K_ASKED, true).apply();

            if (Build.VERSION.SDK_INT >= 33
                    && a.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                       != PackageManager.PERMISSION_GRANTED) {
                a.requestPermissions(
                        new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
                // 引导弹窗在 onRequestPermissionsResult 里弹出，避免两个对话框重叠
            } else {
                afterNotif(a);
            }
        } catch (Throwable e) { }
    }

    /** 通知权限处理完之后：先引导「悬浮窗」权限，再引导后台运行。 */
    static void afterNotif(Activity a) {
        try {
            if (!Settings.canDrawOverlays(a)) showOverlayGuide(a);
            else showGuide(a);
        } catch (Throwable e) { }
    }

    /** 引导开启「悬浮窗」权限（学习弹幕需要）。 */
    static void showOverlayGuide(Activity a) {
        try {
            AlertDialog d = new AlertDialog.Builder(a)
                .setTitle("开启悬浮窗权限")
                .setMessage("「学习弹幕」需要「悬浮窗」权限，才能在你使用手机时从屏幕上划过单词。\n\n"
                        + "点击「去开启」后，请在列表中找到本应用，允许它「显示在其他应用上层」。")
                .setPositiveButton("去开启", new OverlayClick(a))
                .setNegativeButton("以后再说", new SkipOverlayClick(a))
                .create();
            d.setCanceledOnTouchOutside(false);
            d.show();
        } catch (Throwable e) { }
    }

    /** 弹出后台运行 / 自启动引导。 */
    static void showGuide(Activity a) {
        try {
            AlertDialog d = new AlertDialog.Builder(a)
                .setTitle("开启后台运行权限")
                .setMessage("为保证每天按时推送单词，请允许本应用「自启动」并「关闭电池优化」。\n\n"
                        + "· 通知权限：已在首次启动时申请\n"
                        + "· 自启动：请在系统设置里打开\n"
                        + "· 电池优化：请选择「不优化 / 允许后台运行」")
                .setPositiveButton("去设置自启动", new AutoStartClick(a))
                .setNeutralButton("关闭电池优化", new BatteryClick(a))
                .setNegativeButton("以后再说", null)
                .create();
            d.setCanceledOnTouchOutside(false);
            d.show();
        } catch (Throwable e) { }
    }

    /** 打开厂商「自启动管理」设置页；逐个尝试，找不到则回退到应用详情页。 */
    static void openAutoStart(Activity a) {
        String[][] pages = {
            {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
            {"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"},
            {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
            {"com.coloros.phonemanager", "com.coloros.phonemanager.permission.startup.StartupAppListActivity"},
            {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
            {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
            {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
            {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"}
        };
        for (int i = 0; i < pages.length; i++) {
            try {
                Intent it = new Intent();
                it.setComponent(new ComponentName(pages[i][0], pages[i][1]));
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                a.startActivity(it);
                return;
            } catch (Throwable e) { }
        }
        openAppDetails(a);
    }

    /** 请求忽略电池优化。 */
    static void requestIgnoreBattery(Activity a) {
        try {
            Intent it = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            it.setData(Uri.parse("package:" + a.getPackageName()));
            a.startActivity(it);
        } catch (Throwable e) {
            try {
                Intent it = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                a.startActivity(it);
            } catch (Throwable e2) { openAppDetails(a); }
        }
    }

    /** 请求「悬浮窗」权限（学习弹幕需要）。 */
    static void requestOverlay(Activity a) {
        try {
            Intent it = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            it.setData(Uri.parse("package:" + a.getPackageName()));
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(it);
        } catch (Throwable e) {
            try { a.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)); } catch (Throwable e2) { }
        }
    }

    private static void openAppDetails(Activity a) {
        try {
            Intent it = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            it.setData(Uri.parse("package:" + a.getPackageName()));
            a.startActivity(it);
        } catch (Throwable e) { }
    }

    /** 命名静态内部类（避免匿名内部类触发 d8 NPE）。 */
    static class AutoStartClick implements DialogInterface.OnClickListener {
        private final Activity a;
        AutoStartClick(Activity a) { this.a = a; }
        public void onClick(DialogInterface d, int w) { openAutoStart(a); }
    }

    static class BatteryClick implements DialogInterface.OnClickListener {
        private final Activity a;
        BatteryClick(Activity a) { this.a = a; }
        public void onClick(DialogInterface d, int w) { requestIgnoreBattery(a); }
    }

    static class OverlayClick implements DialogInterface.OnClickListener {
        private final Activity a;
        OverlayClick(Activity a) { this.a = a; }
        public void onClick(DialogInterface d, int w) { requestOverlay(a); showGuide(a); }
    }

    static class SkipOverlayClick implements DialogInterface.OnClickListener {
        private final Activity a;
        SkipOverlayClick(Activity a) { this.a = a; }
        public void onClick(DialogInterface d, int w) { showGuide(a); }
    }
}