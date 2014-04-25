
package net.binaryparadox.kerplapp;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import net.binaryparadox.kerplapp.repo.LocalRepoService;

import org.fdroid.fdroid.Utils;
import org.spongycastle.operator.OperatorCreationException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Locale;

public class KerplappActivity extends Activity {
    private static final String TAG = "KerplappActivity";
    private ProgressDialog repoProgress;

    private WifiManager wifiManager;
    private ToggleButton repoSwitch;

    private boolean localRepoServiceIsBound = false;
    private Messenger localRepoServiceMessenger = null;

    private int SET_IP_ADDRESS = 7345;
    private int UPDATE_REPO = 7346;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.kerplapp_activity);

        repoSwitch = (ToggleButton) findViewById(R.id.repoSwitch);
        wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
    }

    @Override
    public void onResume() {
        super.onResume();
        int wifiState = wifiManager.getWifiState();
        if (wifiState == WifiManager.WIFI_STATE_ENABLED) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (KerplappApplication.ipAddress != wifiInfo.getIpAddress()) {
                setIpAddressFromWifi();
                if (repoSwitch.isChecked() && localRepoServiceMessenger != null) {
                    try {
                        Message msg = Message.obtain(null,
                                LocalRepoService.RESTART, LocalRepoService.RESTART, 0);
                        localRepoServiceMessenger.send(msg);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
            wireRepoSwitchToWebServer();
        } else {
            repoSwitch.setText(R.string.enable_wifi);
            repoSwitch.setTextOn(getString(R.string.enabling_wifi));
            repoSwitch.setTextOff(getString(R.string.enable_wifi));
            repoSwitch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    enableWifi();
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.kerplapp_activity, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_setup_repo:
                startActivityForResult(new Intent(this, AppSelectActivity.class), UPDATE_REPO);
                return true;
            case R.id.menu_send_fdroid_via_wifi:
                if (!repoSwitch.isChecked()) {
                    bindService();
                    repoSwitch.setChecked(true);
                }
                startActivity(new Intent(this, QrWizardWifiNetworkActivity.class));
                return true;
            case R.id.menu_settings:
                startActivityForResult(new Intent(this, SettingsActivity.class), SET_IP_ADDRESS);
                return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK)
            return;
        if (requestCode == SET_IP_ADDRESS) {
            setIpAddressFromWifi();
        } else if (requestCode == UPDATE_REPO) {
            setIpAddressFromWifi();
            new UpdateAsyncTask(this, KerplappApplication.selectedApps.toArray(new String[0]))
                    .execute();
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case 0:
                repoProgress = new ProgressDialog(this);
                repoProgress.setMessage("Scanning Apps. Please wait...");
                repoProgress.setIndeterminate(false);
                repoProgress.setMax(100);
                repoProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                repoProgress.setCancelable(false);
                repoProgress.show();
                return repoProgress;
            default:
                return null;
        }
    }

    private void enableWifi() {
        wifiManager.setWifiEnabled(true);
        new WaitForWifiAsyncTask().execute();
    }

    private void wireRepoSwitchToWebServer() {
        repoSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (repoSwitch.isChecked()) {
                    bindService();
                } else {
                    unbindService();
                }
            }
        });
    }

    @TargetApi(14)
    private void setIpAddressFromWifi() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean useHttps = prefs.getBoolean("use_https", false);

        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        String ssid = wifiInfo.getSSID().replaceAll("^\"(.*)\"$", "$1");

        KerplappApplication.ipAddress = wifiInfo.getIpAddress();
        KerplappApplication.ipAddressString = String.format(Locale.ENGLISH, "%d.%d.%d.%d",
                (KerplappApplication.ipAddress & 0xff),
                (KerplappApplication.ipAddress >> 8 & 0xff),
                (KerplappApplication.ipAddress >> 16 & 0xff),
                (KerplappApplication.ipAddress >> 24 & 0xff));

        String scheme;
        if (useHttps)
            scheme = "https";
        else
            scheme = "http";
        KerplappApplication.repo.address = String.format(Locale.ENGLISH, "%s://%s:%d/fdroid/repo",
                scheme, KerplappApplication.ipAddressString, KerplappApplication.port);
        KerplappApplication.repo.fingerprint = KerplappApplication.localRepoKeyStore.getFingerprint();

        // the fingerprint is not useful on the button label
        String buttonLabel = KerplappApplication.repo.address.replaceAll("\\?.*$", "");
        repoSwitch.setText(buttonLabel);
        repoSwitch.setTextOn(buttonLabel);
        repoSwitch.setTextOff(buttonLabel);
        String fdroidrepoUriString = Utils.getSharingUri(this).toString();
        ImageView repoQrCodeImageView = (ImageView) findViewById(R.id.repoQrCode);
        // fdroidrepo:// and fdroidrepos:// ensures it goes directly to F-Droid
        KerplappApplication.localRepo.setUriString(KerplappApplication.repo.address);
        KerplappApplication.localRepo.writeIndexPage(fdroidrepoUriString);
        /*
         * Set URL to UPPER for compact QR Code, FDroid will translate it back.
         * Remove the SSID from the query string since SSIDs are case-sensitive.
         * Instead the receiver will have to rely on the BSSID to find the right
         * wifi AP to join. Lots of QR Scanners are buggy and do not respect
         * custom URI schemes, so we have to use http:// or https:// :-(
         */
        String qrUriString = fdroidrepoUriString
                .replaceFirst("fdroidrepo", "http")
                .replaceAll("ssid=[^?]*", "")
                .toUpperCase(Locale.ENGLISH);
        Log.i("QRURI", qrUriString);
        Bitmap qrBitmap = Utils.generateQrCode(this, qrUriString);
        repoQrCodeImageView.setImageBitmap(qrBitmap);

        TextView wifiNetworkNameTextView = (TextView) findViewById(R.id.wifiNetworkName);
        wifiNetworkNameTextView.setText(KerplappApplication.ssid);

        TextView fingerprintTextView = (TextView) findViewById(R.id.fingerprint);
        if (KerplappApplication.repo.fingerprint != null) {
            fingerprintTextView.setVisibility(View.VISIBLE);
            fingerprintTextView.setText(KerplappApplication.repo.fingerprint);
        } else {
            fingerprintTextView.setVisibility(View.GONE);
        }

        // Once the IP address is known we need to generate a self signed
        // certificate to use for HTTPS that has a CN field set to the
        // ipAddressString. We'll generate it even if useHttps is false
        // to simplify having to detect when that preference changes.
        try {
            KerplappApplication.localRepoKeyStore.setupHTTPSCertificate();
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (OperatorCreationException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // the required NFC API was added in 4.0 aka Ice Cream Sandwich
        if (Build.VERSION.SDK_INT > 13) {
            NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
            if (nfcAdapter == null)
                return;
            nfcAdapter.setNdefPushMessage(new NdefMessage(new NdefRecord[] {
                    NdefRecord.createUri(Utils.getSharingUri(this)),
            }), this);
        }
    }

    public class WaitForWifiAsyncTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            try {
                while (!wifiManager.isWifiEnabled()) {
                    Log.i(TAG, "waiting for the wifi to be enabled...");
                    Thread.sleep(3000);
                    Log.i(TAG, "ever recover from sleep?");
                }
                Log.i(TAG, "0");
                KerplappApplication.ipAddress = wifiManager.getConnectionInfo().getIpAddress();
                Log.i(TAG, "1");
                while (KerplappApplication.ipAddress == 0) {
                    Log.i(TAG, "waiting for an IP address...");
                    Thread.sleep(3000);
                    KerplappApplication.ipAddress = wifiManager.getConnectionInfo().getIpAddress();
                }
                Log.i(TAG, "2");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            Log.i(TAG, "onPostExecute " + KerplappApplication.ipAddress);
            repoSwitch.setChecked(false);
            if (wifiManager.isWifiEnabled() && KerplappApplication.ipAddress != 0) {
                setIpAddressFromWifi();
                wireRepoSwitchToWebServer();
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // ignore orientation/keyboard change
        super.onConfigurationChanged(newConfig);
    }

    class UpdateAsyncTask extends AsyncTask<Void, String, Void> {
        private static final String TAG = "UpdateAsyncTask";
        private ProgressDialog progressDialog;
        private String[] selectedApps;

        public UpdateAsyncTask(Context c, String[] apps) {
            selectedApps = apps;
            progressDialog = new ProgressDialog(c);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialog.setTitle(R.string.updating);
        }

        @Override
        protected void onPreExecute() {
            progressDialog.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                publishProgress(getString(R.string.deleting_repo));
                KerplappApplication.localRepo.deleteRepo();
                for (String app : selectedApps) {
                    publishProgress(String.format(getString(R.string.adding_apks_format), app));
                    KerplappApplication.localRepo.addApp(getApplicationContext(), app);
                }
                publishProgress(getString(R.string.writing_index_xml));
                KerplappApplication.localRepo.writeIndexXML();
                publishProgress(getString(R.string.writing_index_jar));
                KerplappApplication.localRepo.writeIndexJar();
                publishProgress(getString(R.string.linking_apks));
                KerplappApplication.localRepo.copyApksToRepo();
                publishProgress(getString(R.string.copying_icons));
                // run the icon copy without progress, its not a blocker
                new AsyncTask<Void, Void, Void>() {

                    @Override
                    protected Void doInBackground(Void... params) {
                        KerplappApplication.localRepo.copyIconsToRepo();
                        return null;
                    }
                }.execute();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... progress) {
            super.onProgressUpdate(progress);
            progressDialog.setMessage(progress[0]);
        }

        @Override
        protected void onPostExecute(Void result) {
            progressDialog.dismiss();
            Toast.makeText(getBaseContext(), R.string.updated_local_repo, Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            localRepoServiceMessenger = new Messenger(service);
            Toast.makeText(getBaseContext(), "local repo service connected",
                    Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            localRepoServiceMessenger = null;
            Toast.makeText(getBaseContext(), "local repo service disconnected",
                    Toast.LENGTH_SHORT).show();
        }
    };

    void bindService() {
        // Establish a connection with the service. We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        bindService(new Intent(this, LocalRepoService.class),
                serviceConnection, Context.BIND_AUTO_CREATE);
        localRepoServiceIsBound = true;
    }

    void unbindService() {
        if (localRepoServiceIsBound) {
            // Detach our existing connection.
            unbindService(serviceConnection);
            localRepoServiceIsBound = false;
        }
    }
}
