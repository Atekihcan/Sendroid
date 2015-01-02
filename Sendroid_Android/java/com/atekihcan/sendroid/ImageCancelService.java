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

import timber.log.Timber;

/* Handle cancel download notification action click */
public class ImageCancelService extends IntentService {

    private static final String NOTIFICATION_ID = "com.atekihcan.notificationID";
    private static final String NOTIFICATION_ACTION = "com.atekihcan.NOTIFICATION_ACTION";
    private static final String DOWNLOAD_CANCEL = "com.atekihcan.DOWNLOAD_CANCEL";

    public ImageCancelService() {
        super("ImageCancelService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String actionType = (String) intent.getExtras().get(NOTIFICATION_ACTION);
        int notificationID = intent.getIntExtra(NOTIFICATION_ID, 42);
        NotificationManager mNotificationManager = (NotificationManager) ImageCancelService.this
                                                    .getSystemService(Context.NOTIFICATION_SERVICE);

        // Notify download service and cancel notification
        if (actionType.equals(DOWNLOAD_CANCEL)) {
            Timber.d("Cancelling download");
            ImageDownloadService.CANCEL_DOWNLOAD = true;
            mNotificationManager.cancel(notificationID);
        }
    }
}
