/*
 * *
 *  * Created by Théo Mougnibas on 05/07/2024 18:43
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 05/07/2024 18:43
 *
 */

package com.qsync.qsync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent activityIntent = new Intent(context, SelectorActivity.class);
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activityIntent.putExtra("flag","[BOOT_EVENT]");
            context.startActivity(activityIntent);
        }
    }
}
