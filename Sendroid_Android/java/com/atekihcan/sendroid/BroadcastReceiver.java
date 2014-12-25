/*
 * Copyright (c) 2014. Atekihcan <com.atekihcan@gmail.com>
 *
 * Author	: Atekihcan
 * Website	: http://atekihcan.github.io
 */

package com.atekihcan.sendroid;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;


/**
 * Takes care of creating and managing a partial wake lock . It passes off the
 * work of processing the GCM message to an NotificationHandleService, while ensuring
 * that the device does not go back to sleep in the transition.
 */

public class BroadcastReceiver extends WakefulBroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // Explicitly specify that NotificationHandleService will handle the intent.
        ComponentName comp = new ComponentName(context.getPackageName(),
                NotificationHandleService.class.getName());
        // Start the service, keeping the device awake while it is launching.
        startWakefulService(context, (intent.setComponent(comp)));
        setResultCode(Activity.RESULT_OK);
    }
}