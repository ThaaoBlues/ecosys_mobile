package com.qsync.qsync;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class PathUtils {

    public static String joinPaths(String path1, String path2) {
        File file1 = new File(path1);
        File file2 = new File(file1, path2);
        return file2.getPath();
    }

    public static String moveFileToDownloads(Context context, String filePath) {
        File sourceFile = new File(filePath);
        File destinationFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), sourceFile.getName());
        String destinationPath = destinationFile.getPath();
        if (!sourceFile.exists()) {
            // Source file doesn't exist
            return null;
        }

        try {
            FileInputStream inputStream = new FileInputStream(sourceFile);
            FileOutputStream outputStream = new FileOutputStream(destinationFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            inputStream.close();
            outputStream.close();

            // Delete the original file after successful copy
            sourceFile.delete();

            return destinationPath;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String copyFileToExternalStorage(Context context, String sourceFilePath, String dest_folder) {
        File sourceFile = new File(sourceFilePath);

        if (!sourceFile.exists()) {
            // Source file doesn't exist
            return null;
        }

        File externalStorageDirectory = context.getExternalFilesDir(null);
        File destinationDirectory = new File(externalStorageDirectory, dest_folder);

        if (!destinationDirectory.exists()) {
            // Create the directory if it doesn't exist
            if (!destinationDirectory.mkdirs()) {
                // Failed to create directory
                return null;
            }
        }

        File destinationFile = new File(destinationDirectory, sourceFile.getName());
        String destinationPath = destinationFile.getPath();

        try {
            FileInputStream inputStream = new FileInputStream(sourceFile);
            FileOutputStream outputStream = new FileOutputStream(destinationFile);
            FileChannel inChannel = inputStream.getChannel();
            FileChannel outChannel = outputStream.getChannel();
            inChannel.transferTo(0, inChannel.size(), outChannel);

            inputStream.close();
            outputStream.close();
            inChannel.close();
            outChannel.close();

            return destinationPath;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

    }
}
