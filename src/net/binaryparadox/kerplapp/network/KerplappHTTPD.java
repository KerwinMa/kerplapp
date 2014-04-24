
package net.binaryparadox.kerplapp.network;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.SimpleWebServer;

import net.binaryparadox.kerplapp.KerplappApplication;

import java.io.File;
import java.io.IOException;

import javax.net.ssl.SSLServerSocketFactory;

public class KerplappHTTPD extends SimpleWebServer {
    private static final String TAG = "KerplappHTTPD";

    public KerplappHTTPD(File wwwroot, boolean quiet) {
        super(KerplappApplication.ipAddressString, KerplappApplication.port, wwwroot, quiet);
    }

    public void enableHTTPS() {
        try {
            SSLServerSocketFactory factory = NanoHTTPD.makeSSLSocketFactory(
                    KerplappApplication.localRepoKeyStore.getKeyStore(),
                    KerplappApplication.localRepoKeyStore.getKeyManagers());
            makeSecure(factory);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
