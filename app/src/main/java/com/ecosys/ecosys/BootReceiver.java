/*
 * *
 *  * Created by Théo Mougnibas on 05/07/2024 18:43
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 05/07/2024 18:43
 *
 */

package com.ecosys.ecosys;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("BOOTRECEIVER","BOOT EVENT RECEIVED");

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d("BOOTRECEIVER","BOOT EVENT RECEIVED");
            intent = new Intent(context, StartupService.class);
            context.startService(intent);



            /*Intent activityIntent = new Intent(context, SelectorActivity.class);
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activityIntent.putExtra("flag","[BOOT_EVENT]");
            context.startActivity(activityIntent);*/


        }
    }
}
