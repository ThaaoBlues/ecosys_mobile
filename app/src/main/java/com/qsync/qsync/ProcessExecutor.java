package com.qsync.qsync;

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
}
