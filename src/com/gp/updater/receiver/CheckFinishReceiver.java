package com.gp.updater.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.gp.updater.service.UpdaterDCExtension;

public class CheckFinishReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent i = new Intent(context, UpdaterDCExtension.class);
        i.setAction(UpdaterDCExtension.ACTION_DATA_UPDATE);
        context.startService(i);
    }
}
