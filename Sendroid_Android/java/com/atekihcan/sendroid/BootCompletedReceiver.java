/*
 * Copyright (c) 2014-2015. Atekihcan <com.atekihcan@gmail.com>
 *
 * Author	: Atekihcan
 * Website	: http://atekihcan.github.io
 */

package com.atekihcan.sendroid;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/* Sets alarm for deleting downloaded files when device restarts */
public class BootCompletedReceiver extends BroadcastReceiver {
    public BootCompletedReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            // Set an alarm for deleting downloaded files
            try {
                Intent deleteIntent = new Intent(context, FileDeleteService.class);
                PendingIntent pendingDeleteIntent =
                        PendingIntent.getService(context, 0, deleteIntent,
                                PendingIntent.FLAG_CANCEL_CURRENT);

                AlarmManager deleteManager =
                        (AlarmManager) context.getSystemService(Activity.ALARM_SERVICE);

                // Fires inexact alarm once in a day
                deleteManager.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis(), AlarmManager.INTERVAL_DAY, pendingDeleteIntent);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
