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
import android.content.pm.PackageManager;
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



    public boolean checkPackageNameExists(String packageName){
        PackageManager packageManager = AppsIntentActivity.this.getPackageManager();
        try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            return true;  // Package is installed
        } catch (PackageManager.NameNotFoundException e) {
            return false;  // Package is not installed
        }
    }

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
            String packageName = intent.getStringExtra("package_name");
            TextView textView = findViewById(R.id.textView);

            // check if the package name is legit
            // if not, just warn the user and stop
            if(!checkPackageNameExists(packageName)){
                BackendApi.displayToast(AppsIntentActivity.this,getString(R.string.malicious_app_install));
                textView.setText(getString(R.string.malicious_app_install));
                return;
            }



            //packageName = Globals.replaceSpecialChars(packageName);

            // Display the received values (you can handle them as needed)


            textView.setText("Action Flag: " + actionFlag + "\nApp Name: " + packageName);

            AccesBdd acces = new AccesBdd(AppsIntentActivity.this);



            if(Objects.equals(actionFlag, "[INSTALL_APP]")){


                String rp = BackendApi.askInput("[INSTALL_APP]",getString(R.string.confirm_app_install),AppsIntentActivity.this,false);


                if(rp.equals("y")){
                    if(acces.checkAppExistenceFromName(packageName)){
                        textView.setText("An application with this name is already registered");
                    }else{
                        // create app folder
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.MediaColumns.DISPLAY_NAME, packageName);
                        values.put(MediaStore.MediaColumns.MIME_TYPE, "vnd.android.document/directory");
                        Uri folderUri = Uri.parse("content://" + "com.qsync.fileprovider" + "/" + packageName);

                        Uri newFolderUri = getContentResolver().insert(folderUri, values);


                        if (newFolderUri != null) {
                            Log.d(TAG, "Folder created successfully: " + newFolderUri);
                        } else {
                            // app must already exists, don't link the new one
                            Log.e(TAG, "Failed to create folder: " + packageName);
                            return;
                        }


                        // create a sync in it
                        acces.createSync(folderUri.getPath());
                    }
                }



            }
        }
    }
}