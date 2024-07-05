/*
 * *
 *  * Created by Théo Mougnibas on 05/07/2024 18:40
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 05/07/2024 18:39
 *
 */

package com.qsync.qsync;

import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.util.Map;

public class StartupService extends Service implements FolderPickerCallback{


    private Networking nt;
    private String TAG ="QSync Server : StartupService";

    public StartupService() {
    }

    @Override
    public void onFolderPicked(Uri uri) {

        Log.d(TAG,"Retrieveed uri : "+uri.getPath());
        DocumentFile directory = DocumentFile.fromTreeUri( StartupService.this, uri);

        if (directory != null && directory.isDirectory()) {
            AccesBdd acces = new AccesBdd( StartupService.this);

            acces.SetSecureId(nt.getTmpSecureIdForCreation());
            acces.CreateSyncFromOtherEnd(directory.getUri().toString(),nt.getTmpSecureIdForCreation());
            acces.closedb();

            FileSystem.startDirectoryMonitoring( StartupService.this,directory);

            nt.setSetupDlLock(false);


        }
    }



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Perform background tasks
        // Service will continue to run until explicitly stopped


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
                    nt = new Networking(StartupService.this,getFilesDir().toString());
                    nt.setNetworkingCallForPicker(new ProcessExecutor.Function() {
                        @Override
                        public void execute() {
                            openFolderSelector();
                        }
                    });
                    nt.ServerMainLoop();
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
                    Map<String, Globals.SyncInfos> tasks = acces.ListSyncAllTasks();
                    tasks.forEach((k,v)->{

                        DocumentFile df = DocumentFile.fromTreeUri(StartupService.this, Uri.parse(v.getPath()));
                        Log.d("Qsync Server ","Starting to monitor : "+ v.getPath()+"\n Readable = "+df.canRead());

                        FileSystem.startDirectoryMonitoring(
                                StartupService.this,
                                df
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

}





