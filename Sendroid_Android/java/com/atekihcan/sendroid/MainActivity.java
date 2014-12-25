/*
 * Copyright (c) 2014. Atekihcan <com.atekihcan@gmail.com>
 *
 * Author	: Atekihcan
 * Website	: http://atekihcan.github.io
 */

package com.atekihcan.sendroid;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

/* Creates main and only UI of the application and creates and stores GCM registration ID */
public class MainActivity extends Activity {

    static final String TAG = "MainActivity";
    public static final String REGID_CLIP = "com.atekihcan.regidClip";
    public static final String REGISTRATION_ID = "com.atekihcan.registrationID";
    private static final String APP_VERSION = "com.atekihcan.appVersion";
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    String SENDER_ID = "GCM_SENDER_KEY";

    String regID;
    Context context;
    TextView textRegStatus;
    GoogleCloudMessaging gcm;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager.setDefaultValues(this, R.xml.app_preferences, false);

        setContentView(R.layout.app_main);
        textRegStatus = (TextView) findViewById(R.id.text_reg_status);

        context = getApplicationContext();

        // Check device for Play Services APK. If check succeeds, proceed with GCM registration.
        if (isGooglePlayServices()) {
            gcm = GoogleCloudMessaging.getInstance(this);
            regID = getGCMRegistrationId(context);
            Log.i(TAG, "Registration ID = " + regID);

            if (regID.isEmpty()) {
                textRegStatus.setText(R.string.reg_status_ing);
                registerInBackground();
            } else {
                textRegStatus.setText(R.string.reg_status_ok);
            }
        } else {
            Log.i(TAG, "No valid Google Play Services APK found.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check device for Play Services APK.
        isGooglePlayServices();
    }

    /* Check if the device has Google Play Services installed */
    private boolean isGooglePlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i(TAG, "This device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }

    /* Stores the registration ID and the app version in the application's SharedPreferences. */
    private void storeRegistrationId(Context context, String regId) {
        final SharedPreferences prefs =
                getSharedPreferences(MainActivity.class.getSimpleName(),
                        Context.MODE_PRIVATE);
        int appVersion = getAppVersion(context);
        Log.i(TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(REGISTRATION_ID, regId);
        editor.putInt(APP_VERSION, appVersion);
        editor.apply();
    }

    /* Gets the current GCM registration ID, if there is one. */
    private String getGCMRegistrationId(Context context) {
        final SharedPreferences prefs =
                getSharedPreferences(MainActivity.class.getSimpleName(),
                        Context.MODE_PRIVATE);
        String registrationId = prefs.getString(REGISTRATION_ID, "");
        if (registrationId.isEmpty()) {
            textRegStatus.setText(R.string.reg_status_nok);
            Log.i(TAG, "Registration not found.");
            return "";
        }

        /* Check if app was updated; if so, it must clear the registration ID since the
         * existing regID is not guaranteed to work with the new app version. */
        int registeredVersion = prefs.getInt(APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion) {
            Log.i(TAG, "App version changed.");
            return "";
        }
        return registrationId;
    }

    /* Registers the application with GCM servers asynchronously. */
    private void registerInBackground() {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String msg;
                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(context);
                    }
                    regID = gcm.register(SENDER_ID);
                    Log.i(TAG, "Registration ID = " + regID);
                    msg = getResources().getString(R.string.reg_status_ok);

                    // Persist the regID - no need to register again.
                    storeRegistrationId(context, regID);
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                    /* TODO: If there is an error, don't just keep trying to register.
                     * Ask the user to click a button again, or perform exponential back-off. */
                }
                return msg;
            }

            @Override
            protected void onPostExecute(String msg) {
                textRegStatus.setText(msg);
            }
        }.execute(null, null, null);
    }

    public void onClick(View view) {
        if (!regID.isEmpty()) {
            /* copy/mail registration id buttons */
            if (view == findViewById(R.id.button_copy_regid)) {
                ClipboardManager clipBoard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText(REGID_CLIP, regID);
                clipBoard.setPrimaryClip(clip);

                Toast.makeText(this, "Copied Registration ID", Toast.LENGTH_SHORT).show();
            } else if (view == findViewById(R.id.button_mail_regid)) {
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                emailIntent.setType("text/plain");
                emailIntent.setData(Uri.parse("mailto:"));
                emailIntent.putExtra(Intent.EXTRA_SUBJECT,
                                    getResources().getString(R.string.app_name)
                                    + " registration ID for : "
                                    + Build.MODEL);
                emailIntent.putExtra(Intent.EXTRA_TEXT, regID);
                startActivity(emailIntent);
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /* Returns Application's version code */
    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.app_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_refresh:
                context.sendBroadcast(new Intent("com.google.android.intent.action.GTALK_HEARTBEAT"));
                context.sendBroadcast(new Intent("com.google.android.intent.action.MCS_HEARTBEAT"));
                Toast.makeText(this, "Reconnection Successful", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.action_settings:
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
            case R.id.action_feedback:
                // Mail me
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                emailIntent.setType("text/plain");
                emailIntent.setData(Uri.parse("mailto:com.atekihcan@gmail.com"));
                emailIntent.putExtra(Intent.EXTRA_SUBJECT,
                        getResources().getString(R.string.app_name)
                                + " Feedback");
                startActivity(emailIntent);
                finish();
                return true;
            case R.id.action_help:
                // Open browser to show help page
                Intent helpIntent = new Intent(Intent.ACTION_VIEW);
                helpIntent.setData(Uri.parse("http://atekihcan.github.io/Sendroid/#why"));
                helpIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(helpIntent);
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
