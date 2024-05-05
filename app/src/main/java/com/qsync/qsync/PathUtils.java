package com.qsync.qsync;

import java.io.File;

public class PathUtils {

    public static String joinPaths(String path1, String path2) {
        File file1 = new File(path1);
        File file2 = new File(file1, path2);
        return file2.getPath();
    }
}
