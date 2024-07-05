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
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import android.provider.MediaStore;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileProvider extends ContentProvider {

    private static final String AUTHORITY = "com.qsync.fileprovider";

    private static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/");
    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    private static final int URI_IN_PROVIDER = 1;
    static {
        sUriMatcher.addURI(AUTHORITY, "/*", URI_IN_PROVIDER);
    }

    @Override
    public boolean onCreate() {
        return true;
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

        PackageManager packageManager = getContext().getPackageManager();
        String[] callingPackages = packageManager.getPackagesForUid(Binder.getCallingUid());
        AccesBdd accesBdd = new AccesBdd(context);


        // the calling app does not have its own QSync task
        // or the calling app is trying to go somewhere prohibited
        if(!accesBdd.checkAppExistenceFromName(callingPackages[0]) || !checkUriAccess(callingPackages[0],uri.getPath())){
            return null;
        }



        DocumentFile parent = DocumentFile.fromSingleUri(getContext(),uri);

        DocumentFile file;


        if (context == null || values == null || match != URI_IN_PROVIDER || !parent.exists() || parent.isFile())
            return null; // If context or values are null, return null indicating no insertion performed
        if(values.getAsString(MediaStore.MediaColumns.MIME_TYPE).equals("vnd.android.document/directory")){
            file = parent.createDirectory(values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME));


        }else {
            file = parent.createFile(
                    values.getAsString(MediaStore.MediaColumns.MIME_TYPE),
                    MediaStore.MediaColumns.DISPLAY_NAME
            );

        }

        return file.getUri();
    }


    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        int match = sUriMatcher.match(uri);

        Context context = getContext();

        PackageManager packageManager = getContext().getPackageManager();
        String[] callingPackages = packageManager.getPackagesForUid(Binder.getCallingUid());
        AccesBdd accesBdd = new AccesBdd(context);


        // the calling app does not have its own QSync task
        // or the calling app is trying to go somewhere prohibited
        if(!accesBdd.checkAppExistenceFromName(callingPackages[0]) || !checkUriAccess(callingPackages[0],uri.getPath())){
            return 0;
        }




        if (context == null || match != URI_IN_PROVIDER)
            return 0; // If context is null, return 0 indicating no deletion performed

        DocumentFile file = DocumentFile.fromSingleUri(getContext(),uri);
        return file.delete() ? 1 : 0; // Return 1 if deletion is successful, 0 otherwise

    }


    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        return 0;
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        Context context = getContext();

        if (context == null) {
            throw new IllegalStateException("Context cannot be null");
        }

        DocumentFile file = DocumentFile.fromSingleUri(context,uri);
        int match = sUriMatcher.match(uri);

        PackageManager packageManager = context.getPackageManager();
        String[] callingPackages = packageManager.getPackagesForUid(Binder.getCallingUid());
        AccesBdd accesBdd = new AccesBdd(context);

        if(
                        match != URI_IN_PROVIDER
                        || !file.exists()
                        || !file.isFile()
                        || !accesBdd.checkAppExistenceFromName(callingPackages[0])
                        || !checkUriAccess(callingPackages[0],uri.getPath())
        ){
            throw  new IllegalStateException("File does not exists, is not in your dedicated QSync folder or is a directory.");
        }


        int modeFlags = ParcelFileDescriptor.MODE_READ_ONLY;
        if ("r".equals(mode)) {
            modeFlags = ParcelFileDescriptor.MODE_READ_ONLY;
        } else if ("w".equals(mode) || "wt".equals(mode)) {
            modeFlags = ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_TRUNCATE;
        } else if ("wa".equals(mode)) {
            modeFlags = ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_APPEND;
        } else if ("rw".equals(mode)) {
            modeFlags = ParcelFileDescriptor.MODE_READ_WRITE;
        } else if ("rwt".equals(mode)) {
            modeFlags = ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_TRUNCATE;
        } else {
            throw new IllegalArgumentException("Unsupported mode: " + mode);
        }

        return context.getContentResolver().openFileDescriptor(uri, mode);
    }

    @Nullable
    public OutputStream openOutputStream(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        Context context = getContext();

        if (context == null) {
            throw new IllegalStateException("Context cannot be null");
        }

        DocumentFile file = DocumentFile.fromSingleUri(context,uri);
        int match = sUriMatcher.match(uri);

        PackageManager packageManager = context.getPackageManager();
        String[] callingPackages = packageManager.getPackagesForUid(Binder.getCallingUid());
        AccesBdd accesBdd = new AccesBdd(context);

        if(
                match != URI_IN_PROVIDER
                        || !file.exists()
                        || !file.isFile()
                        || !accesBdd.checkAppExistenceFromName(callingPackages[0])
                        || !checkUriAccess(callingPackages[0],uri.getPath())
        ){
            throw  new IllegalStateException("File does not exists, is not in your dedicated QSync folder or is a directory.");
        }


        int modeFlags = ParcelFileDescriptor.MODE_READ_ONLY;
        if ("r".equals(mode)) {
            modeFlags = ParcelFileDescriptor.MODE_READ_ONLY;
        } else if ("w".equals(mode) || "wt".equals(mode)) {
            modeFlags = ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_TRUNCATE;
        } else if ("wa".equals(mode)) {
            modeFlags = ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_APPEND;
        } else if ("rw".equals(mode)) {
            modeFlags = ParcelFileDescriptor.MODE_READ_WRITE;
        } else if ("rwt".equals(mode)) {
            modeFlags = ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_TRUNCATE;
        } else {
            throw new IllegalArgumentException("Unsupported mode: " + mode);
        }

        return context.getContentResolver().openOutputStream(uri);
    }


    @Nullable
    public InputStream openInputStream(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        Context context = getContext();

        if (context == null) {
            throw new IllegalStateException("Context cannot be null");
        }

        DocumentFile file = DocumentFile.fromSingleUri(context,uri);
        int match = sUriMatcher.match(uri);

        PackageManager packageManager = context.getPackageManager();
        String[] callingPackages = packageManager.getPackagesForUid(Binder.getCallingUid());
        AccesBdd accesBdd = new AccesBdd(context);

        if(
                match != URI_IN_PROVIDER
                        || !file.exists()
                        || !file.isFile()
                        || !accesBdd.checkAppExistenceFromName(callingPackages[0])
                        || !checkUriAccess(callingPackages[0],uri.getPath())
        ){
            throw  new IllegalStateException("File does not exists, is not in your dedicated QSync folder or is a directory.");
        }


        int modeFlags = ParcelFileDescriptor.MODE_READ_ONLY;
        if ("r".equals(mode)) {
            modeFlags = ParcelFileDescriptor.MODE_READ_ONLY;
        } else if ("w".equals(mode) || "wt".equals(mode)) {
            modeFlags = ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_TRUNCATE;
        } else if ("wa".equals(mode)) {
            modeFlags = ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_APPEND;
        } else if ("rw".equals(mode)) {
            modeFlags = ParcelFileDescriptor.MODE_READ_WRITE;
        } else if ("rwt".equals(mode)) {
            modeFlags = ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_TRUNCATE;
        } else {
            throw new IllegalArgumentException("Unsupported mode: " + mode);
        }

        return context.getContentResolver().openInputStream(uri);
    }


    /*
    *
    * Check if the provided uri is pointing to something in the folder assigned
    * to the calling app
    *
    *
    * */
    private boolean checkUriAccess(String packageName,String filePath){
        String rootPath = CONTENT_URI.getPath();
        return filePath.replace(rootPath,"").startsWith(packageName);

    }
}
