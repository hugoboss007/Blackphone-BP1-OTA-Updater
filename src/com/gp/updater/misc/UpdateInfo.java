package com.gp.updater.misc;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.gp.updater.utils.Utils;

import java.io.File;
import java.io.Serializable;

public class UpdateInfo implements Parcelable, Serializable {
    private static final long serialVersionUID = 5499890003569313403L;

    public enum Type {
        UNKNOWN,
        STABLE,
        NIGHTLY
    };

    private String mUiName;
    private String mFileName;
    private Type mType;
    private int mApiLevel;

	private long mBuildDate;
    private String mDownloadUrl;
    private String mMd5Sum;

    private Boolean mIsNewerThanInstalled;

    public UpdateInfo(String fileName, String displayName, long date, int apiLevel, String url,
            String md5, Type type) {
        initializeName(fileName, displayName);
        mBuildDate = date;
        mApiLevel = apiLevel;
        mDownloadUrl = url;
        mMd5Sum = md5;
        mType = type;
    }

    private UpdateInfo(Parcel in) {
        readFromParcel(in);
    }

    public File getChangeLogFile(Context context) {
        return new File(context.getCacheDir(), mFileName + ".changelog");
    }

    /**
     * Get name for UI display
     */
    public String getName() {
        return mUiName;
    }

    /**
     * Get file name
     */
    public String getFileName() {
        return mFileName;
    }

    /**
     * Get build type
     */
    public Type getType() {
        return mType;
    }

   /**
     * Get MD5
     */
    public String getMD5Sum() {
        return mMd5Sum;
    }

    /**
     * Get build date
     */
    public long getDate() {
        return mBuildDate;
    }

    /**
     * Check if the API Level is the same
     */
    public boolean chkAPILevel() {
    	int installedApiLevel = Utils.getInstalledApiLevel();
		return mApiLevel == installedApiLevel;
	}
    
    /**
     * Get download location
     */
    public String getDownloadUrl() {
        return mDownloadUrl;
    }
    
    public void setDownloadUrl(String url) {
        this.mDownloadUrl = url;
    }

    public boolean isDifferentOS() {
        return mApiLevel == 999;
    }

    public boolean isNewerThanInstalled() {
        if (mIsNewerThanInstalled != null) {
            return mIsNewerThanInstalled;
        }

        int installedApiLevel = Utils.getInstalledApiLevel();
        if (installedApiLevel != mApiLevel && mApiLevel > 0) {
            mIsNewerThanInstalled = (mApiLevel > installedApiLevel && mApiLevel != 999);
        } else {
            // API levels match, so compare build dates.
            mIsNewerThanInstalled = mBuildDate > Utils.getInstalledBuildDate();
        }

        return mIsNewerThanInstalled;
    }

    private void initializeName(String fileName, String displayName) {
        mFileName = fileName;
        mUiName = displayName;
        if (mUiName == null)
            if (!TextUtils.isEmpty(fileName)) {
                mUiName = extractUiName(fileName);
            } else {
                mUiName = null;
            }
    }

    public static String extractUiName(String fileName) {
        String deviceType = Utils.getDeviceType();
        String uiName = fileName.replaceAll("\\.zip$", "");
        return uiName.replaceAll("-" + deviceType + "-?", "");
    }

    @Override
    public String toString() {
        return "UpdateInfo: " + mFileName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof UpdateInfo)) {
            return false;
        }

        UpdateInfo ui = (UpdateInfo) o;
        return TextUtils.equals(mFileName, ui.mFileName)
                && mType.equals(ui.mType)
                && mBuildDate == ui.mBuildDate
                && TextUtils.equals(mDownloadUrl, ui.mDownloadUrl)
                && TextUtils.equals(mMd5Sum, ui.mMd5Sum);
    }

    public static final Parcelable.Creator<UpdateInfo> CREATOR = new Parcelable.Creator<UpdateInfo>() {
        public UpdateInfo createFromParcel(Parcel in) {
            return new UpdateInfo(in);
        }

        public UpdateInfo[] newArray(int size) {
            return new UpdateInfo[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mUiName);
        out.writeString(mFileName);
        out.writeString(mType.toString());
        out.writeInt(mApiLevel);
        out.writeLong(mBuildDate);
        out.writeString(mDownloadUrl);
        out.writeString(mMd5Sum);
    }

    private void readFromParcel(Parcel in) {
        mUiName = in.readString();
        mFileName = in.readString();
        mType = Enum.valueOf(Type.class, in.readString());
        mApiLevel = in.readInt();
        mBuildDate = in.readLong();
        mDownloadUrl = in.readString();
        mMd5Sum = in.readString();
    }
}

