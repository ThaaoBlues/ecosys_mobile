package com.qsync.qsync;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.util.Map;

public class BackendApi {
    private static final String TAG = "BackendApi";

    private static final String QSYNC_WRITEABLE_DIRECTORY = ""; // Specify your directory path here

    public static String askInput(String flag, String inputContext,Context context,Boolean textMode) {
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
                        EditText input;
                        if (textMode){
                            input = new EditText(context);
                            builder.setView(input);
                        } else {
                            input = null;
                        }


                        // Set up the buttons
                        builder.setPositiveButton("OK", (dialog, which) -> {
                            result[0] = textMode ? input.getText().toString() : "y";
                            dialog.dismiss();
                        });
                        builder.setNegativeButton("Cancel", (dialog, which) -> {
                            result[0] = null;
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


    public static String askMultilineInput(String flag, String inputContext,Context context) {
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
                        EditText input;
                        input = new EditText(context);
                        input.setInputType(InputType.TYPE_CLASS_TEXT |InputType.TYPE_TEXT_FLAG_MULTI_LINE);

                        //input.setLines(10);
                        input.setWidth(100);
                        builder.setView(input);



                        // Set up the buttons
                        builder.setPositiveButton("OK", (dialog, which) -> {
                            result[0] =  input.getText().toString();
                            dialog.dismiss();
                        });
                        builder.setNegativeButton("Cancel", (dialog, which) -> {
                            result[0] = null;
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

    public static void ShareFile(Context context, Uri fileUri) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_STREAM, fileUri);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(intent, "Share File");
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(chooser);
            } else {
                // Handle if no activity can handle the intent
                Toast.makeText(context, "No app found to handle the share action", Toast.LENGTH_SHORT).show();
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

    public static void openFile(Context context, Uri fileUri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(fileUri, "application/*");
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
        } else {
            // Handle if no activity can handle the intent
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


    public static void displayToast(Context context, String text){
        ProcessExecutor.Function showToast = new ProcessExecutor.Function() {
            @Override
            public void execute() {
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
            }
        };

        ProcessExecutor.executeOnUIThread(showToast);
    }



    public interface EventCallback {
        void onEvent(String context);
    }

    public interface DeviceButtonCallback{
        void callback(Map<String,String> device);
    }





    public static void addButtonsFromDevicesGenArray(Context context, Globals.GenArray<Map<String,String>> devices, LinearLayout linearLayout,DeviceButtonCallback callback) {

        // will produce erros when user is not on the linear layout parent's fragment
        try{
            linearLayout.removeAllViews();
        }catch (java.lang.NullPointerException e){

            return;
        }


        for (int i = 0;i<devices.size();i++) {
            Button button = new Button(context);
            //Log.d(TAG,"adding device to linear layout : "+devices.get(i).get("hostname"));
            button.setText(String.format("%s;%s", devices.get(i).get("hostname"), devices.get(i).get("ip_addr")));
            button.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            int finalI = i;
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    callback.callback(devices.get(finalI));
                }
            });
            linearLayout.addView(button);
            //Log.d("BACKEND API","Adding button for device : "+devices.get(i).toString());
        }


    }

    public static void showLargageAerienEmissionNotification(Context context,String msg){

        ProcessExecutor.Function nt = new ProcessExecutor.Function() {
            @Override
            public void execute() {
                NotificationHelper.showLoadingNotification(context,msg);
            }
        };

        ProcessExecutor.executeOnUIThread(nt);

    }

    public static void discardLargageAerienEmissionNotification(Context context){

        ProcessExecutor.Function nt = new ProcessExecutor.Function() {
            @Override
            public void execute() {
                NotificationHelper.removeLoadingNotification(context);
            }
        };

        ProcessExecutor.executeOnUIThread(nt);

    }



}
