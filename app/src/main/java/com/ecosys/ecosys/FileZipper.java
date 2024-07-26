/*
 * *
 *  * Created by Théo Mougnibas on 27/06/2024 17:18
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 27/06/2024 17:18
 *
 */

package com.ecosys.ecosys;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

public class FileZipper {
    private static final String TAG = "Ecosys File Zipper";

    public static void zipFiles(Context context, Uri[] fileUris, String outputZipFilePath) {
        try (FileOutputStream fos = new FileOutputStream(outputZipFilePath);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(bos)) {

            byte[] buffer = new byte[1024];
            for (Uri uri : fileUris) {

                Log.d(TAG, "Reading file: " + uri.toString());

                try (InputStream is = context.getContentResolver().openInputStream(uri);
                     BufferedInputStream bis = new BufferedInputStream(is)) {

                    String fileName = getFileName(context, uri);
                    if (fileName != null) {
                        ZipArchiveEntry zipEntry = new ZipArchiveEntry(fileName);
                        zaos.putArchiveEntry(zipEntry);

                        int len;
                        while ((len = bis.read(buffer)) > 0) {
                            zaos.write(buffer, 0, len);
                        }
                        zaos.closeArchiveEntry();
                    } else {
                        Log.e(TAG, "Could not get file name for uri: " + uri.toString());
                    }
                }
            }
            zaos.finish();

        } catch (Exception e) {
            Log.e(TAG, "Error zipping files", e);
        }
    }

    public static void unzipFile(String zipFilePath, String destDirectory) {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        try (FileInputStream fis = new FileInputStream(zipFilePath);
             ZipArchiveInputStream zais = new ZipArchiveInputStream(fis)) {

            ZipArchiveEntry entry;
            byte[] buffer = new byte[1024];
            while ((entry = zais.getNextZipEntry()) != null) {
                File newFile = new File(destDirectory, entry.getName());
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    new File(newFile.getParent()).mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile);
                         BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                        int len;
                        while ((len = zais.read(buffer)) > 0) {
                            bos.write(buffer, 0, len);
                        }
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error unzipping file", e);
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
}
