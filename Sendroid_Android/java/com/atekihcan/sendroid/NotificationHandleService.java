/*
 * Copyright (c) 2014-2015. Atekihcan <com.atekihcan@gmail.com>
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
import android.widget.Toast;

import timber.log.Timber;

/**
 * Does the actual handling of the GCM message. MessageReceiver holds a
 * partial wake lock for this service while the service does its work. When the
 * service is finished, it releases the wake lock.
 */
public class NotificationHandleService extends IntentService {

    private static final String CLIP = "com.atekihcan.clip";
    private static final String MSG_BODY = "com.atekihcan.msgBody";
    private static final String NOTIFICATION_ID = "com.atekihcan.notificationID";

    public NotificationHandleService() {
        super("NotificationHandleService");
    }

    private Handler uiHandler;

    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
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
                Timber.d("Send error: " + extras.toString());
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(NotificationHandleService.this,
                                "Send error: " + extras.toString(),
                                Toast.LENGTH_LONG).show();
                    }
                });
            } else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
                Timber.d("Deleted message on server: " + extras.toString());
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(NotificationHandleService.this,
                                "Deleted message on server: " + extras.toString(),
                                Toast.LENGTH_LONG).show();
                    }
                });
            } else if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
                Timber.d("Received : " + extras.get("type") + " : " + extras.get("body"));
                // If received message is either text or image or link, handle message
                if (extras.get("type").equals("txt") ||
                    extras.get("type").equals("img") ||
                    extras.get("type").equals("url")) {
                    handleMessage(Integer.parseInt(extras.get("id").toString()),
                            extras.get("type").toString(), extras.get("body").toString());
                } else {
                    // Should never happen
                    Timber.d("Error : Invalid message");
                }
            }
        }
        // Release the wake lock provided by the WakefulBroadcastReceiver.
        MessageReceiver.completeWakefulIntent(intent);
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

        // Get user preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Boolean AUTO_COPY =
                prefs.getBoolean(getResources().getString(R.string.prefs_auto_copy_key), false);
        Boolean AUTO_DOWNLOAD =
                prefs.getBoolean(getResources().getString(R.string.prefs_auto_download_key), false);
        String DEFAULT_SHARING_APP =
                prefs.getString(getResources().getString(R.string.prefs_default_share_key),
                        getResources().getString(R.string.prefs_package_default));

        // If automatic copy is on, put the message body in clipboard
        if (AUTO_COPY) {
            ClipboardManager clipBoard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(CLIP, body);
            clipBoard.setPrimaryClip(clip);
        }

        // If automatic image download is on, start image download service
        if (type.equals("img") && AUTO_DOWNLOAD) {
            Intent imageDownloadIntent = new Intent(this, ImageDownloadService.class);
            imageDownloadIntent.putExtra(MSG_BODY, body);
            imageDownloadIntent.putExtra(NOTIFICATION_ID, id);

            startService(imageDownloadIntent);
        } else {
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
                            .setTicker("Received " + msgType)
                            .setContentTitle("Received " + msgType)
                            .setContentText("Touch to share")
                            .setAutoCancel(true)
                            .setStyle(new NotificationCompat.BigTextStyle()
                                    .bigText(body)
                                    .setSummaryText("Touch to share"));

            mBuilder.setContentIntent(pendingShareIntent);

            // Add action button for copy
            Intent copyIntent = new Intent(this, CopyService.class);
            copyIntent.putExtra(MSG_BODY, body);
            copyIntent.putExtra(NOTIFICATION_ID, id);
            PendingIntent pendingCopyIntent = PendingIntent.getService(this, id, copyIntent, 0);
            mBuilder.addAction(R.drawable.ic_action_copy, "Copy", pendingCopyIntent);

            // If URL is received, add action button for opening in browser
            if (type.equals("img") || type.equals("url")) {
                Intent browserIntent = new Intent(this, BrowserService.class);
                browserIntent.putExtra(MSG_BODY, body);
                browserIntent.putExtra(NOTIFICATION_ID, id);

                PendingIntent pendingBrowserIntent = PendingIntent.getService(this, id,
                        browserIntent, 0);

                mBuilder.addAction(R.drawable.ic_action_browser, "Open",
                        pendingBrowserIntent);
            }

            // If image URL is received, add action button for image download
            if (type.equals("img")) {
                Intent imageDownloadIntent = new Intent(this, ImageDownloadService.class);
                imageDownloadIntent.putExtra(MSG_BODY, body);
                imageDownloadIntent.putExtra(NOTIFICATION_ID, id);
                PendingIntent pendingImageSaveIntent = PendingIntent.getService(this, id,
                        imageDownloadIntent, 0);

                mBuilder.addAction(R.drawable.ic_action_download, "Download",
                        pendingImageSaveIntent);
            }

            mNotificationManager.notify(id, mBuilder.build());
        }
    }
}