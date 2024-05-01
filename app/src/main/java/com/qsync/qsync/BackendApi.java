package com.qsync.qsync;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class BackendApi {
    private static final String TAG = "BackendApi";

    private static final String QSYNC_WRITEABLE_DIRECTORY = "path_to_your_directory"; // Specify your directory path here

    public static String askInput(Context context, String flag, String inputContext) {
        try {
            File file = new File(QSYNC_WRITEABLE_DIRECTORY, flag + ".btf");
            FileWriter writer = new FileWriter(file);
            writer.write(inputContext);
            writer.close();

            long originalSize = file.length();

            while (file.length() == originalSize) {
                Thread.sleep(2000); // Sleep for 2 seconds
            }

            byte[] bytes = new byte[(int) (file.length() - inputContext.length())];
            FileInputStream fis = new FileInputStream(file);
            fis.skip(inputContext.length());
            fis.read(bytes);
            fis.close();

            return new String(bytes);
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Error in askInput(): " + e.getMessage());
            return null;
        }
    }

    public static String readInputContext(String flag) {
        try {
            File file = new File(QSYNC_WRITEABLE_DIRECTORY, flag + ".btf");
            byte[] bytes = new byte[(int) file.length()];
            FileInputStream fis = new FileInputStream(file);
            fis.read(bytes);
            fis.close();

            return new String(bytes);
        } catch (IOException e) {
            Log.e(TAG, "Error in readInputContext(): " + e.getMessage());
            return null;
        }
    }

    public static void giveInput(Context context, String flag, String data) {
        try {
            File file = new File(QSYNC_WRITEABLE_DIRECTORY, flag + ".btf");
            FileWriter writer = new FileWriter(file, true);
            writer.write(data);
            writer.close();
        } catch (IOException e) {
            Log.e(TAG, "Error in giveInput(): " + e.getMessage());
        }
    }

    public static void waitEventLoop(final Map<String, EventCallback> callbacks) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        File directory = new File(QSYNC_WRITEABLE_DIRECTORY);
                        File[] files = directory.listFiles();

                        if (files != null) {
                            for (File file : files) {
                                if (!file.isDirectory() && file.getName().endsWith(".btf")) {
                                    String eventFlag = file.getName().substring(0, file.getName().length() - 4);

                                    try {
                                        FileInputStream fis = new FileInputStream(file);
                                        byte[] contextBytes = new byte[(int) file.length()];
                                        fis.read(contextBytes);
                                        fis.close();
                                        String context = new String(contextBytes);
                                        EventCallback callback = callbacks.get(eventFlag);
                                        if (callback != null) {
                                            callback.onEvent(context);
                                        }
                                    } catch (IOException e) {
                                        Log.e(TAG, "Error while reading event file in waitEventLoop(): " + e.getMessage());
                                    }
                                }
                            }
                        }
                        Thread.sleep(1000); // Sleep for 1 second
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Thread interrupted in waitEventLoop(): " + e.getMessage());
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
        thread.start();
    }

    public interface EventCallback {
        void onEvent(String context);
    }
}
