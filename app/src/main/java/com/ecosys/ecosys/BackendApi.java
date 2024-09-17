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
import static androidx.core.content.ContextCompat.getColor;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;

import java.util.Map;

public class BackendApi {
    private static final String TAG = "Ecosys Server : BackendApi";


    public static void launchInputActivityAndBroadCastResult( String flag, String inputContext, Context context, boolean textMode) {
        Intent intent = new Intent(context, InputActivity.class);
        intent.putExtra(InputActivity.FLAG_KEY, flag);
        intent.putExtra(InputActivity.INPUT_CONTEXT_KEY, inputContext);
        intent.putExtra(InputActivity.TEXT_MODE_KEY, textMode);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Use the ActivityResultLauncher to start the InputActivity
        context.startActivity(intent);
    }


    public static String askMultilineInput(String flag, String inputContext,Context context,String defaultText) {
        final String[] result = new String[1];

        ProcessExecutor.Function userInput = new ProcessExecutor.Function() {
            @Override
            public void execute() {
                final Handler handler = new Handler(Looper.getMainLooper());

                handler.post(new Runnable() {
                    @Override
                    public void run() {


                        LayoutInflater inflater =LayoutInflater.from(context);
                        View dialogView = inflater.inflate(R.layout.text_dialog_custom_layout, null);

                        // Build the alert dialog
                        AlertDialog.Builder builder = new AlertDialog.Builder(context,R.style.TransparentDialogStyle);
                        builder.setView(dialogView);




                        TextView title = dialogView.findViewById(R.id.text_dialog_title);
                        title.setText(R.string.select_an_action);

                        TextView msg = dialogView.findViewById(R.id.text_dialog_message);
                        msg.setText(inputContext);

                        LinearLayout inputLayout = dialogView.findViewById(R.id.text_dialog_input_layout);

                        // Set up the input
                        EditText input;
                        input = new EditText(context);
                        input.setInputType(InputType.TYPE_CLASS_TEXT |InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                        input.setMaxHeight(250);
                        input.setWidth(100);
                        input.setTextColor(getColor(context,R.color.font1));
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams
                                (LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        input.setLayoutParams(params);

                        inputLayout.addView(input);

                        if(flag.equals("[MODIFY_SHARED_TEXT]")){
                            input.setText(defaultText);
                        }

                        AlertDialog alert = builder.create();









                        Button positiveButton = dialogView.findViewById(R.id.text_dialog_positive_button);

                        positiveButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                result[0] = input.getText().toString();
                                alert.dismiss();
                            }
                        });

                        Button negativeButton = dialogView.findViewById(R.id.text_dialog_negative_button);

                        negativeButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                result[0] = "[ANNULATION]";
                                alert.dismiss();
                            }
                        });


                        Log.d(TAG,"LA FENETRE DE DIALOGUE VA ETRE AFFICHEE");
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


    public static void openFile(Context context, Uri fileUri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(fileUri, "application/*");
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
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
