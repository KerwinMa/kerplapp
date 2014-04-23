package org.fdroid.fdroid.data;

import android.content.ContentValues;
import android.database.Cursor;
import org.fdroid.fdroid.Utils;

import java.io.File;
import java.util.Date;

public class Apk extends ValueObject implements Comparable<Apk> {
    public File file;

    public String id;
    public String version;
    public int vercode;
    public int size; // Size in bytes - 0 means we don't know!
    public long repo; // ID of the repo it comes from
    public String hash;
    public String hashType;
    public int minSdkVersion; // 0 if unknown
    public int maxSdkVersion; // 0 if none
    public Date added;
    public Utils.CommaSeparatedList permissions; // null if empty or
    // unknown
    public Utils.CommaSeparatedList features; // null if empty or unknown

    public Utils.CommaSeparatedList nativecode; // null if empty or unknown

    // ID (md5 sum of public key) of signature. Might be null, in the
    // transition to this field existing.
    public String sig;

    // True if compatible with the device.
    public boolean compatible;

    public String apkSourcePath; // the original path to the APK
    public String apkSourceName; // the original name of the APK
    public String apkName; // F-Droid style APK name

    // If not null, this is the name of the source tarball for the
    // application. Null indicates that it's a developer's binary
    // build - otherwise it's built from source.
    public String srcname;

    // Used internally for tracking during repo updates.
    public boolean updated;

    public int repoVersion;
    public String repoAddress;
    public Utils.CommaSeparatedList incompatible_reasons;

    public Apk() {
        updated = false;
        size = 0;
        added = null;
        repo = 0;
        hash = null;
        hashType = null;
        permissions = null;
        compatible = false;
    }

    @Override
    public int compareTo(Apk apk) {
        return Integer.valueOf(vercode).compareTo(apk.vercode);
    }

}
