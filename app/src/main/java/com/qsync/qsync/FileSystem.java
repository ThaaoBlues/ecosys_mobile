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
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class FileSystem {

    private static final String TAG = "FileSystem";
    private static final String QSYNC_WRITEABLE_DIRECTORY = "path_to_your_directory"; // Specify your directory path here


    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final Map<String, FileInfo> previousState = new HashMap<>();
    private static final long POLLING_INTERVAL = 5000; // 5 seconds



    public static void startDirectoryMonitoring(Context context, DocumentFile directory) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkForChanges(context,directory);
                handler.postDelayed(this, POLLING_INTERVAL);
            }
        }, POLLING_INTERVAL);
    }

    private static void checkForChanges(Context context, DocumentFile directory) {
        Map<String, FileInfo> currentState = new HashMap<>();
        populateState(directory, currentState, directory.getUri().toString());
        final AccesBdd acces = new AccesBdd(context);
        acces.getSecureIdFromRootPath(directory.getUri().toString());

        // Check for new, modified, or renamed files
        for (Map.Entry<String, FileInfo> entry : currentState.entrySet()) {
            String filePath = entry.getKey();
            FileInfo fileInfo = entry.getValue();

            if (!previousState.containsKey(filePath)) {
                Log.d("FileMonitor", "New file detected: " + filePath);

                // no need to know if it is a directory as .fromTreeUri().Uri() would only represent
                // the whole directory from the root and be useless to calculate a relative path
                handleCreateEvent(acces, DocumentFile.fromSingleUri(context,fileInfo.uri));


            } else if (!previousState.get(filePath).lastModified.equals(fileInfo.lastModified)) {
                Log.d("FileMonitor", "Modified file detected: " + filePath);
                handleWriteEvent(context,acces,DocumentFile.fromSingleUri(context,fileInfo.uri));
            }

            // Handle renamed files
            FileInfo previousFileInfo = previousState.get(filePath);
            if (previousFileInfo != null && !previousFileInfo.uri.equals(fileInfo.uri)) {
                Log.d("FileMonitor", "Renamed file detected: " + previousFileInfo.uri + " -> " + fileInfo.uri);
            }
        }

        // Check for deleted files and subdirectories
        for (String filePath : previousState.keySet()) {
            if (!currentState.containsKey(filePath)) {
                FileInfo fileInfo = previousState.get(filePath);
                if (fileInfo.isDirectory) {
                    Log.d("FileMonitor", "Deleted subdirectory detected: " + filePath);
                    if(!acces.isSyncInBackupMode()){
                        handleRemoveEvent(acces,DocumentFile.fromTreeUri(context,fileInfo.uri) );
                    }else{
                        Log.d("FileMonitor","skipped file remove as sync is in backup mode.");
                    }
                } else {
                    Log.d("FileMonitor", "Deleted file detected: " + filePath);
                    if(!acces.isSyncInBackupMode()){
                        handleRemoveEvent(acces,DocumentFile.fromSingleUri(context,fileInfo.uri) );
                    }else{
                        Log.d("FileMonitor","skipped file remove as sync is in backup mode.");
                    }

                }
            }
        }

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
        }else{
            Log.d("Qsync Server : FileSystem",relativePath+" File already mapped.");
        }

    }

    private static void handleWriteEvent(Context context,AccesBdd acces, DocumentFile file) {

        try{
            String relativePath = PathUtils.getRelativePath(Uri.parse(acces.GetRootSyncPath()).getPath(),file.getUri().getPath());

            InputStream in = context.getContentResolver().openInputStream(file.getUri());
            DeltaBinaire.Delta delta = DeltaBinaire.buildDeltaFromInputStream(relativePath,
                    file.length(),
                    in,
                    acces.GetFileSizeFromBdd(relativePath),
                    acces.getFileContent(relativePath)
            );
            acces.updateFile(relativePath, delta); // Assuming updateFile is adapted for Java

        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

    }

    private static void handleRemoveEvent(AccesBdd acces, DocumentFile file) {
        String relativePath = PathUtils.getRelativePath(Uri.parse(acces.GetRootSyncPath()).getPath(),file.getUri().getPath());
        if (file.isFile()) {
            acces.rmFile(relativePath);
        } else {
            acces.rmFolder(relativePath);
        }
    }
}

