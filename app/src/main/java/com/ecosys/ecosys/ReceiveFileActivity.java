/*
 * *
 *  * Created by Théo Mougnibas on 27/06/2024 17:18
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 27/06/2024 17:18
 *
 */

package com.ecosys.ecosys;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;


public class ReceiveFileActivity extends Activity {


    private Map<String,String> target_device = null;

    private static final String TAG = "Ecosys Server : ReceiveFileActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive_file);

        // Check if the intent that launched this activity is a file sharing intent
        Intent receivedIntent = getIntent();
        String action = receivedIntent.getAction();
        String type = receivedIntent.getType();

        AccesBdd acces = new AccesBdd(ReceiveFileActivity.this);


        LinearLayout linearLayout = findViewById(R.id.activity_receive_files_network_devices_list_linearlayout);
        TextView title = findViewById(R.id.activity_receive_files_title_textview);
        title.setText(R.string.choose_a_device_to_send_your_things_to);

        // ask user to select a device
        BackendApi.addButtonsFromDevicesGenArray(
                ReceiveFileActivity.this,
                acces.getNetworkMap(),
                linearLayout
                ,
                new BackendApi.DeviceButtonCallback() {
                    @Override
                    public void callback(Map<String, String> device) {
                        target_device = device;


                        if (Intent.ACTION_SEND.equals(action) && type != null) {
                            if ("text/plain".equals(type)) {
                                handleSharedText(receivedIntent); // Handle shared text
                            } else if (type.startsWith("image/")) {
                                handleSharedImage(receivedIntent); // Handle shared image
                            } else if (type.startsWith("audio/")) {
                                handleSharedAudio(receivedIntent); // Handle shared audio
                            } else if (type.startsWith("video/")) {
                                handleSharedVideo(receivedIntent); // Handle shared video
                            } else {
                                handleSharedFile(receivedIntent); // Handle shared generic file
                            }
                        }

                    }
                }
        );


        acces.closedb();



    }

    private void handleSharedText(Intent intent) {
        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (sharedText != null) {
            Toast.makeText(this, "Received shared text: " + sharedText, Toast.LENGTH_SHORT).show();

            ProcessExecutor.Function aski = new ProcessExecutor.Function() {
                @Override
                public void execute() {
                    String text = BackendApi.askMultilineInput(
                            "[MODIFY_SHARED_TEXT]",
                            "Type/paste the text you want to send here",
                            ReceiveFileActivity.this,
                            sharedText
                    );

                    if(text.equals("[ANNULATION]")){
                        Log.d(TAG,text);
                        return;
                    }

                    try {
                        File outputFile = File.createTempFile(
                                "text",
                                ".txt",
                                getCacheDir()
                        );

                        FileOutputStream fos = new FileOutputStream(outputFile);
                        fos.write(text.getBytes());
                        fos.close();

                        Log.d(TAG, Uri.parse(outputFile.getPath()).toString());
                        SendLA(Uri.parse(outputFile.getPath()));

                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            };

            ProcessExecutor.startProcess(aski);
        }
    }

    private void handleSharedImage(Intent intent) {
        Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (imageUri != null) {
            SendLA(imageUri);
            Toast.makeText(this, "Received shared image: " + imageUri.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSharedAudio(Intent intent) {
        Uri audioUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (audioUri != null) {
            SendLA(audioUri);
            Toast.makeText(this, "Received shared audio: " + audioUri.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSharedVideo(Intent intent) {
        Uri videoUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (videoUri != null) {
            SendLA(videoUri);
            Toast.makeText(this, "Received shared video: " + videoUri.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSharedFile(Intent intent) {
        Uri fileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (fileUri != null) {
            SendLA(fileUri);
            Toast.makeText(this, "Received shared file: " + fileUri.toString(), Toast.LENGTH_SHORT).show();
        }
    }


    private void SendLA(Uri uri) {
        ProcessExecutor.Function SendLA = new ProcessExecutor.Function() {
            @Override
            public void execute() {

                BackendApi.showLoadingNotification(ReceiveFileActivity.this,"Sending Largage Aerien...");
                Networking nt = new Networking(ReceiveFileActivity.this, getFilesDir().toString());
                nt.sendLargageAerien(uri, target_device.get("ip_addr"),false);
                BackendApi.discardLoadingNotification(ReceiveFileActivity.this);
            }
        };
        ProcessExecutor.startProcess(SendLA);
    }
}