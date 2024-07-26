/*
 * *
 *  * Created by Théo Mougnibas on 05/07/2024 18:25
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 05/07/2024 18:24
 *
 */

package com.ecosys.ecosys;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class SelectorActivity extends AppCompatActivity {
    public ActivityResultLauncher<Intent> selectFolderLauncher;

    private static final String TAG = "QSync Server : SelectorActivity";

    public static FolderPickerCallback callback;


    private ResultReceiver resultReceiver;


    public static void setCallBack(StartupService startupService) {
        callback = startupService;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);


        /*setContentView(R.layout.activity_selector);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });*/


        Intent intent = getIntent();

        String flag = intent.getStringExtra("flag");
        initFolderSelectorToLinkTask();

        switch (flag){

            case "[BOOT_EVENT]":
                intent = new Intent(this, StartupService.class);
                startService(intent);
                finish();
                break;

                //called from MainActivity at startup
            case "[MAKE_SURE_SERVERS_ARE_RUNNING]":

                if(!ProcessExecutor.isMyServiceRunning(SelectorActivity.this,StartupService.class)){
                    Log.d(TAG,"Service was not running at application startup !! Starting it now");
                    intent = new Intent(this, StartupService.class);
                    startService(intent);
                    finish();
                }

                break;

            case "[PICK_FOLDER]":
                resultReceiver = getIntent().getParcelableExtra("receiver");
                // Start folder picker intent
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                selectFolderLauncher.launch(intent);

            default:
                break;

        }

    }



    @Override
    protected void onStart() {
        super.onStart();

    }



    public void initFolderSelectorToLinkTask(){
        selectFolderLauncher = SelectorActivity.this.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            // There are no request codes
                            Intent data = result.getData();
                            if (data.getData() != null) {

                                //Log.d("SynchronisationsFragment","Uri : "+ PathUtils.getPathFromUri(SelectorActivity.this,data.getData()));
                                Uri treeUri = data.getData();

                                SelectorActivity.this.getContentResolver().takePersistableUriPermission(treeUri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                                // Pass the URI back to the service using the ResultReceiver
                                if (resultReceiver != null) {
                                    Bundle bundle = new Bundle();
                                    bundle.putParcelable("folder_uri", treeUri);
                                    resultReceiver.send(Activity.RESULT_OK, bundle);
                                }else{
                                    Log.d(TAG, "ResultReceiver is null");

                                }

                                finish();

                            }

                        }
                    }
                });
    }
}