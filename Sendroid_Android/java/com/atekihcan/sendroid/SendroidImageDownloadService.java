/*
 * Copyright (c) 2014. Atekihcan <com.atekihcan@gmail.com>
 *
 * Author	: Atekihcan
 * Website	: http://atekihcan.github.io
 */

package com.atekihcan.sendroid;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

/* Downloads image from the notification creates by SendroidNotificationHandleService */
public class SendroidImageDownloadService extends IntentService {

    public static final String TAG = "SendroidImageDownloadService";

    private static final String SENDROID_MSG_BODY = "sendroid_message_body";
    private static final String SENDROID_NOTIFICATION_ID = "sendroid_notification_id";

    private static String filePath = Environment.getExternalStorageDirectory().getPath();

    private int lastUpdate = 0;
    private static NotificationCompat.Builder mBuilder;
    private NotificationManager mNotificationManager;
    private static int notificationID = 42;

    public SendroidImageDownloadService() {
        super("SendroidImageDownloadService");
    }

    SimpleDateFormat date = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
    Date now = new Date();

    @Override
    protected void onHandleIntent(Intent intent) {
        final String imageURL = intent.getStringExtra(SENDROID_MSG_BODY);
        notificationID = intent.getIntExtra(SENDROID_NOTIFICATION_ID, 42);
        Log.i(TAG, "Image : " + imageURL);


        // Create notification
        mNotificationManager = (NotificationManager) SendroidImageDownloadService.this
                                    .getSystemService(Context.NOTIFICATION_SERVICE);

        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);

        mBuilder = new NotificationCompat.Builder(SendroidImageDownloadService.this)
                        .setSmallIcon(R.drawable.ic_stat)
                        .setLargeIcon(largeIcon)
                        .setContentTitle("Downloading Image")
                        .setContentText("Download in progress")
                        .setProgress(0, 0, false)
                        .setAutoCancel(false);

        mNotificationManager.notify(notificationID, mBuilder.build());

        DownloadFile downloadFile = new DownloadFile();
        downloadFile.execute(imageURL);
    }

    private class DownloadFile extends AsyncTask<String, Integer, String> {
        @Override
        protected String doInBackground(String... sUrl) {
            try {
                URL url = new URL(sUrl[0]);
                URLConnection connection = url.openConnection();
                connection.connect();
                // this will be useful so that you can show a typical 0-100% progress bar
                int fileLength = connection.getContentLength();

                // download the file
                InputStream input = new BufferedInputStream(url.openStream());

                File sendroidImageDir = new File(Environment.getExternalStorageDirectory().getPath()
                        + "/Sendroid");

                if (!sendroidImageDir.exists()) {
                    try {
                        sendroidImageDir.mkdirs();
                    } catch (Exception e) {
                        Log.w(TAG, e.toString() + " (Cannot create directory)");
                    }
                }

                filePath =  Environment.getExternalStorageDirectory().getPath()
                                + "/Sendroid/image_" + date.format(now) + ".jpg";

                File file = new File(filePath);
                try {
                    file.createNewFile();
                } catch (Exception e) {
                    Log.w(TAG, e.toString() + " (Cannot create file)");
                }

                FileOutputStream output = new FileOutputStream(file);

                byte data[] = new byte[1024];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    total += count;
                    progressChange((int) (total * 100) / fileLength);
                    output.write(data, 0, count);
                }

                output.flush();
                output.close();
                input.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    void progressChange(int progress){
        if (lastUpdate != progress) {
            lastUpdate = progress;
            if (progress < 100) {
                mBuilder.setProgress(100, progress, false);
                mNotificationManager.notify(notificationID, mBuilder.build());
            } else {
                notifyImageDownloadComplete();
            }
        }
    }

    void notifyImageDownloadComplete() {
        SharedPreferences sendroidPrefs = PreferenceManager
                .getDefaultSharedPreferences(SendroidImageDownloadService.this);

        String DEFAULT_IMAGE_SHARING_APP = sendroidPrefs.getString(
                getResources().getString(R.string.prefs_image_key),
                getResources().getString(R.string.prefs_package_default));

        // Create notification
        NotificationManager mNotificationManager = (NotificationManager)
                SendroidImageDownloadService.this
                        .getSystemService(Context.NOTIFICATION_SERVICE);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/jpeg");
        shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(filePath));

        // If default image sharing app is set, use that
        if (!DEFAULT_IMAGE_SHARING_APP.equals(getResources().
                getString(R.string.prefs_package_default))) {
            shareIntent.setPackage(DEFAULT_IMAGE_SHARING_APP);
        }

        PendingIntent pendingShareIntent = PendingIntent.getActivity(
                SendroidImageDownloadService.this, notificationID,
                Intent.createChooser(shareIntent, "Share image with..."),
                PendingIntent.FLAG_CANCEL_CURRENT);

        Bitmap bitmap = BitmapFactory.decodeFile(filePath);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(SendroidImageDownloadService.this)
                        .setSmallIcon(R.drawable.ic_stat)
                        .setContentTitle("Download Complete")
                        .setContentText("Touch to share image")
                        .setProgress(0, 0, false)
                        .setOngoing(false)
                        .setLargeIcon(bitmap)
                        .setAutoCancel(true);

        mBuilder.setContentIntent(pendingShareIntent);
        mNotificationManager.notify(notificationID, mBuilder.build());
    }
}