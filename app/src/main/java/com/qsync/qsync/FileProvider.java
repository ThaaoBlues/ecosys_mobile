/*
 * *
 *  * Created by Théo Mougnibas on 27/06/2024 17:18
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 27/06/2024 17:18
 *
 */

package com.qsync.qsync;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class FileProvider extends ContentProvider {

    private static final String AUTHORITY = "com.qsync.fileprovider";
    private static final String FOLDER_NAME = "apps";

    private static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + FOLDER_NAME);
    private static final int FOLDER_CODE = 1;
    private static final int FILE_CODE = 2;
    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        sUriMatcher.addURI(AUTHORITY, FOLDER_NAME, FOLDER_CODE);
        sUriMatcher.addURI(AUTHORITY, FOLDER_NAME + "/*", FILE_CODE);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    public OutputStream openOutputStream(@NonNull Uri uri) throws FileNotFoundException {
        int match = sUriMatcher.match(uri);
        if (match == FOLDER_CODE) {
            throw new FileNotFoundException("Directories cannot be opened for reading or writing");
        } else if (match == FILE_CODE) {
            Context context = getContext();
            if (context == null)
                throw new FileNotFoundException("Context is null");
            String fileName = uri.getLastPathSegment();
            File folder = new File(context.getFilesDir(), FOLDER_NAME);
            if (!folder.exists()) {
                folder.mkdirs();
            }
            File file = new File(folder, fileName);
            try {
                return new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                throw e;
            }
        } else {
            throw new FileNotFoundException("Unknown URI: " + uri);
        }
    }



    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        int match = sUriMatcher.match(uri);
        Context context = getContext();
        if (context == null || values == null)
            return null; // If context or values are null, return null indicating no insertion performed
        switch (match) {
            case FOLDER_CODE:
                // Extract folder name and MIME type from the ContentValues
                String folderName = values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME);
                String mimeType = values.getAsString(MediaStore.MediaColumns.MIME_TYPE);
                // Create a File object representing the folder
                File folder = new File(context.getFilesDir(), folderName);
                // Create the folder
                if (folder.mkdirs()) {
                    return Uri.withAppendedPath(uri, folderName); // Return the URI of the newly created folder
                } else {
                    return null; // Return null if folder creation fails
                }
            default:
                throw new IllegalArgumentException("Unsupported URI for insertion: " + uri);
        }
    }


    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        int match = sUriMatcher.match(uri);
        Context context = getContext();
        if (context == null)
            return 0; // If context is null, return 0 indicating no deletion performed
        switch (match) {
            case FOLDER_CODE:
                // Get the folder name from the URI
                String folderName = uri.getLastPathSegment();
                // Create a File object representing the folder
                File folder = new File(context.getFilesDir(), folderName);
                // Delete the folder and its contents recursively
                return deleteFolder(folder) ? 1 : 0; // Return 1 if deletion is successful, 0 otherwise
            case FILE_CODE:
                // Get the file name from the URI
                String fileName = uri.getLastPathSegment();
                // Create a File object representing the file
                File file = new File(context.getFilesDir(), fileName);
                // Delete the file
                return file.delete() ? 1 : 0; // Return 1 if deletion is successful, 0 otherwise
            default:
                throw new IllegalArgumentException("Unsupported URI for deletion: " + uri);
        }
    }
    private boolean deleteFolder(File folder) {
        if (folder.isDirectory()) {
            // List all files and subdirectories in the folder
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    // Recursively delete files and subdirectories
                    deleteFolder(file);
                }
            }
        }
        // Delete the folder itself
        return folder.delete();
    }


    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        return 0;
    }
}
