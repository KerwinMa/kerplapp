
package net.binaryparadox.kerplapp;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import net.binaryparadox.kerplapp.network.WifiStateChangeService;
import net.binaryparadox.kerplapp.repo.LocalRepo;
import net.binaryparadox.kerplapp.repo.LocalRepoKeyStore;
import net.binaryparadox.kerplapp.repo.LocalRepoService;

import org.fdroid.fdroid.data.Repo;
import org.spongycastle.operator.OperatorCreationException;

import java.io.File;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.HashSet;
import java.util.Set;

public class KerplappApplication extends Application {
    private static final String TAG = "KerplappApplication";
    private static final String keyStoreDirName = "keystore";
    private static final String keyStoreFileName = "kerplapp.bks";

    // for the local repo on this device, all static since there is only one
    public static int ipAddress = 0;
    public static int port = 8888;
    public static String ipAddressString = null;
    public static String ssid = "";
    public static String bssid = "";
    public static Repo repo = new Repo();
    public static LocalRepo localRepo = null;
    public static LocalRepoKeyStore localRepoKeyStore = null;
    static Set<String> selectedApps = new HashSet<String>();

    private static Context serviceContext;
    private static Messenger localRepoServiceMessenger = null;
    private static boolean localRepoServiceIsBound = false;

    @Override
    public void onCreate() {
        super.onCreate();

        // Apply the Google PRNG fixes to properly seed SecureRandom
        PRNGFixes.apply();

        File appKeyStoreDir = getDir(keyStoreDirName, Context.MODE_PRIVATE);
        File keyStoreFile = new File(appKeyStoreDir, keyStoreFileName);

        serviceContext = this;

        if (localRepo == null) {
            localRepo = new LocalRepo(getApplicationContext());

            try {
                localRepo.init();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (localRepoKeyStore == null) {
            try {
                localRepoKeyStore = new LocalRepoKeyStore(keyStoreFile);
            } catch (UnrecoverableKeyException e) {
                e.printStackTrace();
            } catch (KeyStoreException e) {
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (CertificateException e) {
                e.printStackTrace();
            } catch (OperatorCreationException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // initialized the local repo information
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        int wifiState = wifiManager.getWifiState();
        if (wifiState == WifiManager.WIFI_STATE_ENABLING
                || wifiState == WifiManager.WIFI_STATE_ENABLED)
            startService(new Intent(this, WifiStateChangeService.class));
    }

    private static ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            localRepoServiceMessenger = new Messenger(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            localRepoServiceMessenger = null;
        }
    };

    public static void startLocalRepoService() {
        serviceContext.bindService(new Intent(serviceContext, LocalRepoService.class),
                serviceConnection, Context.BIND_AUTO_CREATE);
        localRepoServiceIsBound = true;
    }

    public static void stopLocalRepoService() {
        if (localRepoServiceIsBound) {
            serviceContext.unbindService(serviceConnection);
            localRepoServiceIsBound = false;
        }
    }

    public static void restartLocalRepoService() {
        if (localRepoServiceMessenger != null) {
            try {
                Message msg = Message.obtain(null,
                        LocalRepoService.RESTART, LocalRepoService.RESTART, 0);
                localRepoServiceMessenger.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
}
