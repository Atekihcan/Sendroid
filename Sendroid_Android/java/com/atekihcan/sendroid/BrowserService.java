/*
 * Copyright (c) 2014-2015. Atekihcan <com.atekihcan@gmail.com>
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

import timber.log.Timber;


/* Opens the received link in browser and cancels the notification */
public class BrowserService extends IntentService {

    private static final String MSG_BODY = "com.atekihcan.msgBody";
    private static final String NOTIFICATION_ID = "com.atekihcan.notificationID";
    private static final String NOTIFICATION_ACTION = "com.atekihcan.NOTIFICATION_ACTION";
    private static final String DOWNLOAD_CANCEL = "com.atekihcan.DOWNLOAD_CANCEL";

    public BrowserService() {
        super("BrowserService");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String msg = (String) intent.getExtras().get(MSG_BODY);
        String downloadCancel = (String) intent.getExtras().get(NOTIFICATION_ACTION);
        int notificationID = intent.getIntExtra(NOTIFICATION_ID, 42);

        // Cancel the notification
        NotificationManager mNotificationManager =
                (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(notificationID);

        // Close the notification panel
        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

        // If it is coming from ImageDownloadService, cancel the download
        if (downloadCancel != null && downloadCancel.equals(DOWNLOAD_CANCEL)) {
            Timber.d("Cancelling download");
            ImageDownloadService.CANCEL_DOWNLOAD = true;
        }

        // Open browser with the link
        Intent openBrowserIntent = new Intent(Intent.ACTION_VIEW);
        openBrowserIntent.setData(Uri.parse(msg));
        openBrowserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        this.startActivity(openBrowserIntent);
    }
}