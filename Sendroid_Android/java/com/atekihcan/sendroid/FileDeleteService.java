/*
 * Copyright (c) 2014-2015. Atekihcan <com.atekihcan@gmail.com>
 *
 * Author	: Atekihcan
 * Website	: http://atekihcan.github.io
 */

package com.atekihcan.sendroid;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;

import java.io.File;

import timber.log.Timber;


/* Delete files on receiving alarm */
public class FileDeleteService extends IntentService {

    public FileDeleteService() {
        super("FileDeleteService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        Timber.d("Received alarm!!!");
        File imageDir = new File(Environment.getExternalStorageDirectory().getPath()
                + "/" + getResources().getString(R.string.app_name));

        // Get user preference for max file age
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String DELETE_AFTER =
                prefs.getString(getResources().getString(R.string.prefs_auto_delete_key),
                        getResources().getString(R.string.prefs_delete_default));

        if (!DELETE_AFTER.equals(getResources().getString(R.string.prefs_delete_default))) {
            final long MAX_AGE = Integer.parseInt(DELETE_AFTER) * 60 * 1000L;

            // Get list of files in the directory
            if (imageDir.exists()) {
                File[] files = imageDir.listFiles();

                // iterate over all the files to check their age and delete
                for (File f : files) {
                    Long lastModified = f.lastModified();
                    if (lastModified + MAX_AGE < System.currentTimeMillis()) {
                        Timber.d("Deleting " + f.getName());
                        f.delete();
                    }
                }
            }
        }
    }
}
