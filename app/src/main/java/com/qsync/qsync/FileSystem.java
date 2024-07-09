/*
 * *
 *  * Created by Théo Mougnibas on 27/06/2024 17:18
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 27/06/2024 17:18
 *
 */

package com.qsync.qsync;


import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class FileSystem {

    private static final String TAG = "FileSystem";

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final Map<String, FileInfo> previousState = new HashMap<>();
    public static final long POLLING_INTERVAL = 5000; // 5 seconds

    private static Context context;


    public static void startDirectoryMonitoring(Context mContext, DocumentFile directory,String secureId) {

        context = mContext;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                final AccesBdd acces_nonclosing = new AccesBdd(context);
                acces_nonclosing.SetSecureId(secureId);

                // as we use polling to watch the filesystem,
                // even if a new directory is sent by another end
                // it will be taken as part of the usual map
                // and nothing has to be done differently

                // we are checking if the filesystem is being patch later
                // as we still need to make some processing to update
                // the filesystem map
                checkForChanges(context,directory,acces_nonclosing);

                handler.postDelayed(this, POLLING_INTERVAL);
            }
        }, POLLING_INTERVAL);
    }

    private static void avoidGhostDevices(AccesBdd acces){
        Globals.GenArray<String> devices = new Globals.GenArray<>();

        devices = acces.getSyncOnlineDevices();

        for(int i=0;i<devices.size();i++){
            Networking.checkDeviceAvailability(acces.getDeviceIP(devices.get(i)),devices.get(i));
        }
    }

    private static void checkForChanges(Context context, DocumentFile directory,AccesBdd acces) {
        Map<String, FileInfo> currentState = new HashMap<>();
        populateState(directory, currentState, directory.getUri().toString());

        Log.d(TAG,"IsThisFileSystemBeingPatched()="+acces.IsThisFileSystemBeingPatched());
        if(!acces.IsThisFileSystemBeingPatched()){
            // Check for new, modified, or renamed files
            for (Map.Entry<String, FileInfo> entry : currentState.entrySet()) {
                String filePath = entry.getKey();
                FileInfo fileInfo = entry.getValue();

                if (!previousState.containsKey(filePath)) {
                    Log.d("FileMonitor", "New file detected: " + filePath);

                    avoidGhostDevices(acces);


                    // no need to know if it is a directory as .fromTreeUri().Uri() would only represent
                    // the whole directory from the root and be useless to calculate a relative path
                    handleCreateEvent(acces, DocumentFile.fromSingleUri(context,fileInfo.uri));


                } else if (!previousState.get(filePath).lastModified.equals(fileInfo.lastModified) && !fileInfo.isDirectory) {
                    Log.d("FileMonitor", "Modified file detected: " + filePath);
                    avoidGhostDevices(acces);
                    handleWriteEvent(context,acces,DocumentFile.fromSingleUri(context,fileInfo.uri));
                }

                // Handle renamed files
                FileInfo previousFileInfo = previousState.get(filePath);
                if (previousFileInfo != null && !previousFileInfo.uri.equals(fileInfo.uri)) {
                    avoidGhostDevices(acces);
                    Log.d("FileMonitor", "Renamed file detected: " + previousFileInfo.uri + " -> " + fileInfo.uri);
                }
            }

            // Check for deleted files and subdirectories
            for (String filePath : previousState.keySet()) {
                if (!currentState.containsKey(filePath)) {
                    FileInfo fileInfo = previousState.get(filePath);
                    avoidGhostDevices(acces);
                    // same reason as for creation, Uri is not usefull in Tree mode for directories
                    Log.d(TAG,"File suppression detected "+filePath);
                    handleRemoveEvent(acces,DocumentFile.fromSingleUri(context,fileInfo.uri) );




                }
            }
        }



        // independant of the origin of the event (filesystem patch or user interraction )
        // Update the previous state to the current state
        previousState.clear();
        previousState.putAll(currentState);

    }

    private static void populateState(DocumentFile directory, Map<String, FileInfo> state, String basePath) {
        for (DocumentFile file : directory.listFiles()) {
            String filePath = basePath + "/" + file.getName();
            FileInfo fileInfo = new FileInfo(file.getUri(), file.lastModified(), file.isDirectory());
            state.put(filePath, fileInfo);

            if (file.isDirectory()) {
                populateState(file, state, filePath);
            }
        }
    }

    private static class FileInfo {
        Uri uri;
        Long lastModified;
        boolean isDirectory;

        FileInfo(Uri uri, Long lastModified, boolean isDirectory) {
            this.uri = uri;
            this.lastModified = lastModified;
            this.isDirectory = isDirectory;
        }
    }

    private static void handleCreateEvent(AccesBdd acces, DocumentFile file) {

        Log.d(TAG,file.getUri().toString());
        Log.d(TAG,file.getUri().getPath());
        String relativePath = PathUtils.getRelativePath(Uri.parse(acces.GetRootSyncPath()).getPath(),file.getUri().getPath());
        // check if file isn't already mapped as this method may be called at each startup
        if(!acces.checkFileExists(relativePath)){
            if (file.isDirectory()) {
                Log.d(TAG, "Adding " + relativePath + " to the directories to watch.");

                acces.createFolder(relativePath,"[ADD_TO_RETARD]");
            } else {
                Log.d(TAG, "Adding " + relativePath + " to the files to watch.");

                acces.createFile(relativePath,file, "[ADD_TO_RETARD]");
            }


            try{
                DeltaBinaire.Delta delta = null;
                if(file.isFile()){
                    delta = DeltaBinaire.buildDeltaFromInputStream(
                            file.getName(),
                            file.length(),
                            context.getContentResolver().openInputStream(file.getUri()),
                            0,
                            new byte[]{0}
                    );
                }

                Globals.QEvent event = new Globals.QEvent(
                        "[CREATE]",
                        file.isDirectory() ? "folder" : "file",
                        delta,
                        relativePath,
                        "",
                        acces.GetSecureId()
                );

                Globals.GenArray<Globals.QEvent> queue = new Globals.GenArray<>();

                queue.add(event);
                Log.d(TAG,"Number of online devices for this task : "+String.valueOf(acces.getSyncOnlineDevices().size()));
                ProcessExecutor.Function func = new ProcessExecutor.Function() {
                    @Override
                    public void execute() {
                        BackendApi.showLoadingNotification(context,"Sending update to other devices");

                        Networking.sendDeviceEventQueueOverNetwork(acces.getSyncOnlineDevices(),acces.GetSecureId(),queue);
                        BackendApi.discardLoadingNotification(context);
                    }
                };

                ProcessExecutor.startProcess(func);

            }catch (IOException e){
                Log.e(TAG,"Unable to open file to build binary delta",e);
            }
        }else{
            Log.d("Qsync Server : FileSystem",relativePath+" File already mapped.");
        }





    }

    private static void handleWriteEvent(Context context,AccesBdd acces, DocumentFile file) {

            ProcessExecutor.Function f = new ProcessExecutor.Function() {
                @Override
                public void execute() {

                    try {
                        String relativePath = PathUtils.getRelativePath(Uri.parse(acces.GetRootSyncPath()).getPath(), file.getUri().getPath());

                        InputStream in = context.getContentResolver().openInputStream(file.getUri());
                        DeltaBinaire.Delta delta = DeltaBinaire.buildDeltaFromInputStream(relativePath,
                                file.length(),
                                in,
                                acces.GetFileSizeFromBdd(relativePath),
                                acces.getFileContent(relativePath)
                        );
                        acces.updateFile(relativePath, delta,file,true);


                        Globals.QEvent event = new Globals.QEvent(
                                "[UPDATE]",
                                "file",
                                delta,
                                relativePath,
                                "",
                                acces.GetSecureId()
                        );

                        BackendApi.showLoadingNotification(context,"Sending update to other devices");
                        Log.d(TAG,"Sending update to other devices");

                        Globals.GenArray<Globals.QEvent> queue = new Globals.GenArray<>();

                        queue.add(event);


                        Networking.sendDeviceEventQueueOverNetwork(acces.getSyncOnlineDevices(), acces.GetSecureId(), queue);

                        BackendApi.discardLoadingNotification(context);
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }
            };

            ProcessExecutor.startProcess(f);


    }

    private static void handleRemoveEvent(AccesBdd acces, DocumentFile file) {
        String relativePath = PathUtils.getRelativePath(Uri.parse(acces.GetRootSyncPath()).getPath(),file.getUri().getPath());
        if (file.isFile()) {
            acces.rmFile(relativePath);
        } else {
            acces.rmFolder(relativePath);
        }

        // don't send suppression event if sync is in backup mode


        if(!acces.isSyncInBackupMode()) {

            Globals.QEvent event = new Globals.QEvent(
                    "[REMOVE]",
                    file.isDirectory() ? "folder" : "file",
                    null,
                    relativePath,
                    "",
                    acces.GetSecureId()
            );

            ProcessExecutor.Function f = new ProcessExecutor.Function() {
                @Override
                public void execute() {

                    BackendApi.showLoadingNotification(context,"Sending update to other devices");

                    Globals.GenArray<Globals.QEvent> queue = new Globals.GenArray<>();

                    queue.add(event);

                    Networking.sendDeviceEventQueueOverNetwork(acces.getSyncOnlineDevices(), acces.GetSecureId(), queue);

                    BackendApi.discardLoadingNotification(context);

                }
            };

            ProcessExecutor.startProcess(f);
        }

    }
}

