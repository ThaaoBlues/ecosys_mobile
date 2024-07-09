/*
 * *
 *  * Created by Théo Mougnibas on 27/06/2024 17:18
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 27/06/2024 17:18
 *
 */

package com.qsync.qsync;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

public class ProcessExecutor {

    // Define a functional interface for the function you want to execute
    public interface Function {
        void execute();
    }

    // Method to start a process with the given function
    public static void startProcess(Function function) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Execute the function
                function.execute();
            }
        }).start();
    }

    // Method to execute a function on the UI thread
    public static void executeOnUIThread(Function function) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {


                function.execute();
            }
        });
    }

    public static boolean isMyServiceRunning(Context context,Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
