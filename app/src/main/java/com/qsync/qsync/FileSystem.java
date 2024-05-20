package com.qsync.qsync;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
                handleCreateEvent(acces, filePath, true);

            } else if (!previousState.get(filePath).lastModified.equals(fileInfo.lastModified)) {
                Log.d("FileMonitor", "Modified file detected: " + filePath);
                handleWriteEvent(acces,filePath);
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
                } else {
                    Log.d("FileMonitor", "Deleted file detected: " + filePath);
                    handleRemoveEvent(acces, filePath);

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

    private static void handleCreateEvent(AccesBdd acces, String path, boolean isDirectory) {
        String relativePath = path.replace(QSYNC_WRITEABLE_DIRECTORY, "");
        if (isDirectory) {
            Log.d(TAG, "Adding " + path + " to the directories to watch.");
            acces.createFolder(relativePath);
        } else {
            DeltaBinaire.Delta delta = DeltaBinaire.buildDelta(relativePath, path, 0, new byte[0]); // Assuming BuilDelta is adapted for Java
            acces.createFile(relativePath, path, "[ADD_TO_RETARD]");
        }
    }

    private static void handleWriteEvent(AccesBdd acces, String path) {
        String relativePath = path.replace(QSYNC_WRITEABLE_DIRECTORY, "");
        DeltaBinaire.Delta delta = DeltaBinaire.buildDelta(relativePath, path, acces.GetFileSizeFromBdd(relativePath), acces.getFileContent(relativePath));
        acces.updateFile(relativePath, delta); // Assuming updateFile is adapted for Java
    }

    private static void handleRemoveEvent(AccesBdd acces, String path) {
        String relativePath = path.replace(QSYNC_WRITEABLE_DIRECTORY, "");
        if (acces.wasFile(relativePath)) {
            acces.rmFile(relativePath);
        } else {
            acces.rmFolder(relativePath);
        }
    }
}

