/*
 * Copyright (c) 2014-2015. Atekihcan <com.atekihcan@gmail.com>
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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;

import timber.log.Timber;

/* Downloads image from the notification creates by NotificationHandleService */
public class ImageDownloadService extends IntentService {

    private static final String MSG_BODY = "com.atekihcan.msgBody";
    private static final String NOTIFICATION_ID = "com.atekihcan.notificationID";
    private static final String NOTIFICATION_ACTION = "com.atekihcan.NOTIFICATION_ACTION";
    private static final String DOWNLOAD_CANCEL = "com.atekihcan.DOWNLOAD_CANCEL";

    private int lastUpdate = 0;
    private static int notificationID = 42;
    private static NotificationCompat.Builder mBuilder;
    private NotificationManager mNotificationManager;

    public static Boolean CANCEL_DOWNLOAD = false;

    public ImageDownloadService() {
        super("ImageDownloadService");
    }

    SimpleDateFormat date = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
    Date now = new Date();

    private static String filePath = Environment.getExternalStorageDirectory().getPath();
    private static File imageOutputFile;

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        CANCEL_DOWNLOAD = false;
        final String imageURL = intent.getStringExtra(MSG_BODY);
        notificationID = intent.getIntExtra(NOTIFICATION_ID, 42);
        Timber.d("Image : " + imageURL);

        // Create notification
        mNotificationManager = (NotificationManager) ImageDownloadService.this
                .getSystemService(Context.NOTIFICATION_SERVICE);

        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);

        mBuilder = new NotificationCompat.Builder(ImageDownloadService.this)
                .setSmallIcon(R.drawable.ic_stat)
                .setLargeIcon(largeIcon)
                .setContentTitle("Downloading Image")
                .setContentText("Starting download...")
                .setProgress(0, 0, false)
                .setOngoing(true)
                .setAutoCancel(false);

        // Create cancel action
        Intent cancelIntent = new Intent(this, ImageCancelService.class);
        cancelIntent.putExtra(NOTIFICATION_ACTION, DOWNLOAD_CANCEL);
        cancelIntent.putExtra(NOTIFICATION_ID, notificationID);
        PendingIntent pendingCancelIntent =
                PendingIntent.getService(this, notificationID, cancelIntent,
                                         PendingIntent.FLAG_CANCEL_CURRENT);
        mBuilder.addAction(R.drawable.ic_action_cancel, "Cancel Download", pendingCancelIntent);

        mNotificationManager.notify(notificationID, mBuilder.build());

        // Proceed only if network is present
        if (isNetworkAvailable()) {
            File imageDir = new File(Environment.getExternalStorageDirectory().getPath()
                    + "/" + getResources().getString(R.string.app_name));

            if (!imageDir.exists()) {
                try {
                    imageDir.mkdirs();
                } catch (Exception e) {
                    Timber.e(e, "Cannot create directory");
                }
            }

            filePath =  Environment.getExternalStorageDirectory().getPath()
                    + "/" + getResources().getString(R.string.app_name)
                    + "/image_" + date.format(now) + ".jpg";

            imageOutputFile = new File(filePath);

            // Proceed only if file creation is successful
            try {
                Boolean fileCreated = imageOutputFile.createNewFile();

                // Start download async task
                if (fileCreated) {
                    DownloadFile downloadFile = new DownloadFile();
                    downloadFile.execute(imageURL);
                }
            } catch (Exception e) {
                Timber.e(e, "Cannot create file");
                mBuilder.setContentTitle("Cannot access SD card")
                        .setContentText("Try again later.");
                mNotificationManager.notify(notificationID, mBuilder.build());
            }
        } else {
            Timber.d("Cannot connect to network");
            mBuilder.setContentTitle("Network not found")
                    .setContentText("Try again later.");
            mNotificationManager.notify(notificationID, mBuilder.build());
        }
    }

    /* AsyncTask for downloading image */
    private class DownloadFile extends AsyncTask<String, Integer, String> {
        @Override
        protected String doInBackground(String... sUrl) {
            try {
                Timber.d("Beginning Download");
                mBuilder.setContentText("Connecting...");
                mNotificationManager.notify(notificationID, mBuilder.build());
                URL url = new URL(sUrl[0]);
                URLConnection connection = url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();

                int fileLength = connection.getContentLength();
                InputStream input = new BufferedInputStream(url.openStream());
                FileOutputStream output = new FileOutputStream(imageOutputFile);

                byte data[] = new byte[1024];
                long total = 0;
                int count;

                Timber.d("Available : " + input.available());
                // If stream is not available for reading do not proceed and let the user know
                while (!CANCEL_DOWNLOAD && input.available() < 1) {
                    mBuilder.setContentText("Trying to fetch image...");
                    mNotificationManager.notify(notificationID, mBuilder.build());
                }

                // If download has not been cancelled, continue reading stream
                while (!CANCEL_DOWNLOAD && (count = input.read(data)) != -1) {
                    total += count;
                    progressChange((int) (total * 100) / fileLength);
                    output.write(data, 0, count);
                }

                output.flush();
                output.close();
                input.close();
            } catch (Exception e) {
                Timber.d(e.getClass().getName() + " happened");
                mBuilder.setContentTitle("Image Download Failed")
                        .setContentText("Try again later.");
                mNotificationManager.notify(notificationID, mBuilder.build());
            }
            return null;
        }
    }

    /* Handles progress changed by showing progress bar */
    void progressChange(int progress){
        if (lastUpdate != progress) {
            lastUpdate = progress;
            if (progress < 100) {
                mBuilder.setContentText("Download in progress..." + progress + "%")
                        .setProgress(100, progress, false)
                        .setOngoing(true);
                mNotificationManager.notify(notificationID, mBuilder.build());
            } else {
                notifyImageDownloadComplete();
            }
        }
    }

    /* When download is complete updates notification with share intent */
    void notifyImageDownloadComplete() {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(ImageDownloadService.this);

        String DEFAULT_IMAGE_SHARING_APP = prefs.getString(
                getResources().getString(R.string.prefs_default_image_key),
                getResources().getString(R.string.prefs_package_default));

        // Create notification
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/jpeg");
        shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(filePath));

        // If default image sharing app is set, use that
        if (!DEFAULT_IMAGE_SHARING_APP.equals(getResources().
                getString(R.string.prefs_package_default))) {
            shareIntent.setPackage(DEFAULT_IMAGE_SHARING_APP);
        }

        PendingIntent pendingShareIntent = PendingIntent.getActivity(
                ImageDownloadService.this, notificationID,
                Intent.createChooser(shareIntent, "Share image with..."),
                PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(ImageDownloadService.this)
                        .setSmallIcon(R.drawable.ic_stat)
                        .setTicker("Image Download Complete")
                        .setContentTitle("Image Download Complete")
                        .setContentText("Touch to share")
                        .setProgress(0, 0, false)
                        .setOngoing(false)
                        .setLargeIcon(getLargeIcon(filePath))
                        .setAutoCancel(true);

        mBuilder.setContentIntent(pendingShareIntent);
        mNotificationManager.notify(notificationID, mBuilder.build());
    }

    /* Checks whether network is present */
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    /* Create scaled down bitmap of downloaded image to use in the notification icon */
    private Bitmap getLargeIcon(String filePath) {
        Bitmap icon = BitmapFactory.decodeFile(filePath);

        if (icon != null) {
            final int maxSize = (int) (64 * getResources().getDisplayMetrics().density);
            int inWidth, inHeight, outWidth, outHeight;

            inWidth = icon.getWidth();
            inHeight = icon.getHeight();
            if (inWidth > inHeight) {
                outWidth = (inWidth * maxSize) / inHeight;
                outHeight = maxSize;
            } else {
                outWidth = maxSize;
                outHeight = (inHeight * maxSize) / inWidth;
            }

            return Bitmap.createScaledBitmap(icon, outWidth, outHeight, false);
        }

        return null;
    }
}