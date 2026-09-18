package com.compass.proj;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** 每日早报闹钟注册（inexact，无需精确闹钟权限，±15 分钟系统误差） */
public final class AlarmScheduler {

    private AlarmScheduler() {}

    static PendingIntent buildPending(Context ctx) {
        Intent i = new Intent(ctx, AlarmReceiver.class);
        int flags = android.os.Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        return PendingIntent.getBroadcast(ctx, 2001, i, flags);
    }

    /** time 形如 "08:00" */
    public static void schedule(Context ctx, String time, boolean on) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = buildPending(ctx);
        if (!on) {
            am.cancel(pi);
            return;
        }
        String[] hm = (time == null ? "08:00" : time).split(":");
        int h = 8, m = 0;
        try {
            h = Integer.parseInt(hm[0]);
            m = hm.length > 1 ? Integer.parseInt(hm[1]) : 0;
        } catch (Exception ignore) {}

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, h);
        cal.set(java.util.Calendar.MINUTE, m);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1);   // 今天已过则明天
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pi);
    }
}
