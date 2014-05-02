
package net.binaryparadox.kerplapp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;
import android.text.TextUtils;

import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.net.WifiStateChangeService;

@SuppressWarnings("deprecation") //See Task #2955
public class SettingsActivity extends PreferenceActivity
        implements OnSharedPreferenceChangeListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);
        EditTextPreference pref = (EditTextPreference) findPreference("repo_name");
        String current = pref.getText();
        if (TextUtils.isEmpty(current)) {
            String defaultValue = Utils.getDefaultRepoName();
            pref.setText(defaultValue);
        }
        setSummaries();
    }

    @Override
    protected void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equals("use_https")) {
            setResult(Activity.RESULT_OK);
            // use AsyncTask to keep this Activity responsive
            new AsyncTask<Void, Void, Void>() {

                @Override
                protected Void doInBackground(Void... params) {
                    WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
                    int wifiState = wifiManager.getWifiState();
                    if (wifiState == WifiManager.WIFI_STATE_ENABLING
                            || wifiState == WifiManager.WIFI_STATE_ENABLED) {

                        startService(new Intent(SettingsActivity.this, WifiStateChangeService.class));
                    }
                    return null;
                }
            }.execute((Void) null);
        } else if (key.equals("repo_name")) {
            setSummaries();
        }
    }

    private void setSummaries() {
        EditTextPreference pref = (EditTextPreference) findPreference("repo_name");
        String current = pref.getText();
        if (current.equals(Utils.getDefaultRepoName()))
            pref.setSummary(R.string.local_repo_name_summary);
        else
            pref.setSummary(current);
    }
}
