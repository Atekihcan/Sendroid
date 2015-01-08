/*
 * Copyright (c) 2014-2015. Atekihcan <com.atekihcan@gmail.com>
 *
 * Author	: Atekihcan
 * Website	: http://atekihcan.github.io
 */

package com.atekihcan.sendroid;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.Context;

/* Puts content of notification into clipboard */
public class CopyService extends IntentService {

    private static final String CLIP = "com.atekihcan.clip";
    private static final String MSG_BODY = "com.atekihcan.msgBody";
    private static final String NOTIFICATION_ID = "com.atekihcan.notificationID";

    public CopyService() {
        super("CopyService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String msg = (String) intent.getExtras().get(MSG_BODY);
        int notificationID = intent.getIntExtra(NOTIFICATION_ID, 42);

        // Cancel the notification
        NotificationManager mNotificationManager =
                (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(notificationID);

        // Close the notification panel
        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

        // Put the message body in clipboard
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(CLIP, msg);
        clipboard.setPrimaryClip(clip);
    }
}