package com.qsync.qsync;

import android.content.Context;
import android.util.Log;
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

    public static void startWatcher(final Context context, final String rootPath) {
        // Initialize the database connection
        final AccesBdd acces = new AccesBdd();
        acces.initConnection();
        acces.getSecureIdFromRootPath(rootPath);

        // Start the filesystem watcher
        FileAlterationObserver observer = new FileAlterationObserver(rootPath);
        observer.addListener(new FileAlterationListener() {
            @Override
            public void onStart(FileAlterationObserver observer) {}

            @Override
            public void onDirectoryCreate(File directory) {
                handleCreateEvent(acces, directory.getAbsolutePath(), true);
            }

            @Override
            public void onDirectoryChange(File directory) {}

            @Override
            public void onDirectoryDelete(File directory) {}

            @Override
            public void onFileCreate(File file) {
                handleCreateEvent(acces, file.getAbsolutePath(), false);
            }

            @Override
            public void onFileChange(File file) {
                handleWriteEvent(acces, file.getAbsolutePath());
            }

            @Override
            public void onFileDelete(File file) {
                handleRemoveEvent(acces, file.getAbsolutePath());
            }

            @Override
            public void onStop(FileAlterationObserver observer) {}
        });

        FileAlterationMonitor monitor = new FileAlterationMonitor(1000); // Check every second
        monitor.addObserver(observer);

        try {
            monitor.start();
        } catch (Exception e) {
            Log.e(TAG, "Error starting file watcher: " + e.getMessage());
        }
    }

    private static void handleCreateEvent(AccesBdd acces, String path, boolean isDirectory) {
        String relativePath = path.replace(QSYNC_WRITEABLE_DIRECTORY, "");
        try {
            if (isDirectory) {
                Log.d(TAG, "Adding " + path + " to the directories to watch.");
                acces.createFolder(relativePath);
            } else {
                DeltaBinaire.Delta delta = DeltaBinaire.buildDelta(relativePath, path, 0, new byte[0]); // Assuming BuilDelta is adapted for Java
                acces.createFile(relativePath, path, "[ADD_TO_RETARD]");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error handling create event: " + e.getMessage());
        }
    }

    private static void handleWriteEvent(AccesBdd acces, String path) {
        String relativePath = path.replace(QSYNC_WRITEABLE_DIRECTORY, "");
        try {
            DeltaBinaire.Delta delta = DeltaBinaire.buildDelta(relativePath, path, acces.getFileSizeFromBdd(relativePath), acces.getFileContent(relativePath));
            acces.updateFile(relativePath, delta); // Assuming updateFile is adapted for Java
        } catch (IOException e) {
            Log.e(TAG, "Error handling write event: " + e.getMessage());
        }
    }

    private static void handleRemoveEvent(AccesBdd acces, String path) {
        String relativePath = path.replace(QSYNC_WRITEABLE_DIRECTORY, "");
        try {
            if (acces.wasFile(relativePath)) {
                acces.rmFile(relativePath);
            } else {
                acces.rmFolder(relativePath);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error handling remove event: " + e.getMessage());
        }
    }
}

