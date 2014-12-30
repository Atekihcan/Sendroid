/*
 * Copyright (c) 2014. Atekihcan <com.atekihcan@gmail.com>
 *
 * Author	: Atekihcan
 * Website	: http://atekihcan.github.io
 */

package com.atekihcan.sendroid;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;


/* Opens the received link in browser and cancels the notification */
public class BrowserService extends IntentService {

    public static final String MSG_BODY = "com.atekihcan.msgBody";
    public static final String NOTIFICATION_ID = "com.atekihcan.notificationID";
    public static int notificationID = 42;

    public BrowserService() {
        super("BrowserService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String msg = (String) intent.getExtras().get(MSG_BODY);
        notificationID = intent.getIntExtra(NOTIFICATION_ID, 42);

        // Cancel the notification
        NotificationManager mNotificationManager = (NotificationManager)
                BrowserService.this.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(notificationID);

        // Close the notification panel
        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

        // Open browser with the link
        Intent openBrowserIntent = new Intent(Intent.ACTION_VIEW);
        openBrowserIntent.setData(Uri.parse(msg));
        openBrowserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        this.startActivity(openBrowserIntent);
    }
}