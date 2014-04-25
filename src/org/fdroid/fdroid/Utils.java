
package org.fdroid.fdroid;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.encode.Contents;
import com.google.zxing.encode.QRCodeEncoder;

import org.fdroid.fdroid.data.Repo;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * The UAppIDUtils class provides static utility methods for calculating
 * application UAppID's. Provided a binary file or a package name this class is
 * capable of creating a UAppID returned as a string literal.
 */
public class Utils {
    private static final String TAG = "Utils";

    public static String hashBytes(byte[] input, String algo) {
        try {
            MessageDigest md = MessageDigest.getInstance(algo);
            byte[] hashBytes = md.digest(input);
            String hash = toHexString(hashBytes);

            md.reset();
            return hash;
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Device does not support " + algo + " MessageDisgest algorithm");
            return null;
        }
    }

    public static String getBinaryHash(File apk, String algo) {
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        try {
            MessageDigest md = MessageDigest.getInstance(algo);
            fis = new FileInputStream(apk);
            bis = new BufferedInputStream(fis);

            byte[] dataBytes = new byte[524288];
            int nread = 0;

            while ((nread = bis.read(dataBytes)) != -1)
                md.update(dataBytes, 0, nread);

            byte[] mdbytes = md.digest();
            return toHexString(mdbytes);
        } catch (IOException e) {
            Log.e(TAG, "Error reading \"" + apk.getAbsolutePath() + "\" to compute SHA1 hash.");
            return null;
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Device does not support " + algo + " MessageDisgest algorithm");
            return null;
        } finally {
            if (fis != null)
                try {
                    fis.close();
                } catch (IOException e) {
                    return null;
                }
        }
    }

    /**
     * Computes the base 16 representation of the byte array argument.
     *
     * @param bytes an array of bytes.
     * @return the bytes represented as a string of hexadecimal digits.
     */
    public static String toHexString(byte[] bytes) {
        BigInteger bi = new BigInteger(1, bytes);
        return String.format("%0" + (bytes.length << 1) + "X", bi);
    }

    @SuppressWarnings("deprecation")
    @TargetApi(13)
    public static Bitmap generateQrCode(Activity activity, String qrData) {
        Display display = activity.getWindowManager().getDefaultDisplay();
        Point outSize = new Point();
        int x, y, qrCodeDimension;
        /* lame, got to use both the new and old APIs here */
        if (Build.VERSION.SDK_INT > 12) {
            display.getSize(outSize);
            x = outSize.x;
            y = outSize.y;
        } else {
            x = display.getWidth();
            y = display.getHeight();
        }
        if (outSize.x < outSize.y)
            qrCodeDimension = x;
        else
            qrCodeDimension = y;
        Log.i(TAG, "generating QRCode Bitmap of " + qrCodeDimension + "x" + qrCodeDimension);
        QRCodeEncoder qrCodeEncoder = new QRCodeEncoder(qrData, null,
                Contents.Type.TEXT, BarcodeFormat.QR_CODE.toString(), qrCodeDimension);

        try {
            return qrCodeEncoder.encodeAsBitmap();
        } catch (WriterException e) {
            Log.e(TAG, e.getMessage());
        }
        return null;
    }

    public static Uri getSharingUri(Context context, Repo repo) {
        Uri uri = Uri.parse(repo.address.replaceFirst("http", "fdroidrepo"));
        Uri.Builder b = uri.buildUpon();
        b.appendQueryParameter("fingerprint", repo.fingerprint);
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        String ssid = wifiInfo.getSSID().replaceAll("^\"(.*)\"$", "$1");
        String bssid = wifiInfo.getBSSID();
        if (!TextUtils.isEmpty(bssid)) {
            b.appendQueryParameter("bssid", Uri.encode(bssid));
            if (!TextUtils.isEmpty(ssid))
                b.appendQueryParameter("ssid", Uri.encode(ssid));
        }
        return b.build();
    }

    /* this stuff is already included in FDroid */
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd",
            Locale.ENGLISH);

    public static class CommaSeparatedList implements Iterable<String> {
        private String value;

        private CommaSeparatedList(String list) {
            value = list;
        }

        public static CommaSeparatedList make(List<String> list) {
            if (list == null || list.size() == 0)
                return null;
            else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(list.get(i));
                }
                return new CommaSeparatedList(sb.toString());
            }
        }

        public static CommaSeparatedList make(String list) {
            if (list == null || list.length() == 0)
                return null;
            else
                return new CommaSeparatedList(list);
        }

        public static String str(CommaSeparatedList instance) {
            return (instance == null ? null : instance.toString());
        }

        @Override
        public String toString() {
            return value;
        }

        public String toPrettyString() {
            return value.replaceAll(",", ", ");
        }

        @Override
        public Iterator<String> iterator() {
            TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(',');
            splitter.setString(value);
            return splitter.iterator();
        }

        public boolean contains(String v) {
            for (String s : this) {
                if (s.equals(v))
                    return true;
            }
            return false;
        }
    }
}
