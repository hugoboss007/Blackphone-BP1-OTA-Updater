package com.gp.updater.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.gp.updater.OTA;

public class NotificationClickReceiver extends BroadcastReceiver{
	
    @Override
    public void onReceive(Context context, Intent intent) {

        // Bring the main app to the foreground
    	Intent clickIntent = null;
		
		clickIntent = new Intent(context, OTA.class);	

		clickIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | 
                   			 Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        context.startActivity(clickIntent);
    }
}
