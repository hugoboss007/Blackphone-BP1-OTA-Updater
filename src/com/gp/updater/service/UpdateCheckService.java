package com.gp.updater.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.Arrays;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import android.content.ContentResolver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;

import com.gp.updater.OTA;
import com.gp.updater.R;
import com.gp.updater.UpdateApplication;
import com.gp.updater.misc.Constants;
import com.gp.updater.misc.State;
import com.gp.updater.misc.UpdateInfo;
import com.gp.updater.receiver.DownloadReceiver;
import com.gp.updater.utils.Utils;
import com.gp.updater.utils.BlackphoneHttpClient;

public class UpdateCheckService extends IntentService {
    private static final int FETCHER_SOCKET_TIMEOUT_MS = 10000;

    private static final String TAG = "UpdateCheckService";

    // request actions
    public static final String ACTION_CHECK = "com.gp.updater.action.CHECK";

    public static final String ACTION_CANCEL_CHECK = "com.gp.updater.action.CANCEL_CHECK";

    // broadcast actions
    public static final String ACTION_CHECK_FINISHED = "com.gp.updater.action.UPDATE_CHECK_FINISHED";
    // extra for ACTION_CHECK_FINISHED: total amount of found updates
    public static final String EXTRA_UPDATE_COUNT = "update_count";
    // extra for ACTION_CHECK_FINISHED: amount of updates that are newer than what is installed
    public static final String EXTRA_REAL_UPDATE_COUNT = "real_update_count";
    // extra for ACTION_CHECK_FINISHED: amount of updates that were found for the first time
    public static final String EXTRA_NEW_UPDATE_COUNT = "new_update_count";

    // max. number of updates listed in the expanded notification
    private static final int EXPANDED_NOTIF_UPDATE_COUNT = 4;

    // For Dynamic json name from serial mod 10
    private final String JSON_NAME = "update";
    private final String JSON_EXT  = ".json";

    public UpdateCheckService() {
        super("UpdateCheckService");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (TextUtils.equals(intent.getAction(), ACTION_CANCEL_CHECK)) {
            return START_NOT_STICKY;
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (!Utils.isOnline(this)) {
            // Only check for updates if the device is actually connected to a network
            Log.e(TAG, "Could not check for updates. Not connected to the network.");
            return;
        }

        // Start the update check
        Intent finishedIntent = new Intent(ACTION_CHECK_FINISHED);
        LinkedList<UpdateInfo> availableUpdates;
        try {
            availableUpdates = getAvailableUpdatesAndFillIntent(finishedIntent);
        } catch (IOException e) {
            Log.e(TAG, "Could not check for updates", e);
            availableUpdates = null;
        }

        if (availableUpdates == null) {
            sendBroadcast(finishedIntent);
            return;
        }

        // Store the last update check time and ensure boot check completed is true
        Date d = new Date();
        PreferenceManager.getDefaultSharedPreferences(UpdateCheckService.this).edit()
                .putLong(Constants.LAST_UPDATE_CHECK_PREF, d.getTime())
                .putBoolean(Constants.BOOT_CHECK_COMPLETED, true)
                .apply();

        int realUpdateCount = finishedIntent.getIntExtra(EXTRA_REAL_UPDATE_COUNT, 0);
        UpdateApplication app = (UpdateApplication) getApplicationContext();

        // Write to log
        /*Log.e(TAG, "The update check successfully completed at " + d + " and found "
                + availableUpdates.size() + " updates ("
                + realUpdateCount + " newer than installed)");*/

        if (realUpdateCount != 0 && !app.isMainActivityActive()) {
            // There are updates available
            // The notification should launch the main app
            Intent i = new Intent(this, OTA.class);
            i.putExtra(OTA.EXTRA_UPDATE_LIST_UPDATED, true);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, i,
                    PendingIntent.FLAG_ONE_SHOT);

            Resources res = getResources();
            String text = res.getQuantityString(R.plurals.new_updates_found_body,
                    realUpdateCount, realUpdateCount);

            // Get the notification ready
            Notification.Builder builder = new Notification.Builder(this)
                    .setSmallIcon(R.drawable.updater)
                    .setWhen(System.currentTimeMillis())
                    .setTicker(res.getString(R.string.new_updates_found_ticker))
                    .setContentTitle(res.getString(R.string.new_updates_found_title))
                    .setContentText(text)
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true);

            LinkedList<UpdateInfo> realUpdates = new LinkedList<UpdateInfo>();
            for (UpdateInfo ui : availableUpdates) {
                if (ui.isNewerThanInstalled()) {
                    realUpdates.add(ui);
                }
            }

            Collections.sort(realUpdates, new Comparator<UpdateInfo>() {
                @Override
                public int compare(UpdateInfo lhs, UpdateInfo rhs) {
                    /* sort by date descending */
                    long lhsDate = lhs.getDate();
                    long rhsDate = rhs.getDate();
                    if (lhsDate == rhsDate) {
                        return 0;
                    }
                    return lhsDate < rhsDate ? 1 : -1;
                }
            });

            Notification.InboxStyle inbox = new Notification.InboxStyle(builder)
                    .setBigContentTitle(text);
            int added = 0, count = realUpdates.size();

            for (UpdateInfo ui : realUpdates) {
                if (added < EXPANDED_NOTIF_UPDATE_COUNT) {
                    inbox.addLine(ui.getName());
                    added++;
                }
            }
            if (added != count) {
                inbox.setSummaryText(res.getQuantityString(R.plurals.not_additional_count,
                            count - added, count - added));
            }
            builder.setStyle(inbox);
            builder.setNumber(availableUpdates.size());

            if (count == 1) {
                i = new Intent(this, DownloadReceiver.class);
                i.setAction(DownloadReceiver.ACTION_START_DOWNLOAD);
                i.putExtra(DownloadReceiver.EXTRA_UPDATE_INFO, (Parcelable) realUpdates.getFirst());
                PendingIntent downloadIntent = PendingIntent.getBroadcast(this, 0, i,
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);

                builder.addAction(R.drawable.ic_tab_download,
                        res.getString(R.string.not_action_download), downloadIntent);
            }

            // Trigger the notification
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(R.string.new_updates_found_title, builder.build());
        }

        sendBroadcast(finishedIntent);
    }

    private LinkedList<UpdateInfo> getAvailableUpdatesAndFillIntent(Intent intent) throws IOException {
        // Get the type of update we should check for
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int updateType = prefs.getInt(Constants.UPDATE_TYPE_PREF, 0);

        String json = null;

		URI uri = null;
		HttpClient httpClient = null;
		HttpGet httpGet = null;
		HttpResponse httpResponse = null;

        //Read the json file with the updates information
        try {
            // Get the actual ROM Update Server URL
			String finalURLString = Utils.getFinalURL(getString(R.string.conf_update_server_url_def));

			// Then, if we have the beta tool, ignore all that and just use what beta gives us
			try {
				String betaProvider = "ch.blackphone.android.blackphonebeta";

				// Signature based permissions protect the content provider from us, but we need to protect ourselves from the content provider
				PackageManager pm = getPackageManager();

				ProviderInfo pi = pm.resolveContentProvider(betaProvider, 0);

				// Throw anything w/out a provider info
				if (pi == null) {
					throw new IllegalArgumentException("expected provider info");
				}

				PackageInfo selfInfo = pm.getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
				PackageInfo provInfo = pm.getPackageInfo(pi.packageName, PackageManager.GET_SIGNATURES);

				if (selfInfo.signatures.length != provInfo.signatures.length)
					throw new IllegalArgumentException("expected matching sigs");

				for (int s = 0; s < selfInfo.signatures.length; s++) {
					if (!Arrays.equals(selfInfo.signatures[s].toByteArray(), provInfo.signatures[s].toByteArray())) {
						throw new IllegalArgumentException("expected matching sigs");
					}
				}

				Uri contentUri = Uri.parse("content://" + betaProvider + "/ota");
				ContentResolver resolver = getContentResolver();
				Cursor c = resolver.query(contentUri, null, null, null, null);

				if (c != null) {
					c.moveToFirst();
					finalURLString = c.getString(0);
				}
			} catch (IllegalArgumentException iae) {
				// Nothing intelligent to do here
			} catch (PackageManager.NameNotFoundException nnfe) {
				// Nothing intelligent to do here
			}

			// We want to have different updates for different serials (progressive updates).
			String partURL = JSON_NAME + (new BigInteger(Utils.getSerial(), 16).mod(new BigInteger("10")).intValue()) + JSON_EXT;

			uri = new URI(finalURLString + partURL);

			httpClient = new BlackphoneHttpClient(this);
			HttpProtocolParams.setVersion(httpClient.getParams(), HttpVersion.HTTP_1_1);
			HttpProtocolParams.setContentCharset(httpClient.getParams(), HTTP.UTF_8);
			HttpConnectionParams.setConnectionTimeout(httpClient.getParams(), FETCHER_SOCKET_TIMEOUT_MS);

			httpGet = new HttpGet(uri);

			httpResponse = httpClient.execute(httpGet);

			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				// gets the server json data
				// BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
				BufferedReader in = new BufferedReader(new InputStreamReader(httpResponse.getEntity().getContent()));

				StringBuilder responseText = new StringBuilder();
				String line;
				while( (line = in.readLine()) != null) {
					responseText.append(line);
				}

				in.close();

				json = responseText.toString();
			}
		} catch (URISyntaxException mue) {
			// Nothing intelligent to do here
		} catch (SSLHandshakeException she) {
			// SSL error, do nothing intelligent
		} catch (SSLPeerUnverifiedException pue) {
			// SSL error, do nothing intelligent
		} catch (IllegalStateException ise) {
			// https got kluged to http due to broken proxy of some sort
		} catch (IOException ioe) {
            // General http failure
		}

        if(json == null){
            Log.e(TAG, "The updates JSON is null, try to download again");
            return null;
        }

        LinkedList<UpdateInfo> lastUpdates = State.loadState(this);

        // Read the ROM Infos
		//Log.e(TAG, "JSON: " + json);
        LinkedList<UpdateInfo> updates = parseJSON(json, updateType);

        int newUpdates = 0, realUpdates = 0;
        for (UpdateInfo ui : updates) {
            if (!lastUpdates.contains(ui)) {
                newUpdates++;
            }
            if (ui.isNewerThanInstalled()) {
                realUpdates++;
            }
        }

        intent.putExtra(EXTRA_UPDATE_COUNT, updates.size());
        intent.putExtra(EXTRA_REAL_UPDATE_COUNT, realUpdates);
        intent.putExtra(EXTRA_NEW_UPDATE_COUNT, newUpdates);

        State.saveState(this, updates);

        return updates;
    }

    private LinkedList<UpdateInfo> parseJSON(String jsonString, int updateType) {
        LinkedList<UpdateInfo> updates = new LinkedList<UpdateInfo>();
        try {
            JSONObject result = new JSONObject(jsonString);
            JSONArray updateList = result.getJSONArray("result");
            int length = updateList.length();

            //Log.e(TAG, "Got update JSON data with " + length + " entries");

            for (int i = 0; i < length; i++) {
                if (updateList.isNull(i)) {
                    continue;
                }
                JSONObject item = updateList.getJSONObject(i);
                UpdateInfo info = parseUpdateJSONObject(item, updateType);
                if (info != null) {
                    updates.add(info);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error in JSON result", e);
        }
        return updates;
    }

    private UpdateInfo parseUpdateJSONObject(JSONObject obj, int updateType) throws JSONException {
        String fileName = obj.getString("filename");
        String displayName = obj.getString("displayname");
        String url = obj.getString("url");
        String md5 = obj.getString("md5sum");
        int apiLevel = obj.getInt("api_level");
        long timestamp = obj.getLong("timestamp");
        String typeString = obj.getString("channel");
        UpdateInfo.Type type;

        if (TextUtils.equals(typeString, "stable")) {
            type = UpdateInfo.Type.STABLE;
        } else if (TextUtils.equals(typeString, "nightly")) {
            type = UpdateInfo.Type.NIGHTLY;
        } else {
            type = UpdateInfo.Type.UNKNOWN;
        }

        UpdateInfo ui = new UpdateInfo(fileName, displayName, timestamp, apiLevel, url, md5, type);
        boolean includeAll = updateType == Constants.UPDATE_TYPE_ALL_STABLE ||
        					 updateType == Constants.UPDATE_TYPE_ALL_NIGHTLY;

        if (!includeAll && !ui.isNewerThanInstalled()) {
            return null;
        }

        // Check the updateType to ensure that the list is completed correctly (We only support stable and nightly channels).
        int restrictedType = 0;
        switch (updateType) {
            case 0:  restrictedType = UpdateInfo.Type.STABLE.ordinal();
                     break;
            case 1:  restrictedType = UpdateInfo.Type.STABLE.ordinal();
                     break;
            case 2:  restrictedType = UpdateInfo.Type.NIGHTLY.ordinal();
                     break;
            case 3:  restrictedType = UpdateInfo.Type.NIGHTLY.ordinal();
                     break;
            default: restrictedType = UpdateInfo.Type.UNKNOWN.ordinal();
                     break;
        }
        if(ui.getType().ordinal() != restrictedType)
            return null;

        // fetch change log after checking whether to include this build to
        // avoid useless network traffic
        if (!ui.getChangeLogFile(this).exists()) {
            fetchChangeLog(ui, obj.getString("changes"));
        }

        return ui;
    }

    private void fetchChangeLog(UpdateInfo info, String url) {
        //Log.e(TAG, "Getting change log for " + info + ", url " + url);

        BufferedWriter writer = null;
        BufferedReader reader = null;
        boolean finished = false;

		URI uri = null;
		HttpClient httpClient = null;
		HttpGet httpGet = null;
		HttpResponse httpResponse = null;

        try {
        	writer = new BufferedWriter(new FileWriter(info.getChangeLogFile(this)));

			uri = new URI(url);

			httpClient = new BlackphoneHttpClient(this);
			HttpProtocolParams.setVersion(httpClient.getParams(), HttpVersion.HTTP_1_1);
			HttpProtocolParams.setContentCharset(httpClient.getParams(), HTTP.UTF_8);
			HttpConnectionParams.setConnectionTimeout(httpClient.getParams(), FETCHER_SOCKET_TIMEOUT_MS);

			httpGet = new HttpGet(uri);

			httpResponse = httpClient.execute(httpGet);

			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				// gets the server changelog data
				try{
					reader = new BufferedReader(new InputStreamReader(httpResponse.getEntity().getContent()));
				} catch (IOException e) {
					//Log.e(TAG, "Downloading change log for " + info + " failed, the file " + url + " not exists on server");
					reader = null;
				}

				if (reader != null){
					boolean categoryMatch = false, hasData = false;
					String line;

					while ((line = reader.readLine()) != null) {
						line = line.trim();

						if (line.isEmpty()) {
							continue;
						}

						if (line.startsWith("=")) {
							categoryMatch = !categoryMatch;
						} else if (categoryMatch) {
							if (hasData) {
								writer.append("<br />");
							}
							writer.append("<b><u>");
							writer.append(line);
							writer.append("</u></b>");
							writer.append("<br />");
							hasData = true;
						} else if (line.startsWith("*")) {
							writer.append("<br /><b>");
							writer.append(line.replaceAll("\\*", ""));
							writer.append("</b>");
							writer.append("<br />");
							hasData = true;
						} else {
							writer.append("&#8226;&nbsp;");
							writer.append(line);
							writer.append("<br />");
							hasData = true;
						}
					}
				} else {
					writer.write("");
				}
			}

        finished = true;
	} catch (URISyntaxException mue) {
		// Nothing intelligent to do here
	} catch (SSLHandshakeException she) {
		// SSL error, do nothing intelligent
	} catch (SSLPeerUnverifiedException pue) {
		// SSL error, do nothing intelligent
	} catch (IllegalStateException ise) {
		// https got kluged to http due to broken proxy of some sort
    } catch (IOException e) {
        //Log.e(TAG, "Downloading change log for " + info + " failed", e);
        // keeping finished at false will delete the partially written file below
    } finally {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                // ignore, not much we can do anyway
            }
        }
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                // ignore, not much we can do anyway
            }
        }
    }

        if (!finished) {
            info.getChangeLogFile(this).delete();
        }
    }
}
