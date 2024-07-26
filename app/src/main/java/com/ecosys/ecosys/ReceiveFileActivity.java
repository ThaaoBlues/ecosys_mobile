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
import android.widget.Toast;

public class ReceiveFileActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_receive_file);

        // Check if the intent that launched this activity is a file sharing intent
        Intent receivedIntent = getIntent();
        String action = receivedIntent.getAction();
        String type = receivedIntent.getType();

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

    private void handleSharedText(Intent intent) {
        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (sharedText != null) {
            // Handle the shared text here
            Toast.makeText(this, "Received shared text: " + sharedText, Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSharedImage(Intent intent) {
        Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (imageUri != null) {
            // Handle the shared image here
            Toast.makeText(this, "Received shared image: " + imageUri.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSharedAudio(Intent intent) {
        Uri audioUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (audioUri != null) {
            // Handle the shared audio here
            Toast.makeText(this, "Received shared audio: " + audioUri.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSharedVideo(Intent intent) {
        Uri videoUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (videoUri != null) {
            // Handle the shared video here
            Toast.makeText(this, "Received shared video: " + videoUri.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSharedFile(Intent intent) {
        Uri fileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (fileUri != null) {
            // Handle the shared file here
            Toast.makeText(this, "Received shared file: " + fileUri.toString(), Toast.LENGTH_SHORT).show();
        }
    }
}