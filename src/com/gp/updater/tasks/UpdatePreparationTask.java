
package com.gp.updater.tasks;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.gp.updater.R;
import com.gp.updater.Updater;

public class UpdatePreparationTask extends AsyncTask<Void, Void, Void> {
    private static final String TAG = "UpdatePreparationTask";

    private final ProgressDialog mProgressDialog;
    private final Updater mParent;
    private final List<String> mCommands;
	private final Context mContext;
    private boolean mPreparationFailed;

    public UpdatePreparationTask(Updater parent, List<String> commands, Context context) {
        mParent = parent;
        mCommands = commands;
		mContext = context;
        mPreparationFailed = true;   
        mProgressDialog = new ProgressDialog(mParent);  
        mProgressDialog.setTitle(R.string.apply_preparing_the_update);
        mProgressDialog.setMessage(mParent.getResources().getString(R.string.apply_preparing_the_update));
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCancelable(false);
    }

    @Override
    protected void onPreExecute() {
        mProgressDialog.show();
    }

    @Override
    protected Void doInBackground(Void... arg0) {
        try {
            // Run the commands using sh
            Process p = Runtime.getRuntime().exec("sh");
            OutputStream os = p.getOutputStream();
            for(String command : mCommands) {
                command = command + "\n";
                os.write(command.getBytes());
            }
            os.close();

            // Wait for the sh process to finish; this could take awhile if there
            // are any long running commands like 'cp'
            try {
                p.waitFor();
            } catch (InterruptedException e) {
                Log.e(TAG, "Update preparation failed:", e);
                return null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Unable to reboot into recovery mode:", e);
            return null;
        }

        // Everything finsihed without error
        mPreparationFailed = false;
        return null;
    }

    @Override
    protected void onPostExecute(Void result) {
        if (mProgressDialog != null) {
            // Dismiss the progress dialog
            mProgressDialog.dismiss();
        }

        if (mPreparationFailed) {
            // Something went wrong
            mParent.updatePreparationFailed();
        } else {
            // Everything is ready, so reboot into recovery
            mParent.triggerReboot(mContext);
        }
    }
}
