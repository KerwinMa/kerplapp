
package net.binaryparadox.kerplapp;

import android.app.Application;
import android.content.Context;

import net.binaryparadox.kerplapp.repo.LocalRepo;

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

    // the local repo on this device
    public static int ipAddress = 0;
    public static int port = 8888;
    public static String ipAddressString = null;
    public static LocalRepo localRepo = null;
    public static LocalRepoKeyStore localRepoKeyStore = null;
    static Set<String> selectedApps = new HashSet<String>();

    @Override
    public void onCreate() {
        super.onCreate();

        // Apply the Google PRNG fixes to properly seed SecureRandom
        PRNGFixes.apply();

        File appKeyStoreDir = getDir(keyStoreDirName, Context.MODE_PRIVATE);
        File keyStoreFile = new File(appKeyStoreDir, keyStoreFileName);

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
    }
}
