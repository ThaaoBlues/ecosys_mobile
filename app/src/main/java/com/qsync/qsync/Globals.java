package com.qsync.qsync;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Globals {



    public static class QEvent {
        public String flag;
        public String fileType;
        public DeltaBinaire.Delta delta;
        public String filePath;
        private String newFilePath;
        private String secureId;

        // Constructor
        public QEvent(String flag, String fileType, DeltaBinaire.Delta delta, String filePath, String newFilePath, String secureId) {
            this.flag = flag;
            this.fileType = fileType;
            this.delta = delta;
            this.filePath = filePath;
            this.newFilePath = newFilePath;
            this.secureId = secureId;
        }

        // Getters and setters
        public String getFlag() {
            return flag;
        }

        public void setFlag(String flag) {
            this.flag = flag;
        }

        public String getFileType() {
            return fileType;
        }

        public void setFileType(String fileType) {
            this.fileType = fileType;
        }

        public DeltaBinaire.Delta getDelta() {
            return delta;
        }

        public void setDelta(DeltaBinaire.Delta delta) {
            this.delta = delta;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getNewFilePath() {
            return newFilePath;
        }

        public void setNewFilePath(String newFilePath) {
            this.newFilePath = newFilePath;
        }

        public String getSecureId() {
            return secureId;
        }

        public void setSecureId(String secureId) {
            this.secureId = secureId;
        }
    }



    public interface GenArrayInterface<T> {
        void add(T val);
        T get(int i);
        int size();
        void popLast();
    }

    public static class GenArray<T> implements GenArrayInterface<T> {
        private List<T> items;

        public GenArray() {
            items = new ArrayList<>();
        }

        public void add(T val) {
            items.add(val);
        }

        @Override
        public T get(int i) {
            return items.get(i);
        }

        @Override
        public int size() {
            return items.size();
        }

        @Override
        public void popLast() {
            items.remove(items.size() - 1);
        }


        public boolean isEmpty() {
            return items.isEmpty();
        }

        public String join(String delimiter) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) {
                    sb.append(delimiter);
                }
                T item = items.get(i);
                if (item instanceof String) {
                    sb.append((String) item);
                } else {
                    sb.append(item.toString());
                }
            }
            return sb.toString();
        }
    }


    public class ToutEnUnConfig {
        public String appName;
        public String appDownloadUrl;
        public boolean needsInstaller;
        public String appLauncherPath;
        public String appInstallerPath;
        public String appUninstallerPath;
        public String appSyncDataFolderPath;
        public String appDescription;
        public String appIconURL;

        // Constructor
        public ToutEnUnConfig(String appName, String appDownloadUrl, boolean needsInstaller, String appLauncherPath,
                              String appInstallerPath, String appUninstallerPath, String appSyncDataFolderPath,
                              String appDescription, String appIconURL) {
            this.appName = appName;
            this.appDownloadUrl = appDownloadUrl;
            this.needsInstaller = needsInstaller;
            this.appLauncherPath = appLauncherPath;
            this.appInstallerPath = appInstallerPath;
            this.appUninstallerPath = appUninstallerPath;
            this.appSyncDataFolderPath = appSyncDataFolderPath;
            this.appDescription = appDescription;
            this.appIconURL = appIconURL;
        }

        // Getters and Setters
        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        public String getAppDownloadUrl() {
            return appDownloadUrl;
        }

        public void setAppDownloadUrl(String appDownloadUrl) {
            this.appDownloadUrl = appDownloadUrl;
        }

        public boolean isNeedsInstaller() {
            return needsInstaller;
        }

        public void setNeedsInstaller(boolean needsInstaller) {
            this.needsInstaller = needsInstaller;
        }

        public String getAppLauncherPath() {
            return appLauncherPath;
        }

        public void setAppLauncherPath(String appLauncherPath) {
            this.appLauncherPath = appLauncherPath;
        }

        public String getAppInstallerPath() {
            return appInstallerPath;
        }

        public void setAppInstallerPath(String appInstallerPath) {
            this.appInstallerPath = appInstallerPath;
        }

        public String getAppUninstallerPath() {
            return appUninstallerPath;
        }

        public void setAppUninstallerPath(String appUninstallerPath) {
            this.appUninstallerPath = appUninstallerPath;
        }

        public String getAppSyncDataFolderPath() {
            return appSyncDataFolderPath;
        }

        public void setAppSyncDataFolderPath(String appSyncDataFolderPath) {
            this.appSyncDataFolderPath = appSyncDataFolderPath;
        }

        public String getAppDescription() {
            return appDescription;
        }

        public void setAppDescription(String appDescription) {
            this.appDescription = appDescription;
        }

        public String getAppIconURL() {
            return appIconURL;
        }

        public void setAppIconURL(String appIconURL) {
            this.appIconURL = appIconURL;
        }
    }

    public class GrapinConfig {
        public String appName;
        public String appSyncDataFolderPath;
        public boolean needsFormat;
        public String[] supportedPlatforms;
        public String appDescription;
        public String appIconURL;

        // Constructor
        public GrapinConfig(String appName, String appSyncDataFolderPath, boolean needsFormat, String[] supportedPlatforms,
                            String appDescription, String appIconURL) {
            this.appName = appName;
            this.appSyncDataFolderPath = appSyncDataFolderPath;
            this.needsFormat = needsFormat;
            this.supportedPlatforms = supportedPlatforms;
            this.appDescription = appDescription;
            this.appIconURL = appIconURL;
        }

        // Getters and Setters
        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        public String getAppSyncDataFolderPath() {
            return appSyncDataFolderPath;
        }

        public void setAppSyncDataFolderPath(String appSyncDataFolderPath) {
            this.appSyncDataFolderPath = appSyncDataFolderPath;
        }

        public boolean isNeedsFormat() {
            return needsFormat;
        }

        public void setNeedsFormat(boolean needsFormat) {
            this.needsFormat = needsFormat;
        }

        public String[] getSupportedPlatforms() {
            return supportedPlatforms;
        }

        public void setSupportedPlatforms(String[] supportedPlatforms) {
            this.supportedPlatforms = supportedPlatforms;
        }

        public String getAppDescription() {
            return appDescription;
        }

        public void setAppDescription(String appDescription) {
            this.appDescription = appDescription;
        }

        public String getAppIconURL() {
            return appIconURL;
        }

        public void setAppIconURL(String appIconURL) {
            this.appIconURL = appIconURL;
        }
    }

    public class MinGenConfig {
        public String appName;
        public int appId;
        public String binPath;
        public String type;
        public String secureId;
        public String uninstallerPath;

        // Constructor
        public MinGenConfig(String appName, int appId, String binPath, String type, String secureId, String uninstallerPath) {
            this.appName = appName;
            this.appId = appId;
            this.binPath = binPath;
            this.type = type;
            this.secureId = secureId;
            this.uninstallerPath = uninstallerPath;
        }

        // Getters and Setters
        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        public int getAppId() {
            return appId;
        }

        public void setAppId(int appId) {
            this.appId = appId;
        }

        public String getBinPath() {
            return binPath;
        }

        public void setBinPath(String binPath) {
            this.binPath = binPath;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getSecureId() {
            return secureId;
        }

        public void setSecureId(String secureId) {
            this.secureId = secureId;
        }

        public String getUninstallerPath() {
            return uninstallerPath;
        }

        public void setUninstallerPath(String uninstallerPath) {
            this.uninstallerPath = uninstallerPath;
        }
    }

    // exists returns whether the given file or directory exists
    public static boolean exists(String path) {
        File file = new File(path);
        return file.exists();
    }


    public static HashMap<String, String> modTypes(){
        HashMap<String, String> mt = new HashMap<>();
        mt.put("creation", "c");
        mt.put("delete", "d");
        mt.put("patch", "p");
        mt.put("move", "m");

        return mt;

    }


    public static HashMap<String, String> modTypesReverse(){
        HashMap<String, String> mt = new HashMap<>();
        mt.put("c","creation");
        mt.put("d","delete");
        mt.put("p","patch");
        mt.put("m","move");

        return mt;

    }


    // SyncInfos.java
    public static class SyncInfos {
        private String path;
        private String secureId;

        public SyncInfos(String path, String secureId) {
            this.path = path;
            this.secureId = secureId;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getSecureId() {
            return secureId;
        }

        public void setSecureId(String secureId) {
            this.secureId = secureId;
        }
    }


    // LinkDevice.java
    public class LinkDevice {
        private String secureId;
        private boolean isConnected;

        public LinkDevice(String secureId, boolean isConnected) {
            this.secureId = secureId;
            this.isConnected = isConnected;
        }

        public String getSecureId() {
            return secureId;
        }

        public void setSecureId(String secureId) {
            this.secureId = secureId;
        }

        public boolean isConnected() {
            return isConnected;
        }

        public void setConnected(boolean connected) {
            isConnected = connected;
        }
    }




}
