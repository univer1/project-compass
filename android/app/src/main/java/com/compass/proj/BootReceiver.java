package com.compass.proj;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** 开机后重新注册每日早报（AlarmManager 闹钟不随重启保留） */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        SharedPreferences sp = ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        if (sp.getBoolean(MainActivity.KEY_REMIND_ON, true)) {
            AlarmScheduler.schedule(ctx, sp.getString(MainActivity.KEY_REMIND_TIME, "08:00"), true);
        }
    }
}
