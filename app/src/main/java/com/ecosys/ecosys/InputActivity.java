package com.ecosys.ecosys;/*
 * *
 *  * Created by Théo Mougnibas on 16/08/2024 13:30
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 16/08/2024 13:29
 *
 */

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.Objects;

public class InputActivity extends Activity {

    public static final String FLAG_KEY = "flag";
    public static final String INPUT_CONTEXT_KEY = "inputContext";
    public static final String TEXT_MODE_KEY = "textMode";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Parse the intent
        Intent intent = getIntent();
        String flag = intent.getStringExtra(FLAG_KEY);
        String inputContext = intent.getStringExtra(INPUT_CONTEXT_KEY);
        boolean textMode = intent.getBooleanExtra(TEXT_MODE_KEY,false);
        switch (flag){
            case "[MULTILINE_INPUT]":
                askMultilineInput(flag, inputContext, this);
                break;
            case "[SINGLE_LINE_INPUT_OR_CONFIRMATION_DIALOG]":
                askInput(flag, inputContext, this, textMode);
                break;

            default:
                break;

        }
        // Decide which method to call based on the flag
        if ("multiLineInput".equals(flag)) {
        } else {
        }



    }

    public void askInput(String flag, String inputContext, Context context, Boolean textMode) {
        final String[] result = new String[1];

        ProcessExecutor.Function waitInput = new ProcessExecutor.Function() {
            @Override
            public void execute() {
                // Wait for user input
                while (result[0] == null) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                // Send the result back to the service via a broadcast
                Intent resultIntent = new Intent("com.ecosys.RESULT_ACTION");
                resultIntent.putExtra("result", result[0]);
                sendBroadcast(resultIntent);
                finish();
            }
        };

        ProcessExecutor.Function userInput = new ProcessExecutor.Function() {
            @Override
            public void execute() {
                final Handler handler = new Handler(Looper.getMainLooper());

                handler.post(new Runnable() {
                    @RequiresApi(api = Build.VERSION_CODES.O)
                    @Override
                    public void run() {

                        LayoutInflater inflater = getLayoutInflater();
                        View dialogView = inflater.inflate(R.layout.text_dialog_custom_layout, null);

                        // Build the alert dialog
                        AlertDialog.Builder builder = new AlertDialog.Builder(context,R.style.TransparentDialogStyle);
                        builder.setView(dialogView);




                        TextView title = dialogView.findViewById(R.id.text_dialog_title);
                        title.setText(R.string.select_an_action);

                        TextView msg = dialogView.findViewById(R.id.text_dialog_message);
                        msg.setText(R.string.largage_aerien_msg);

                        // Set up the input
                        EditText input;
                        if (textMode) {
                            input = new EditText(context);
                            builder.setView(input);
                        } else {
                            input = null;
                        }

                        AlertDialog alert = builder.create();


                        Button positiveButton = dialogView.findViewById(R.id.text_dialog_positive_button);

                        positiveButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                result[0] = textMode ? input.getText().toString() : "y";
                                alert.dismiss();
                            }
                        });

                        Button negativeButton = dialogView.findViewById(R.id.text_dialog_negative_button);

                        negativeButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                result[0] = textMode ? input.getText().toString() : "n";
                                alert.dismiss();
                            }
                        });

                        Log.d("BACKEND API", "LA FENETRE DE DIALOGUE VA ETRE AFFICHEE");

                        // Adjust window type to ensure it shows over other apps
                        alert.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                        alert.show();
                    }
                });
            }
        };

        ProcessExecutor.executeOnUIThread(userInput);
        ProcessExecutor.startProcess(waitInput);
    }

    public void askMultilineInput(String flag, String inputContext, Context context) {
        final String[] result = new String[1];


        ProcessExecutor.Function waitInput = new ProcessExecutor.Function() {
            @Override
            public void execute() {
                // Wait for user input
                while (result[0] == null) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                // Send the result back to the service via a broadcast
                Intent resultIntent = new Intent("com.ecosys.RESULT_ACTION");
                resultIntent.putExtra("result", result[0]);
                sendBroadcast(resultIntent);
                finish();
            }
        };

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
                        EditText input = new EditText(context);
                        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                        input.setWidth(100);
                        builder.setView(input);

                        // Set up the buttons
                        builder.setPositiveButton("OK", (dialog, which) -> {
                            result[0] = input.getText().toString();
                            dialog.dismiss();
                        });
                        builder.setNegativeButton("Cancel", (dialog, which) -> {
                            result[0] = null;
                            dialog.cancel();
                        });

                        Log.d("BACKEND API", "LA FENETRE DE DIALOGUE VA ETRE AFFICHEE");
                        builder.show();
                    }
                });
            }
        };

        ProcessExecutor.executeOnUIThread(userInput);
        ProcessExecutor.startProcess(waitInput);


    }
}
