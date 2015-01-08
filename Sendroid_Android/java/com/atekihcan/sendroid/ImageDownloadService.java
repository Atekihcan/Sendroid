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
import java.net.HttpURLConnection;
import java.net.URL;
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

    protected static Boolean CANCEL_DOWNLOAD = false;

    public ImageDownloadService() {
        super("ImageDownloadService");
    }

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
        cancelIntent.putExtra(NOTIFICATION_ID, notificationID);
        cancelIntent.putExtra(NOTIFICATION_ACTION, DOWNLOAD_CANCEL);
        PendingIntent pendingCancelIntent =
                PendingIntent.getService(this, notificationID, cancelIntent,
                                         PendingIntent.FLAG_CANCEL_CURRENT);
        mBuilder.addAction(R.drawable.ic_action_cancel, "Cancel", pendingCancelIntent);

        // Open in browser action, in case download doesn't start or stops

        Intent browserIntent = new Intent(this, BrowserService.class);
        browserIntent.putExtra(MSG_BODY, imageURL);
        browserIntent.putExtra(NOTIFICATION_ID, notificationID);
        cancelIntent.putExtra(NOTIFICATION_ACTION, DOWNLOAD_CANCEL);
        PendingIntent pendingBrowserIntent =
                PendingIntent.getService(this, notificationID, browserIntent, 0);
        mBuilder.addAction(R.drawable.ic_action_browser, "Open", pendingBrowserIntent);

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

            SimpleDateFormat date = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
            Date now = new Date();

            filePath =  Environment.getExternalStorageDirectory().getPath()
                                    + "/" + getResources().getString(R.string.app_name)
                                    + "/image_" + date.format(now) + ".jpg";

            imageOutputFile = new File(filePath);

            // Proceed only if file creation is successful
            try {
                Boolean fileCreated = imageOutputFile.createNewFile();
                if (fileCreated) {
                    DownloadFile(new URL(imageURL));
                }
            } catch (Exception e) {
                Timber.e(e, "Cannot create file");
                notifyImageDownloadFailed("Cannot access SD card");
            }
        } else {
            Timber.d("Cannot connect to network");
            notifyImageDownloadFailed("Network not found");
        }
    }

    /* Downloads the file located at url */
    private void DownloadFile (URL url) {
        try {
            Timber.d("Starting Download");
            mBuilder.setContentText("Connecting...");
            mNotificationManager.notify(notificationID, mBuilder.build());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            Timber.d("Connection opened with response : " + connection.getResponseMessage());
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.connect();

            Timber.d("Connected");
            long fileLength = connection.getContentLength();
            InputStream input = new BufferedInputStream(url.openStream());
            FileOutputStream output = new FileOutputStream(imageOutputFile);

            // If fileLength is less than 1, set download progress indeterminate
            if (fileLength < 1) {
                mBuilder.setProgress(0, 0, true);
                mNotificationManager.notify(notificationID, mBuilder.build());
            }

            byte data[] = new byte[1024];
            long total = 0;
            int count;

            Timber.d("Stream Opened. Available : " + input.available());
            // If download has not been cancelled, continue reading stream
            while (!CANCEL_DOWNLOAD && (count = input.read(data)) != -1) {
                total += count;
                if (fileLength > 0) {
                    progressChange(total, fileLength);
                }
                output.write(data, 0, count);
            }

            output.flush();
            output.close();
            input.close();

            // If fileLength is less than 1 and download is complete, notify user
            if (!CANCEL_DOWNLOAD && fileLength < 1) {
                notifyImageDownloadComplete();
            }

            if (CANCEL_DOWNLOAD && imageOutputFile != null) {
                try {
                    imageOutputFile.delete();
                } catch (Exception e) {
                    Timber.d(e.getClass().getName() + " happened");
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            Timber.d(e.getClass().getName() + " happened");
            notifyImageDownloadFailed(e.getClass().getSimpleName());
        }
    }

    /* Handles progress changed by showing progress bar */
    private void progressChange(long done, long fileLength){
        int progress = (int) (done * 100 / fileLength);
        if (lastUpdate != progress) {
            lastUpdate = progress;
            if (progress < 100) {
                mBuilder.setContentText(progress + "% of " + humanReadableByte(fileLength, true))
                        .setProgress(100, progress, false)
                        .setOngoing(true);
                mNotificationManager.notify(notificationID, mBuilder.build());
            } else {
                notifyImageDownloadComplete();
            }
        }
    }

    /* When download is complete updates notification with share intent */
    private void notifyImageDownloadComplete() {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(ImageDownloadService.this);

        String DEFAULT_IMAGE_SHARING_APP = prefs.getString(
                getResources().getString(R.string.prefs_default_image_key),
                getResources().getString(R.string.prefs_package_default));

        // Send broadcast to scan file by media
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                Uri.fromFile(imageOutputFile)));

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

    /* Notify user for image download failed*/
    private void notifyImageDownloadFailed(String error) {
        mBuilder.setContentTitle("Image Download Failed")
                .setContentText("Error : " + error)
                .setOngoing(false);
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

    /* Returns bytes in human-readable format */
    private static String humanReadableByte(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }
}