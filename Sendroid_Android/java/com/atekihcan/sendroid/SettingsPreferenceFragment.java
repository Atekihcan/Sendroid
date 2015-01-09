/*
 * Copyright (c) 2014-2015. Atekihcan <com.atekihcan@gmail.com>
 *
 * Author	: Atekihcan
 * Website	: http://atekihcan.github.io
 */

package com.atekihcan.sendroid;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/* Displays settings */
public class SettingsPreferenceFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.app_preferences);

        ListPreference shareAppList = (ListPreference)
                findPreference(getResources().getString(R.string.prefs_default_share_key));
        ListPreference imageAppList = (ListPreference)
                findPreference(getResources().getString(R.string.prefs_default_image_key));

        final PackageManager packageManager = getActivity().getPackageManager();

        final Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        final List<ResolveInfo> sharePackageList =
                packageManager.queryIntentActivities(shareIntent, 0);
        Collections.sort(sharePackageList, new Comparator<ResolveInfo>(){
            public int compare(ResolveInfo pkg1, ResolveInfo pkg2) {
                return pkg1.loadLabel(packageManager).toString().replaceAll("^\\s+", "")
                           .compareToIgnoreCase(pkg2.loadLabel(packageManager)
                                   .toString().replaceAll("^\\s+", ""));
            }
        });
        CharSequence[] shareEntries = new CharSequence[sharePackageList.size() + 1];
        CharSequence[] shareEntryValues = new CharSequence[sharePackageList.size() + 1];

        try {
            int i = 0;
            String packageName;
            shareEntries[i] = getResources().getString(R.string.prefs_package_default);
            shareEntryValues[i] = getResources().getString(R.string.prefs_package_default);
            for ( ResolveInfo P : sharePackageList ) {
                i++;
                packageName = P.loadLabel(packageManager).toString().replaceAll("^\\s+", "");
                shareEntries[i] = String.valueOf(packageName.charAt(0)).toUpperCase()
                                                + packageName.substring(1, packageName.length());
                shareEntryValues[i] = P.activityInfo.applicationInfo.packageName;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        final Intent imageIntent = new Intent(Intent.ACTION_SEND);
        imageIntent.setType("image/jpeg");
        final List<ResolveInfo> imagePackageList =
                packageManager.queryIntentActivities(imageIntent, 0);
        Collections.sort(imagePackageList, new Comparator<ResolveInfo>(){
            public int compare(ResolveInfo pkg1, ResolveInfo pkg2) {
                return pkg1.loadLabel(packageManager).toString().replaceAll("^\\s+", "")
                        .compareToIgnoreCase(pkg2.loadLabel(packageManager)
                                .toString().replaceAll("^\\s+", ""));
            }
        });
        CharSequence[] imageEntries = new CharSequence[imagePackageList.size() + 1];
        CharSequence[] imageEntryValues = new CharSequence[imagePackageList.size() + 1];

        try {
            int i = 0;
            String packageName;
            imageEntries[i] = getResources().getString(R.string.prefs_package_default);
            imageEntryValues[i] = getResources().getString(R.string.prefs_package_default);
            for ( ResolveInfo P : imagePackageList ) {
                i++;
                packageName = P.loadLabel(packageManager).toString().replaceAll("^\\s+", "");
                imageEntries[i] = String.valueOf(packageName.charAt(0)).toUpperCase()
                        + packageName.substring(1, packageName.length());
                imageEntryValues[i] = P.activityInfo.applicationInfo.packageName;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        shareAppList.setEntries(shareEntries);
        shareAppList.setEntryValues(shareEntryValues);
        imageAppList.setEntries(imageEntries);
        imageAppList.setEntryValues(imageEntryValues);
    }
}