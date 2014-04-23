package org.fdroid.fdroid.data;

import android.content.pm.ApplicationInfo;

import org.fdroid.fdroid.Utils;

import java.util.Date;
import java.util.List;

public class App extends ValueObject implements Comparable<App> {

    // True if compatible with the device (i.e. if at least one apk is)
    public boolean compatible;
    public boolean includeInRepo = false;

    public String id = "unknown";
    public String name = "Unknown";
    public String summary = "Unknown application";
    public String icon;

    public String description;

    public String license = "Unknown";

    public String webURL;

    public String trackerURL;

    public String sourceURL;

    public String donateURL;

    public String bitcoinAddr;

    public String litecoinAddr;

    public String dogecoinAddr;

    public String flattrID;

    public String upstreamVersionName;
    public int upstreamVersionCode;

    /**
     * Unlike other public fields, this is only accessible via a getter, to
     * emphasise that setting it wont do anything. In order to change this,
     * you need to change suggestedVercode to an apk which is in the apk table.
     */
    private String suggestedVersionName;
    
    public int suggestedVersionCode;

    public Date added;
    public Date lastUpdated;

    // List of categories (as defined in the metadata
    // documentation) or null if there aren't any.
    public Utils.CommaSeparatedList categories;

    // List of anti-features (as defined in the metadata
    // documentation) or null if there aren't any.
    public Utils.CommaSeparatedList antiFeatures;

    // List of special requirements (such as root privileges) or
    // null if there aren't any.
    public Utils.CommaSeparatedList requirements;

    // True if all updates for this app are to be ignored
    public boolean ignoreAllUpdates;

    // True if the current update for this app is to be ignored
    public int ignoreThisUpdate;

    // Used internally for tracking during repo updates.
    public boolean updated;

    public String iconUrl;

    public String installedVersionName;

    public int installedVersionCode;

    public ApplicationInfo appInfo;
    public List<Apk> apks;

    @Override
    public int compareTo(App app) {
        return name.compareToIgnoreCase(app.name);
    }

    public App() {

    }
}
