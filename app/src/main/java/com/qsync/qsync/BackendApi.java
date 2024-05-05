package com.qsync.qsync;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.EditText;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class BackendApi {
    private static final String TAG = "BackendApi";

    private static final String QSYNC_WRITEABLE_DIRECTORY = ""; // Specify your directory path here

    public static String askInput(String flag, String inputContext,Context context) {
        final String[] result = new String[1];

        ProcessExecutor.Function userInput = new ProcessExecutor.Function() {
            @Override
            public void execute() {
                final Handler handler = new Handler(Looper.getMainLooper());

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(flag);
                        builder.setMessage(inputContext);

                        // Set up the input
                        final EditText input = new EditText(context);
                        builder.setView(input);

                        // Set up the buttons
                        builder.setPositiveButton("OK", (dialog, which) -> {
                            result[0] = input.getText().toString();
                            dialog.dismiss();
                        });
                        builder.setNegativeButton("Cancel", (dialog, which) -> {
                            dialog.cancel();
                        });
                        Log.d("BACKEND API","LA FENETRE DE DIALOGUE VA ETRE AFFICHEE");
                        builder.show();
                    }
                });


            }

        };

        ProcessExecutor.executeOnUIThread(userInput);



        // Wait for user input
        while (result[0] == null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return result[0];
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

    public static void giveInput(String flag, String data) {
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
