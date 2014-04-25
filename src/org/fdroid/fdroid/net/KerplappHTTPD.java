
package org.fdroid.fdroid.net;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.SimpleWebServer;

import net.binaryparadox.kerplapp.FDroidApp;

import java.io.File;
import java.io.IOException;

import javax.net.ssl.SSLServerSocketFactory;

public class KerplappHTTPD extends SimpleWebServer {
    private static final String TAG = "KerplappHTTPD";

    public KerplappHTTPD(File wwwroot, boolean quiet) {
        super(FDroidApp.ipAddressString, FDroidApp.port, wwwroot, quiet);
    }

    public void enableHTTPS() {
        try {
            SSLServerSocketFactory factory = NanoHTTPD.makeSSLSocketFactory(
                    FDroidApp.localRepoKeyStore.getKeyStore(),
                    FDroidApp.localRepoKeyStore.getKeyManagers());
            makeSecure(factory);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
