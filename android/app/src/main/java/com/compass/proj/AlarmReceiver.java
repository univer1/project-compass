package com.compass.proj;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** 每日早报：读快照 → 通知栏推送，点开直达今日页 */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        SharedPreferences sp = ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        String json = sp.getString(MainActivity.KEY_DIGEST, null);

        String title;
        String body;
        int over = 0, nudge = 0, soon = 0;
        StringBuilder tops = new StringBuilder();
        if (json != null) {
            try {
                org.json.JSONObject o = new org.json.JSONObject(json);
                over = o.optInt("over", 0);
                nudge = o.optInt("nudge", 0);
                soon = o.optInt("soon", 0);
                org.json.JSONArray arr = o.optJSONArray("tops");
                if (arr != null) {
                    for (int i = 0; i < arr.length() && i < 3; i++) {
                        tops.append('·').append(arr.optString(i, "")).append('\n');
                    }
                }
            } catch (Exception ignore) {}
        }

        if (json == null) {
            title = "项目罗盘 · 早报";
            body = "点开查看今日安排";
        } else if (over == 0 && nudge == 0 && soon == 0) {
            title = "项目罗盘 · 早报";
            body = "没有逾期、待催和临近截止，安心推进";
        } else {
            title = "项目罗盘 · 早报";
            body = "逾期 " + over + " 项 · 该催 " + nudge + " 项 · 3 天内截止 " + soon + " 项";
            if (tops.length() > 0) {
                body += "\n" + tops.toString().trim();
                // 通知正文多行需要 BigText 样式
            }
        }

        Intent open = new Intent(ctx, MainActivity.class);
        open.putExtra("tab", "today");
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = android.os.Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(ctx, 3001, open, flags);

        Notification.Builder b;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(ctx, MainActivity.CHANNEL_ID);
        } else {
            b = new Notification.Builder(ctx);
        }
        b.setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(pi)
                .setAutoCancel(true);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            try { nm.notify(4001, b.build()); } catch (Exception ignore) {}
        }
    }
}
