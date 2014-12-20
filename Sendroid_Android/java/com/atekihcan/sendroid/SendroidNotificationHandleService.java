/*
 * Copyright (c) 2014. Atekihcan <com.atekihcan@gmail.com>
 *
 * Author	: Atekihcan
 * Website	: http://atekihcan.github.io
 */

package com.atekihcan.sendroid;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

/**
 * Does the actual handling of the GCM message. SendroidBroadcastReceiver holds a
 * partial wake lock for this service while the service does its work. When the
 * service is finished, it releases the wake lock.
 */
public class SendroidNotificationHandleService extends IntentService {
    public static final String TAG = "SendroidNotificationHandleService";
    public static final String SENDROID_CLIP = "sendroid_clip";
    public static final String SENDROID_MSG_BODY = "sendroid_message_body";
    public static final String SENDROID_NOTIFICATION_ID = "sendroid_notification_id";

    public SendroidNotificationHandleService() {
        super("SendroidNotificationHandleService");
    }

    private Handler uiHandler;

    public void onCreate() {
        super.onCreate();
        uiHandler = new Handler();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);

        String messageType = gcm.getMessageType(intent);

        if (!extras.isEmpty()) {
            /*
             * Filter messages based on message type. Since it is likely that GCM will be
             * extended in the future with new message types, just ignore any message types you're
             * not interested in, or that you don't recognize.
             */
            if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
                Log.i(TAG, "Send error: " + extras.toString());
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(SendroidNotificationHandleService.this,
                                "[Sendroid] Send error: " + extras.toString(),
                                Toast.LENGTH_LONG).show();
                    }
                });
            } else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
                Log.i(TAG, "Deleted message on server: " + extras.toString());
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(SendroidNotificationHandleService.this,
                                "[Sendroid] Deleted message on server: " + extras.toString(),
                                Toast.LENGTH_LONG).show();
                    }
                });
            } else if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
                Log.i(TAG, "Received : " + extras.get("type") + " : " + extras.get("body"));
                // If received message is either text or image or link, handle message
                if (extras.get("type").equals("txt") ||
                    extras.get("type").equals("img") ||
                    extras.get("type").equals("url")) {
                    handleMessage(Integer.parseInt(extras.get("id").toString()),
                            extras.get("type").toString(), extras.get("body").toString());
                } else {
                    // Should never happen
                    Log.i(TAG, "Error : Invalid message");
                }
            }
        }
        // Release the wake lock provided by the WakefulBroadcastReceiver.
        SendroidBroadcastReceiver.completeWakefulIntent(intent);
    }

    /* Handles the message by showing a notification with actions */
    private void handleMessage(int id, String type, String body) {
        String msgType = "";

        if (type.equals("img")) {
            msgType = "image link";
        } else if (type.equals("txt")) {
            msgType = "text";
        } else if (type.equals("url")) {
            msgType = "link";
        }

        SharedPreferences sendroidPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        Boolean DEFAULT_COPY =
                sendroidPrefs.getBoolean(getResources().getString(R.string.prefs_copy_key), false);
        String DEFAULT_SHARING_APP =
                sendroidPrefs.getString(getResources().getString(R.string.prefs_share_key),
                                      getResources().getString(R.string.prefs_package_default));

        // If default copy is on, put the message body in clipboard
        if (DEFAULT_COPY) {
            ClipboardManager clipBoard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(SENDROID_CLIP, body);
            clipBoard.setPrimaryClip(clip);
        }

        // Create notification
        NotificationManager mNotificationManager = (NotificationManager)
                this.getSystemService(Context.NOTIFICATION_SERVICE);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, body);

        // If default sharing app is set, use that
        if (!DEFAULT_SHARING_APP.equals(getResources().getString(R.string.prefs_package_default))) {
            shareIntent.setPackage(DEFAULT_SHARING_APP);
        }
		
        PendingIntent pendingShareIntent = PendingIntent.getActivity(this, id,
                Intent.createChooser(shareIntent, "Share " + msgType + " with..."), 0);

        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setLargeIcon(largeIcon)
                    .setContentTitle("Received " + msgType)
                    .setContentText("Touch to share " + msgType)
                    .setAutoCancel(true)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body));

        mBuilder.setContentIntent(pendingShareIntent);

        Intent copyIntent = new Intent(this, SendroidCopyService.class);
        copyIntent.putExtra(SENDROID_MSG_BODY, body);
        copyIntent.putExtra(SENDROID_NOTIFICATION_ID, id);
        PendingIntent pendingCopyIntent = PendingIntent.getService(this, id, copyIntent, 0);
        mBuilder.addAction(R.drawable.ic_action_copy, "Copy", pendingCopyIntent);


        if (type.equals("img") || type.equals("url")) {
            Intent browserIntent = new Intent(this, SendroidBrowserService.class);
            browserIntent.putExtra(SENDROID_MSG_BODY, body);
            browserIntent.putExtra(SENDROID_NOTIFICATION_ID, id);

            PendingIntent pendingBrowserIntent = PendingIntent.getService(this, id,
                    browserIntent, 0);

            mBuilder.addAction(R.drawable.ic_action_browser, "Open",
                    pendingBrowserIntent);
        }

        if (type.equals("img")) {
            Intent imageSaveIntent = new Intent(this, SendroidImageDownloadService.class);
            Log.i(TAG, "Image : " + body);
            imageSaveIntent.putExtra(SENDROID_MSG_BODY, body);
            imageSaveIntent.putExtra(SENDROID_NOTIFICATION_ID, id);
            PendingIntent pendingImageSaveIntent = PendingIntent.getService(this, id,
                    imageSaveIntent, 0);

            mBuilder.addAction(R.drawable.ic_action_download, "Download",
                    pendingImageSaveIntent);
        }

        mNotificationManager.notify(id, mBuilder.build());
    }
}