/*
 * *
 *  * Created by Théo Mougnibas on 27/06/2024 17:18
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 27/06/2024 17:18
 *
 */

package com.qsync.qsync;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;

public class AppsIntentActivity extends AppCompatActivity {




    private TextView textView;
    private String PROVIDER_ROOT=  "content://com.qsync.qsync.fileprovider/apps/";

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
            textView = findViewById(R.id.apps_intent_activity_textview);

            // check if the package name is legit
            // if not, just warn the user and stop

            Log.d(TAG,"PACKAGE NAME : "+packageName);
            /*if(!checkPackageNameExists(packageName)){

                BackendApi.displayToast(AppsIntentActivity.this,getString(R.string.malicious_app_install));
                textView.setText(getString(R.string.malicious_app_install));
                return;
            }*/



            //packageName = Globals.replaceSpecialChars(packageName);

            // Display the received values (you can handle them as needed)


            textView.setText("Action Flag: " + actionFlag + "\nApp Name: " + packageName);

            AccesBdd acces = new AccesBdd(AppsIntentActivity.this);


            switch (actionFlag){
                case "[INSTALL_APP]":
                    installApp(intent,packageName,acces);
                    break;

                case "[CREATE_FILE]":


                    Log.d(TAG,"AUTHORITY : "+getReferrer().getAuthority());
                    if(packageName.equals(getReferrer().getAuthority()) && acces.checkAppExistenceFromName(packageName)){
                       Log.d(TAG,"IN CREATE FILE");
                        DocumentFile rootFolder = DocumentFile.fromFile(
                                getExternalFilesDir(null)
                        );

                        DocumentFile appFolder = rootFolder.findFile("apps").findFile(packageName);


                        String fileName = intent.getStringExtra("file_name");
                        String mimetype = intent.getStringExtra("mime_type");
                        // sanitize file name

                        // create file
                        Uri uri = FileProvider.getUriForFile(
                                this,
                                "com.qsync.qsync.fileprovider",
                                new File(appFolder.createFile(mimetype,fileName).getUri().getPath())
                        );
                        AppsIntentActivity.this.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

                        intent = new Intent(Intent.ACTION_SEND);

                        intent.setClassName(packageName,packageName+".QSyncCallbackActivity");
                        intent.putExtra("action_flag","[CREATE_FILE]");
                        intent.setDataAndType(uri, mimetype);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                        startActivity(intent);
                        finish();
                    }else{
                        textView.setText(R.string.app_is_not_registered_in_qsync_or_is_trying_to_access_data_that_is_not_its_own);
                        return;
                    }




            }
        }
    }



    private void installApp(Intent intent, String packageName, AccesBdd acces){
        Log.d(TAG,"IN INSTALL APP");


                /*String rp = BackendApi.askInput(
                        "[INSTALL_APP]",
                        getString(R.string.confirm_app_install),
                        AppsIntentActivity.this,
                        false
                );*/

        String rp = "y";
        if(rp.equals("y")) {
            if (acces.checkAppExistenceFromName(packageName)) {
                textView.setText("An application with this name is already registered");
            } else {
                DocumentFile rootFolder = DocumentFile.fromFile(
                        getExternalFilesDir(null)
                );

                // make sure the apps folder is present
                if (rootFolder.findFile("apps") == null) {
                    rootFolder = rootFolder.createDirectory("apps");
                } else {
                    rootFolder = rootFolder.findFile("apps");
                }

                DocumentFile appFolder = rootFolder.createDirectory(packageName);


                if (appFolder != null) {
                    Log.d(TAG, "Folder created successfully: " + appFolder.getUri());
                } else {
                    // app must already exists, don't link the new one
                    Log.e(TAG, "Failed to create folder into : " + rootFolder.getUri().getPath());
                    return;
                }

                Uri uri = FileProvider.getUriForFile(
                        this,
                        "com.qsync.qsync.fileprovider",
                        new File(appFolder.getUri().getPath())
                );





                // create a sync in it
                //acces.createSync(getSafUris(new File(appFolder.getUri().getPath()))[1].toString());
                acces.createSync(appFolder.getUri().getPath());
                acces.addToutEnUn(new Globals.ToutEnUnConfig(
                        packageName,
                        "",
                        false,
                        "",
                        "",
                        "",
                        uri.getPath(),
                        "",
                        ""
                ));


                Log.d(TAG,"Restarting background processes to take account of new app...");
                // restart networking and files watching service to take account of new task
                if(ProcessExecutor.isMyServiceRunning(AppsIntentActivity.this,StartupService.class)){
                    stopService( new Intent(this, StartupService.class));
                }
                startService( new Intent(this, StartupService.class));
                Log.d(TAG,"Service restarted");


                AppsIntentActivity.this.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);


                intent = new Intent(Intent.ACTION_SEND);

                intent.setClassName(packageName, packageName + ".QSyncCallbackActivity");
                intent.putExtra("flag", "[INSTALL_APP]");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.setDataAndType(uri, "*/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivity(intent);


            }
        }
    }



}