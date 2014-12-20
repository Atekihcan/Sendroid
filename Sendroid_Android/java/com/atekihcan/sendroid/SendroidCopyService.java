/*
 * Copyright (c) 2014. Atekihcan <com.atekihcan@gmail.com>
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
public class SendroidCopyService extends IntentService {
    //public static final String TAG = "SendroidCopyService";
    public static final String SENDROID_CLIP = "sendroid_clip";
    public static final String SENDROID_MSG_BODY = "sendroid_message_body";
    public static final String SENDROID_NOTIFICATION_ID = "sendroid_notification_id";
    public static int notificationID = 42;

    public SendroidCopyService() {
        super("SendroidCopyService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String msg = (String) intent.getExtras().get(SENDROID_MSG_BODY);
        notificationID = intent.getIntExtra(SENDROID_NOTIFICATION_ID, 42);

        // Cancel the notification
        NotificationManager mNotificationManager = (NotificationManager)
                SendroidCopyService.this.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(notificationID);

        // Close the notification panel
        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

        // Put the message body in clipboard
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(SENDROID_CLIP, msg);
        clipboard.setPrimaryClip(clip);
    }
}