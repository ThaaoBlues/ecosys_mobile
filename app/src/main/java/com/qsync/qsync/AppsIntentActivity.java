/*
 * *
 *  * Created by Théo Mougnibas on 27/06/2024 17:18
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 27/06/2024 17:18
 *
 */

package com.qsync.qsync;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Objects;

public class AppsIntentActivity extends AppCompatActivity {


    private static String TAG = "Qsync Server : AppsIntentActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_apps_intent);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        Intent intent = getIntent();
        if (intent != null) {
            // Retrieve the flag and app name from the intent
            String actionFlag = intent.getStringExtra("action_flag");
            String appName = intent.getStringExtra("app_name");

            // Display the received values (you can handle them as needed)


            TextView textView = findViewById(R.id.textView);
            textView.setText("Action Flag: " + actionFlag + "\nApp Name: " + appName);

            AccesBdd acces = new AccesBdd(AppsIntentActivity.this);



            if(Objects.equals(actionFlag, "[INSTALL_APP]")){

                if(acces.checkAppExistenceFromName(appName)){
                    textView.setText("An application with this name is already registered");
                }else{
                    // create app folder
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, appName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, "vnd.android.document/directory");
                    Uri folderUri = Uri.parse("content://" + "com.qsync.fileprovider" + "/" + appName);

                    Uri newFolderUri = getContentResolver().insert(folderUri, values);

                    if (newFolderUri != null) {
                        Log.d(TAG, "Folder created successfully: " + newFolderUri);
                    } else {
                        // app must already exists, don't link the new one
                        Log.e(TAG, "Failed to create folder: " + appName);
                        return;
                    }


                    // create a sync in it
                    acces.createSync(folderUri.getPath());
                }


            }
        }
    }
}