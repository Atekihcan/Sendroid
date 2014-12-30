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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.widget.Toast;

import timber.log.Timber;

/* Downloads image from the notification creates by NotificationHandleService */
public class ImageDownloadService extends IntentService {

    private static final String MSG_BODY = "com.atekihcan.msgBody";
    private static final String NOTIFICATION_ID = "com.atekihcan.notificationID";

    private static String filePath = Environment.getExternalStorageDirectory().getPath();

    private int lastUpdate = 0;
    private static NotificationCompat.Builder mBuilder;
    private NotificationManager mNotificationManager;
    private static int notificationID = 42;

    public ImageDownloadService() {
        super("ImageDownloadService");
    }

    SimpleDateFormat date = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
    Date now = new Date();

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final String imageURL = intent.getStringExtra(MSG_BODY);
        notificationID = intent.getIntExtra(NOTIFICATION_ID, 42);
        Timber.d("Image : " + imageURL);

        DownloadFile downloadFile = new DownloadFile();
        downloadFile.execute(imageURL);

        // Create notification
        mNotificationManager = (NotificationManager) ImageDownloadService.this
                .getSystemService(Context.NOTIFICATION_SERVICE);

        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);

        mBuilder = new NotificationCompat.Builder(ImageDownloadService.this)
                .setSmallIcon(R.drawable.ic_stat)
                .setLargeIcon(largeIcon)
                .setContentTitle("Downloading Image")
                .setContentText("Download in progress")
                .setProgress(0, 0, false)
                .setAutoCancel(false);

        mNotificationManager.notify(notificationID, mBuilder.build());
    }

    private class DownloadFile extends AsyncTask<String, Integer, String> {
        @Override
        protected String doInBackground(String... sUrl) {
            if (!isNetworkAvailable()) {
                Timber.d("Cannot connect to network");
                mBuilder.setContentText("You are not connected to internet. Try again later.");
                mNotificationManager.notify(notificationID, mBuilder.build());
                Handler uiHandler =
                        new Handler(ImageDownloadService.this.getMainLooper());
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ImageDownloadService.this,
                                "Network not available. Cannot download image.",
                                Toast.LENGTH_LONG).show();
                    }
                });
                return null;
            }
            try {
                URL url = new URL(sUrl[0]);
                URLConnection connection = url.openConnection();
                connection.connect();
                // this will be useful so that you can show a typical 0-100% progress bar
                int fileLength = connection.getContentLength();

                // download the file
                InputStream input = new BufferedInputStream(url.openStream());

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

                File file = new File(filePath);
                try {
                    file.createNewFile();
                } catch (Exception e) {
                    Timber.e(e, "Cannot create file");
                    mBuilder.setContentText("Cannot access SD card.");
                    mNotificationManager.notify(notificationID, mBuilder.build());
                    Handler uiHandler =
                            new Handler(ImageDownloadService.this.getMainLooper());
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ImageDownloadService.this,
                                    "Unable to access SD card. Cannot download image.",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                    return null;
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

    /* Handles progress changed by showing progress bar */
    void progressChange(int progress){
        if (lastUpdate != progress) {
            lastUpdate = progress;
            if (progress < 100) {
                mBuilder.setProgress(100, progress, false)
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
                getResources().getString(R.string.prefs_image_key),
                getResources().getString(R.string.prefs_package_default));

        // Create notification
        NotificationManager mNotificationManager = (NotificationManager)
                ImageDownloadService.this
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
                ImageDownloadService.this, notificationID,
                Intent.createChooser(shareIntent, "Share image with..."),
                PendingIntent.FLAG_CANCEL_CURRENT);

        Bitmap bitmap = BitmapFactory.decodeFile(filePath);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(ImageDownloadService.this)
                        .setSmallIcon(R.drawable.ic_stat)
                        .setContentTitle("Image Download Complete")
                        .setContentText("Touch to share")
                        .setProgress(0, 0, false)
                        .setOngoing(false)
                        .setLargeIcon(bitmap)
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
}