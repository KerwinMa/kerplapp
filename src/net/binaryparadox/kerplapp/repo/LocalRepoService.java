
package net.binaryparadox.kerplapp.repo;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import net.binaryparadox.kerplapp.KerplappActivity;
import net.binaryparadox.kerplapp.KerplappApplication;
import net.binaryparadox.kerplapp.KerplappKeyStore;
import net.binaryparadox.kerplapp.R;
import net.binaryparadox.kerplapp.network.KerplappHTTPD;

import java.io.IOException;

public class LocalRepoService extends Service {
    private static final String TAG = "LocalRepoService";

    private NotificationManager notificationManager;
    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private int NOTIFICATION = R.string.local_repo_running;

    private Thread webServerThread = null;
    private Handler handler = null;

    public class LocalRepoBinder extends Binder {
        public LocalRepoService getService() {
            return LocalRepoService.this;
        }
    }

    @Override
    public void onCreate() {
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        showNotification();
        startWebServer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopWebServer();
        notificationManager.cancel(NOTIFICATION);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // This is the object that receives interactions from clients. See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalRepoBinder();

    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        // launch KerplappActivity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, KerplappActivity.class), 0);
        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(getText(R.string.local_repo_running))
                .setContentText(getText(R.string.touch_to_configure_local_repo))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(contentIntent)
                .build();
        notificationManager.notify(NOTIFICATION, notification);
    }

    private void startWebServer() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean useHttps = prefs.getBoolean("use_https", false);

        Runnable webServer = new Runnable() {
            // Tell Eclipse this is not a leak because of Looper use.
            @SuppressLint("HandlerLeak")
            @Override
            public void run() {
                Log.i(TAG, "run");
                final KerplappHTTPD kerplappHttpd = new KerplappHTTPD(
                        KerplappApplication.ipAddressString,
                        KerplappApplication.port, getFilesDir(), false);

                if (useHttps)
                {
                    KerplappApplication appCtx = (KerplappApplication) getApplication();
                    KerplappKeyStore keyStore = appCtx.getKeyStore();
                    kerplappHttpd.enableHTTPS(keyStore);
                }

                Looper.prepare(); // must be run before creating a Handler
                handler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        // the only message this Thread responds to is STOP!
                        Log.i(TAG, "we've been asked to stop the webserver: " + msg.obj);
                        kerplappHttpd.stop();
                    }
                };
                try {
                    kerplappHttpd.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Looper.loop(); // start the message receiving loop
            }
        };
        webServerThread = new Thread(webServer);
        webServerThread.start();
    }

    private void stopWebServer() {
        if (handler == null) {
            Log.i(TAG, "null handler in stopWebServer");
            return;
        }
        Message msg = handler.obtainMessage();
        msg.obj = handler.getLooper().getThread().getName() + " says stop";
        handler.sendMessage(msg);
    }
}
