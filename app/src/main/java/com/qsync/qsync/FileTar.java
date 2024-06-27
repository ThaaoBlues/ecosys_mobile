/*
 * *
 *  * Created by Théo Mougnibas on 27/06/2024 17:18
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 27/06/2024 17:18
 *
 */

package com.qsync.qsync;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

public class FileTar {
    private static final String TAG = "Qsync File Tar";

    public static void tarFiles(Context context, Uri[] fileUris, String outputTarFilePath) {
        try (FileOutputStream fos = new FileOutputStream(outputTarFilePath);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             TarArchiveOutputStream taos = new TarArchiveOutputStream(bos)) {

            byte[] buffer = new byte[1024];
            for (Uri uri : fileUris) {
                Log.d(TAG, "Reading file: " + uri.toString());

                try (InputStream is = context.getContentResolver().openInputStream(uri);
                     BufferedInputStream bis = new BufferedInputStream(is)) {

                    String fileName = getFileName(context, uri);
                    if (fileName != null) {
                        // Calculate the size of the file
                        ParcelFileDescriptor fileDescriptor = context.getContentResolver().openFileDescriptor(uri , "r");
                        long fileSize = fileDescriptor.getStatSize();
                        Log.d("Qsync Server","FILE SIZE !!!! = "+fileSize);
                        TarArchiveEntry tarEntry = new TarArchiveEntry(fileName);
                        tarEntry.setSize(fileSize);
                        taos.putArchiveEntry(tarEntry);


                        int len;
                        while ((len = bis.read(buffer)) > 0) {
                            taos.write(buffer, 0, len);
                        }
                        taos.closeArchiveEntry();
                    } else {
                        Log.e(TAG, "Could not get file name for uri: " + uri.toString());
                    }
                }
            }
            taos.finish();

        } catch (Exception e) {
            Log.e(TAG, "Error tarring files", e);
        }
    }

    public static void untarFile(String tarFilePath, String destDirectory) {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        try (FileInputStream fis = new FileInputStream(tarFilePath);
             TarArchiveInputStream tais = new TarArchiveInputStream(fis)) {

            TarArchiveEntry entry;
            byte[] buffer = new byte[1024];
            while ((entry = (TarArchiveEntry) tais.getNextEntry()) != null) {
                File newFile = new File(destDirectory, entry.getName());
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    new File(newFile.getParent()).mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile);
                         BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                        int len;
                        while ((len = tais.read(buffer)) > 0) {
                            bos.write(buffer, 0, len);
                        }
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error untarring file", e);
        }
    }

    private static String getFileName(Context context, Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            String[] projection = { android.provider.MediaStore.Images.Media.DISPLAY_NAME };
            try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DISPLAY_NAME);
                    result = cursor.getString(index);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting file name", e);
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result != null ? result.lastIndexOf('/') : -1;
            if (cut != -1 && result != null) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private static long calculateFileSize(InputStream is) throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) > 0) {
            byteArrayOutputStream.write(buffer, 0, len);
        }
        return byteArrayOutputStream.size();
    }

    private static String getFilePath(Context context, Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            String[] projection = { android.provider.MediaStore.Images.Media.DATA };
            try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATA);
                    result = cursor.getString(index);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting file path", e);
            }
        }
        if (result == null) {
            result = uri.getPath();
        }
        return result;
    }
}
