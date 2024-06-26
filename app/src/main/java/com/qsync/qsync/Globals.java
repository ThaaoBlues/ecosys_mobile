package com.qsync.qsync;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Globals {



    public static class QEvent {
        public String Flag;
        public String FileType;
        public DeltaBinaire.Delta Delta;
        public String FilePath;
        public String NewFilePath;
        public String SecureId;

        // Constructor
        public QEvent(String flag, String fileType, DeltaBinaire.Delta delta, String filePath, String newFilePath, String secureId) {
            this.Flag = flag;
            this.FileType = fileType;
            this.Delta = delta;
            this.FilePath = filePath;
            this.NewFilePath = newFilePath;
            this.SecureId = secureId;
        }

        @Override
        public String toString() {
            return "QEvent{" +
                    "flag='" + this.Flag + '\'' +
                    ", fileType='" + this.FileType + '\'' +
                    ", delta=" + this.Delta +
                    ", filePath='" + this.FilePath + '\'' +
                    ", newFilePath='" + this.NewFilePath + '\'' +
                    ", secureId='" + this.SecureId + '\'' +
                    '}';
        }

        // Getters and setters
        public String getFlag() {
            return Flag;
        }

        public void setFlag(String flag) {
            this.Flag = flag;
        }

        public String getFileType() {
            return FileType;
        }

        public void setFileType(String fileType) {
            this.FileType = fileType;
        }

        public DeltaBinaire.Delta getDelta() {
            return Delta;
        }

        public void setDelta(DeltaBinaire.Delta delta) {
            this.Delta = delta;
        }

        public String getFilePath() {
            return FilePath;
        }

        public void setFilePath(String filePath) {
            this.FilePath = filePath;
        }

        public String getNewFilePath() {
            return NewFilePath;
        }

        public void setNewFilePath(String newFilePath) {
            this.NewFilePath = newFilePath;
        }

        public String getSecureId() {
            return SecureId;
        }

        public void setSecureId(String secureId) {
            this.SecureId = secureId;
        }


        public String serialize() {
            StringBuilder EventStringBuilder = new StringBuilder();
            EventStringBuilder.append(this.Flag);
            EventStringBuilder.append(";");
            EventStringBuilder.append(this.FileType);
            EventStringBuilder.append(";");

            if(this.Delta != null) {
                for (DeltaBinaire.DeltaInstruction instruction : this.Delta.Instructions) {
                    EventStringBuilder.append(instruction.InstructionType).append(",");
                    for (int data : instruction.Data) {
                        EventStringBuilder.append(data).append(",");
                    }
                    EventStringBuilder.append(instruction.ByteIndex).append("|");
                }
                // Remove the last "|"
                if (EventStringBuilder.length() > 0) {
                    EventStringBuilder.setLength(EventStringBuilder.length() - 1);
                }
                EventStringBuilder.append(";");
                EventStringBuilder.append(this.Delta.getFilePath());

            }else {
                EventStringBuilder.append(";");
            }

            EventStringBuilder.append(";");
            EventStringBuilder.append(this.FilePath);
            EventStringBuilder.append(";");
            EventStringBuilder.append(this.NewFilePath);
            EventStringBuilder.append(";");
            EventStringBuilder.append(this.SecureId);

            return EventStringBuilder.toString();



        }

        public void deserializeQEvent(String data) {
            String[] parts = data.split(";");

            // check if a binary delta is present
            if(!parts[2].isEmpty()){
                String[] instructionParts = parts[2].split("\\|");
                List<DeltaBinaire.DeltaInstruction> instructions = new ArrayList<>();
                for (String instructionStr : instructionParts) {
                    String[] instructionData = instructionStr.split(",");
                    byte[] dataBytes = new byte[instructionData.length - 2];
                    for (int i = 1; i < instructionData.length - 1; i++) {
                        dataBytes[i - 1] = (byte) Integer.parseInt(instructionData[i]);
                    }
                    long byteIndex = Long.parseLong(instructionData[instructionData.length - 1]);
                    instructions.add(new DeltaBinaire.DeltaInstruction(instructionData[0], dataBytes, byteIndex));
                }
                DeltaBinaire.Delta delta = new DeltaBinaire.Delta();
                delta.Instructions = instructions;
                delta.setFilePath(parts[3]);

                this.Delta = delta;

            }
            this.Flag = parts[0];
            this.FileType = parts[1];
            this.FilePath = parts[4];
            this.NewFilePath = parts[5];
            this.SecureId = parts[6];
        }
    }



    public interface GenArrayInterface<T> {
        void add(T val);
        T get(int i);
        int size();
        void popLast();

        void del(int i);
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

        public void del(int i){
            items.remove(i);
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

    public static class MinGenConfig {
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


    public static class SyncInfos {
        private String path;
        private String secureId;
        private boolean backup_mode;

        private String name;

        private boolean isApp;

        public SyncInfos(String path, String secureId) {
            this.path = path;
            this.secureId = secureId;
            this.backup_mode = false;
            this.isApp = false;
            this.name = "prout";
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
        public Boolean getBackup_mode() {
            return backup_mode;
        }

        public void setBackup_mode(Boolean backup_mode) {
            this.backup_mode = backup_mode;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isApp() {
            return isApp;
        }

        public void setApp(boolean app) {
            isApp = app;
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
