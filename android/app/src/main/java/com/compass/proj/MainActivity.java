package com.compass.proj;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    static final String CHANNEL_ID = "daily_digest";
    static final String PREFS = "compass_prefs";
    static final String KEY_DIGEST = "digest_json";
    static final String KEY_REMIND_ON = "remind_on";
    static final String KEY_REMIND_TIME = "remind_time";

    private WebView web;
    private String pendingExport = null;
    private boolean pageReady = false;
    private boolean openTodayOnReady = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createChannel();
        requestNotifPermissionIfNeeded();

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);           // localStorage 生效
        s.setAllowFileAccess(true);              // API 30+ 默认关闭，file:// 加载需要
        s.setAllowContentAccess(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                if (openTodayOnReady) {
                    openTodayOnReady = false;
                    evalJs("window.__openToday && window.__openToday()");
                }
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");

        handleViewIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleViewIntent(intent);
    }

    /* 通知点击 → 直达今日页 */
    private void handleViewIntent(Intent intent) {
        if (intent != null && "today".equals(intent.getStringExtra("tab"))) {
            if (pageReady) evalJs("window.__openToday && window.__openToday()");
            else openTodayOnReady = true;
        }
    }

    /* ============ JS 桥 ============ */
    private class Bridge {

        @JavascriptInterface
        public void saveDailyDigest(String json) {
            prefs().edit().putString(KEY_DIGEST, json).apply();
        }

        @JavascriptInterface
        public void setAlarm(String time, boolean on) {
            prefs().edit().putBoolean(KEY_REMIND_ON, on).putString(KEY_REMIND_TIME, time).apply();
            AlarmScheduler.schedule(MainActivity.this, time, on);
        }

        @JavascriptInterface
        public void exportBackup(String json) {
            pendingExport = json;
            String name = "项目罗盘备份-" + new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                    .format(new java.util.Date()) + ".json";
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, name);
            try {
                startActivityForResult(i, 4001);
            } catch (Exception e) {
                toast("无法调出保存窗口");
            }
        }

        @JavascriptInterface
        public void importBackup() {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            try {
                startActivityForResult(i, 4002);
            } catch (Exception e) {
                toast("无法调出文件选择器");
            }
        }

        @JavascriptInterface
        public void requestNotif() {
            requestNotifPermissionIfNeeded();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == 4001 && pendingExport != null) {
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                os.write(pendingExport.getBytes(StandardCharsets.UTF_8));
                toast("备份已保存");
            } catch (Exception e) {
                toast("写入失败，请换一个位置");
            }
            pendingExport = null;
        } else if (requestCode == 4002) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
                final String text = sb.toString();
                evalJs("window.__onNativeImport && window.__onNativeImport(" +
                        JSONObject.quote(text) + ")");
            } catch (Exception e) {
                toast("读取文件失败");
            }
        }
    }

    /* ============ 返回键：先关弹层，再退 APP ============ */
    @Override
    public void onBackPressed() {
        if (web != null) {
            web.evaluateJavascript("window.__back ? window.__back() : false", new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    if (value == null || "false".equals(value) || "null".equals(value)) {
                        finish();
                    }
                }
            });
        } else {
            super.onBackPressed();
        }
    }

    /* ============ 生命周期 ============ */
    @Override
    protected void onResume() {
        super.onResume();
        // 开机未打开 APP 时闹钟可能丢失，兜底重注册
        if (prefs().getBoolean(KEY_REMIND_ON, true)) {
            AlarmScheduler.schedule(this, prefs().getString(KEY_REMIND_TIME, "08:00"), true);
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }

    /* ============ 工具 ============ */
    private SharedPreferences prefs() { return getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    private void evalJs(String js) {
        if (web != null) web.evaluateJavascript(js, null);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                    "每日早报", NotificationManager.IMPORTANCE_DEFAULT));
        }
    }

    private void requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 100);
            }
        }
    }

    private void toast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
