package com.gp.updater.utils;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Base64InputStream;
import android.util.Base64OutputStream;
import android.util.Log;
import android.text.TextUtils;

import com.gp.updater.R;
import com.gp.updater.Updater;
import com.gp.updater.OTA;
import com.gp.updater.misc.Constants;
import com.gp.updater.service.UpdateCheckService;
import com.gp.updater.tasks.UpdatePreparationTask;
import android.app.ProgressDialog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.io.Serializable;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import java.io.FilenameFilter;

public class Utils {
    private Utils() {
        // this class is not supposed to be instantiated
    }

    public static File getUpdateFolder() {
        return new File(Environment.getExternalStorageDirectory().getAbsolutePath(), Constants.UPDATES_FOLDER);
    }
  
    public static File cleanUpdateFolder() {
        File updateFolder = Utils.getUpdateFolder();

        if ((updateFolder.exists()) && (updateFolder.isDirectory()) && (updateFolder.listFiles().length > 0)) {
            // Remove update folder if it exists to not accumulate downloads in order to save space
            Utils.deleteDir(updateFolder);
            // Now create an empty update folder
            updateFolder = getUpdateFolder();
        }

        return updateFolder;
    }

    public static void cancelNotification(Context context) {
        final NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(R.string.new_updates_found_title);
        nm.cancel(R.string.not_download_success);
    }
	
	public static String getSerial() {
		return SystemProperties.get("ro.serialno");
	}

	public static String getFinalURL(String urlDef) {
		// Get the resource pointer to our region based on build.prop version
		String customVersion = SystemProperties.get("ro.custom.build.version");
		String regionVersion = null;
		String finalURLString = null;

		// We need to split to position 3 to know the region (example: PrivatOS 1.0.5b1 US)
		String[] arrCustomVersion = customVersion.split(" ");

		// Check the region
		if(arrCustomVersion.length >= 3)
			regionVersion = arrCustomVersion[2].replace("EU","");
		else
			regionVersion = "";

		// Check the URL to know if we need to add the region or the URL is correctly added.
		String[] arrUrlDef = urlDef.split("/");

		if(!arrUrlDef[arrUrlDef.length - 1].replace("EU","").equals(regionVersion)){
			finalURLString = urlDef + regionVersion + "/";
		}else{
			finalURLString = urlDef;
		}

		return finalURLString;
	}

    public static String getDeviceType() {
        return SystemProperties.get("ro.product.manufacturer") + " " + SystemProperties.get("ro.product.device");
    }

    public static String getInstalledVersion() {
        return SystemProperties.get("ro.product.device") + "-" + SystemProperties.get("ro.build.version.release") +
        	   "-" + SystemProperties.get("ro.build.date.utc");
    }

    public static int getInstalledApiLevel() {
        return SystemProperties.getInt("ro.build.version.sdk", 0);
    }

    public static long getInstalledBuildDate() {
        return SystemProperties.getLong("ro.build.date.utc", 0);
    }

    public static String getBuildType() {
        return SystemProperties.get("ro.build.type", "eng");
    }

    public static String getLocale() {
        return SystemProperties.get("persist.sys.language", "en") + "_" +SystemProperties.get("persist.sys.country", "US");
    }

    public static String getUserAgentString(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            return pi.packageName + "/" + pi.versionName;
        } catch (PackageManager.NameNotFoundException nnfe) {
            return null;
        }
    }

    public static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnected()) {
            return true;
        }
        return false;
    }

    public static void scheduleUpdateService(Context context, int updateFrequency) {
        // Load the required settings from preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        long lastCheck = prefs.getLong(Constants.LAST_UPDATE_CHECK_PREF, 0);

        // Get the intent ready
        Intent i = new Intent(context, UpdateCheckService.class);
        i.setAction(UpdateCheckService.ACTION_CHECK);
        PendingIntent pi = PendingIntent.getService(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);

        // Clear any old alarms and schedule the new alarm
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);

        if (updateFrequency != Constants.UPDATE_FREQ_NONE) {
            am.setRepeating(AlarmManager.RTC_WAKEUP, lastCheck + updateFrequency, updateFrequency, pi);
        }
    }

	public static void triggerUpdate(Context context, String updateFileName) throws IOException {
        // Set the 'boot recovery' command
		List<String> commands = new ArrayList<String>();
        commands.add("mkdir -p /cache/recovery/");
        commands.add("echo 'boot-recovery' >/cache/recovery/command");

        String srcFile = Environment.getExternalStorageDirectory().getAbsolutePath() + "/"
            			 + Constants.UPDATES_FOLDER + "/" + updateFileName;
        String dstPath = "/cache/" + Constants.UPDATES_FOLDER;

        commands.add("rm -rf " + dstPath);
        commands.add("mkdir -p " + dstPath);
        commands.add("cp " + srcFile + " " + dstPath);

        String updatePackage = dstPath + "/" + updateFileName;

        commands.add("echo '--update_package=" + updatePackage + "' >> /cache/recovery/command");
        commands.add("echo '--locale=" + getLocale() + "' >> /cache/recovery/command");

        UpdatePreparationTask task = new UpdatePreparationTask((Updater) context, commands, context);
		task.execute((Void) null);
	}

    public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (String aChildren : children) {
                boolean success = deleteDir(new File(dir, aChildren));
                if (!success) {
                    return false;
                }
            }
        }
        // The directory is now empty so delete it
        return dir.delete();
    }

    public static boolean deletePartials(File dir) {
        if ((dir == null) || (!dir.exists()) || (!dir.isDirectory())) return false;

        File[] partials = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(final File dir, final String name) {
                return name.matches("blackphone-*\\.partial");
            }
        });

        for (File partial : partials) {
            if (!partial.delete()) return false;
        }

        return true;
    }

    public static String readFromFile(Context context, File file) {
        if ((context == null) || (file == null)) return "";

        FileInputStream fis = null;
        StringBuffer fileContent = null;
        try {
            fis = new FileInputStream(file);
            fileContent = new StringBuffer("");

            byte[] buffer = new byte[1024];

            int n;
            while ((n = fis.read(buffer)) != -1) {
                fileContent.append(new String(buffer, 0, n));
            }
            if (fis != null) fis.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } 

        return (TextUtils.isEmpty(fileContent)) ? "" : fileContent.toString();
    }
}
