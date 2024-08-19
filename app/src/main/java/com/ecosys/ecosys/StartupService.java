/*
 * *
 *  * Created by Théo Mougnibas on 05/07/2024 18:40
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 05/07/2024 18:39
 *
 */

package com.ecosys.ecosys;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.util.Map;

public class StartupService extends Service implements FolderPickerCallback{


    private Networking nt;
    private String TAG ="Ecosys Server : StartupService";

    public StartupService() {
    }

    private final BroadcastReceiver resultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String result = intent.getStringExtra("result");
            Log.d(TAG, "Received result: " + result);
            // Handle the result here
            nt.setBroadcastResult(result);
        }
    };

    @Override
    public void onFolderPicked(Uri uri) {

        Log.d(TAG,"Retrieveed uri : "+uri.getPath());
        DocumentFile directory = DocumentFile.fromTreeUri( StartupService.this, uri);

        if (directory != null && directory.isDirectory()) {
            AccesBdd acces = new AccesBdd( StartupService.this);

            acces.setSecureId(nt.getTmpSecureIdForCreation());
            acces.createSyncFromOtherEnd(directory.getUri().toString(),nt.getTmpSecureIdForCreation());

            FileSystem.startDirectoryMonitoring( StartupService.this,directory,acces.getSecureId());
            acces.closedb();

            nt.setSetupDlLock(false);


        }
    }



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Perform background tasks
        // Service will continue to run until explicitly stopped
        Toast.makeText(StartupService.this, "BOOT EVENT COMPLETE", Toast.LENGTH_LONG).show();


        // make sure not crash has stuck a task in a locked state
        AccesBdd accesBdd = new AccesBdd(StartupService.this);
        accesBdd.cleanFilesystemLocksFromDb();
        accesBdd.closedb();
        BackendApi.showAppRunningNotification(StartupService.this,"Ecosys is running");



        startNetworkingServer();

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    public void startNetworkingServer() {
            ProcessExecutor.Function StartServer = new ProcessExecutor.Function() {
                @Override
                public void execute() {
                    try{
                        nt = new Networking(StartupService.this,getFilesDir().toString());
                        nt.setNetworkingCallForPicker(new ProcessExecutor.Function() {
                            @Override
                            public void execute() {
                                openFolderSelector();
                            }
                        });
                        nt.ServerMainLoop();
                    }catch (RuntimeException e){
                        // server already started
                        Log.d(TAG,"Server already started");
                    }

                }
            };

            ProcessExecutor.startProcess(StartServer);



            // Start the file picker activity
            // clean old network map at each app startup
            AccesBdd acces = new AccesBdd(StartupService.this);
            acces.cleanNetworkMap();



            ZeroConfService zc = new ZeroConfService(StartupService.this);


            ProcessExecutor.Function StartWatcher = new ProcessExecutor.Function() {
                @Override
                public void execute() {
                    AccesBdd acces = new AccesBdd(StartupService.this);
                    Map<String, Globals.SyncInfos> tasks = acces.listSyncAllTasks();
                    tasks.forEach((k,v)->{
                        Uri uri;

                        DocumentFile df;
                        if(v.isApp()){
                            /*uri = FileProvider.getUriForFile(
                                    StartupService.this,
                                    "com.ecosys.ecosys.fileprovider",
                                    new File(v.getPath())

                            );*/

                            df = DocumentFile.fromFile(new File(v.getPath()));
                            Log.d("Ecosys Server ","Starting to monitor (path format) : "+ v.getPath() +"\n Readable = "+df.canRead());

                        }else{
                            uri = Uri.parse(v.getPath());
                            df = DocumentFile.fromTreeUri(StartupService.this, uri);
                            Log.d("Ecosys Server ","Starting to monitor (path format) : "+ uri.getPath() +"\n Readable = "+df.canRead());
                        }

                        //uri = Uri.parse(v.getPath());
                        //df = DocumentFile.fromTreeUri(StartupService.this, uri);


                        FileSystem.startDirectoryMonitoring(
                                StartupService.this,
                                df,
                                v.getSecureId()
                        );



                    });

                    acces.closedb();
                }
            };

            ProcessExecutor.startProcess(StartWatcher);
        }


    public void openFolderSelector() {

        Handler handler = new Handler(Looper.getMainLooper());
        FolderPickerReceiver receiver = new FolderPickerReceiver(handler, this);

        Intent intent = new Intent(StartupService.this,SelectorActivity.class);
        intent.putExtra("flag","[PICK_FOLDER]");
        intent.putExtra("receiver", receiver);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        SelectorActivity.setCallBack(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter("com.ecosys.RESULT_ACTION");
        registerReceiver(resultReceiver, filter);
    }


    @Override
    public void onDestroy(){
        unregisterReceiver(resultReceiver);
    }


}





