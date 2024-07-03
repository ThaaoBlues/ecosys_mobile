/*
 * *
 *  * Created by Théo Mougnibas on 27/06/2024 17:18
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 27/06/2024 17:18
 *
 */

package com.qsync.qsync;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.qsync.qsync.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;

import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int MY_PERMISSIONS_REQUEST_POST_NOTIFICATIONS = 2;
    private ActivityResultLauncher<Intent> selectFolderLauncher;
    private static Networking nt;
    public void requestPermission(final Activity activity, final String permission, final int requestCode) {
        if (ActivityCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                // Show an explanation to the user why you need the permission
                // After the user sees the explanation, try again to request the permission
                new AlertDialog.Builder(this)
                        .setTitle("Notification Permission Needed")
                        .setMessage("This app needs the Notification permission to show you when it is talking to your others devices.")
                        .setPositiveButton("OK", (dialog, which) -> {
                            ActivityCompat.requestPermissions(this,
                                    new String[]{permission},
                                    requestCode);
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                        .create()
                        .show();
            } else {
                // No explanation needed, request the permission
                ActivityCompat.requestPermissions(this,
                        new String[]{permission},
                        requestCode);
            }
        } else {
            // Permission has already been granted
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode){


            case MY_PERMISSIONS_REQUEST_POST_NOTIFICATIONS:

                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {


                } else {
                    // Permission denied
                }
                break;
        }
    }

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        /*setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        */

        requestPermission(MainActivity.this,"android.permission.POST_NOTIFICATIONS",MY_PERMISSIONS_REQUEST_POST_NOTIFICATIONS);

        //deleteDatabase("qsync");

        // clean old network map at each app startup
        AccesBdd acces = new AccesBdd(MainActivity.this);
        acces.cleanNetworkMap();



        ZeroConfService zc = new ZeroConfService(MainActivity.this);


        selectFolderLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            // There are no request codes
                            Intent data = result.getData();
                            if (result.getResultCode() == Activity.RESULT_OK) {
                                if (data.getData() != null) {

                                    //Log.d("SynchronisationsFragment","Uri : "+ PathUtils.getPathFromUri(getContext(),data.getData()));
                                    Uri treeUri = data.getData();

                                    MainActivity.this.getContentResolver().takePersistableUriPermission(treeUri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                                    DocumentFile directory = DocumentFile.fromTreeUri( MainActivity.this, treeUri);
                                    if (directory != null && directory.isDirectory()) {
                                        AccesBdd acces = new AccesBdd( MainActivity.this);

                                        acces.SetSecureId(nt.getTmpSecureIdForCreation());
                                        acces.CreateSyncFromOtherEnd(directory.getUri().toString(),nt.getTmpSecureIdForCreation());
                                        acces.closedb();
                                        FileSystem.startDirectoryMonitoring( MainActivity.this,directory);

                                        nt.setSetupDlLock(false);


                                    }
                                }
                            }

                        }
                    }
                });

        ProcessExecutor.Function StartServer = new ProcessExecutor.Function() {
            @Override
            public void execute() {
                nt = new Networking(MainActivity.this,getFilesDir().toString());
                nt.setSelectFolderLauncher(selectFolderLauncher);
                nt.ServerMainLoop();
            }
        };

        ProcessExecutor.startProcess(StartServer);

        ProcessExecutor.Function StartWatcher = new ProcessExecutor.Function() {
            @Override
            public void execute() {
                AccesBdd acces = new AccesBdd(MainActivity.this);
                Map<String, Globals.SyncInfos> tasks = acces.ListSyncAllTasks();
                tasks.forEach((k,v)->{

                    DocumentFile df = DocumentFile.fromTreeUri(MainActivity.this,Uri.parse(v.getPath()));
                    Log.d("Qsync Server ","Starting to monitor : "+ v.getPath()+"\n Readable = "+df.canRead());

                    FileSystem.startDirectoryMonitoring(
                            MainActivity.this,
                            df
                    );

                });

                acces.closedb();
            }
        };

        ProcessExecutor.startProcess(StartWatcher);



    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }





}