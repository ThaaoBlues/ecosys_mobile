/*
 * *
 *  * Created by Théo Mougnibas on 27/06/2024 17:18
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 27/06/2024 17:18
 *
 */

package com.ecosys.ecosys;

import static android.app.Activity.RESULT_OK;

import static androidx.activity.result.ActivityResultCallerKt.registerForActivityResult;
import static androidx.core.content.ContextCompat.startActivity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;

import java.util.Map;

public class BackendApi {
    private static final String TAG = "BackendApi";

    private static final String ECOSYS_WRITEABLE_DIRECTORY = ""; // Specify your directory path here
        // Initialize the ActivityResultLauncher

    public static void launchInputActivityAndBroadCastResult( String flag, String inputContext, Context context, boolean textMode) {
        Intent intent = new Intent(context, InputActivity.class);
        intent.putExtra(InputActivity.FLAG_KEY, flag);
        intent.putExtra(InputActivity.INPUT_CONTEXT_KEY, inputContext);
        intent.putExtra(InputActivity.TEXT_MODE_KEY, textMode);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Use the ActivityResultLauncher to start the InputActivity
        context.startActivity(intent);
    }
    public static String askInput(String flag, String inputContext,Context context,Boolean textMode) {
        final String[] result = new String[1];

        ProcessExecutor.Function userInput = new ProcessExecutor.Function() {
            @Override
            public void execute() {
                final Handler handler = new Handler(Looper.getMainLooper());

                handler.post(new Runnable() {
                    @RequiresApi(api = Build.VERSION_CODES.O)
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

                        AlertDialog alert = builder.create();
                        Log.d("BACKEND API","LA FENETRE DE DIALOGUE VA ETRE AFFICHEE");

                        // as we send this from a service started from an activity that does not exists anymore
                        alert.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                        alert.show();

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




    public static void displayToast(Context context, String text){
        ProcessExecutor.Function showToast = new ProcessExecutor.Function() {
            @Override
            public void execute() {
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
            }
        };

        ProcessExecutor.executeOnUIThread(showToast);
    }


    public interface DeviceButtonCallback{
        void callback(Map<String,String> device);
    }





    public static void addButtonsFromDevicesGenArray(Context context, Globals.GenArray<Map<String, String>> devices, LinearLayout linearLayout, DeviceButtonCallback callback) {
        // Will produce errors when user is not on the linear layout parent's fragment
        try {
            linearLayout.removeAllViews();
        } catch (NullPointerException e) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(context);

        for (int i = 0; i < devices.size(); i++) {
            View cardView = inflater.inflate(R.layout.button_card_layout, linearLayout, false);
            TextView buttonText = cardView.findViewById(R.id.button_text);

            buttonText.setText(String.format("%s; %s", devices.get(i).get("hostname"), devices.get(i).get("ip_addr")));

            int finalI = i;
            cardView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    callback.callback(devices.get(finalI));
                }
            });

            linearLayout.addView(cardView);
        }
    }

    public static void addButtonsFromSynchroGenArray(Context context, Globals.GenArray<Globals.SyncInfos> synchros, LinearLayout linearLayout, SynchronisationsFragment.SynchronisationButtonCallback callback) {
        // Will produce errors when user is not on the linear layout parent's fragment
        try {
            linearLayout.removeAllViews();
        } catch (NullPointerException e) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(context);

        for (int i = 0; i < synchros.size(); i++) {
            View cardView = inflater.inflate(R.layout.button_card_layout, linearLayout, false);
            TextView buttonText = cardView.findViewById(R.id.button_text);

            buttonText.setText(synchros.get(i).getPath());

            int finalI = i;
            cardView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    callback.callback(synchros.get(finalI));
                }
            });

            linearLayout.addView(cardView);
        }
    }

    public static void showLoadingNotification(Context context,String msg){

        ProcessExecutor.Function nt = new ProcessExecutor.Function() {
            @Override
            public void execute() {
                NotificationHelper.showLoadingNotification(context,msg);
            }
        };

        ProcessExecutor.executeOnUIThread(nt);

    }

    public static void discardLoadingNotification(Context context){

        ProcessExecutor.Function nt = new ProcessExecutor.Function() {
            @Override
            public void execute() {
                NotificationHelper.removeLoadingNotification(context);
            }
        };

        ProcessExecutor.executeOnUIThread(nt);

    }

    public static void discardAppRunningNotification(Context context){

        ProcessExecutor.Function nt = new ProcessExecutor.Function() {
            @Override
            public void execute() {
                NotificationHelper.removeLoadingNotification(context);
            }
        };

        ProcessExecutor.executeOnUIThread(nt);

    }


    public static void showAppRunningNotification(Context context,String msg){

        ProcessExecutor.Function nt = new ProcessExecutor.Function() {
            @Override
            public void execute() {
                NotificationHelper.showAppRunningNotification(context,msg);
            }
        };

        ProcessExecutor.executeOnUIThread(nt);

    }




}
