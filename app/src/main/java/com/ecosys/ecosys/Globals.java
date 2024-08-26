/*
 * *
 *  * Created by Théo Mougnibas on 27/06/2024 17:18
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 27/06/2024 17:18
 *
 */

package com.ecosys.ecosys;

import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class Globals {


    public static final String VERSION = "0.0.3-Beta";

    public static class QEvent {
        public String Flag;
        public String FileType;
        public DeltaBinaire.Delta Delta;
        public String FilePath;
        public String NewFilePath;
        public String SecureId;
        public long VersionToPatch;

        // separate main fields of the request
        public static final byte[] FIELD_SEPARATOR = new byte[]{
                (byte) 0x00, (byte) 0xFF, (byte) 0x00, (byte) 0xFF,
                '-', '-', 'C', 'H', 'A', 'M', 'P', '-', '-', 'C', 'H', 'A', 'M', 'P', '-',
                (byte) 0xFF, (byte) 0x00, (byte) 0xFF, (byte) 0x00
        };

        // separate specific values of a field
        public static final byte[] VALUE_SEPARATOR = new byte[]{
                (byte) 0x00, (byte) 0xFF, (byte) 0x00, (byte) 0xFF,
                '-', '-', 'V', 'A', 'L', 'U', 'E', '-', '-', 'V', 'A', 'L', 'U', 'E', '-',
                (byte) 0xFF, (byte) 0x00, (byte) 0xFF, (byte) 0x00
        };

        // separate delta instructions
        public static final byte[] INSTRUCTION_SEPARATOR = new byte[]{
                (byte) 0x00, (byte) 0xFF, (byte) 0x00, (byte) 0xFF,
                '-', '-', 'I', 'N', 'S', 'T', 'R', 'U', 'C', 'T', 'I', 'O', 'N', '-', '-', 'I', 'N', 'S', 'T', 'R', 'U', 'C', 'T', 'I', 'O', 'N', '-',
                (byte) 0xFF, (byte) 0x00, (byte) 0xFF, (byte) 0x00
        };

        // Constructor
        public QEvent(String flag, String fileType, DeltaBinaire.Delta delta, String filePath, String newFilePath, String secureId, long versionToPatch) {
            this.Flag = flag;
            this.FileType = fileType;
            this.Delta = delta;
            this.FilePath = filePath;
            this.NewFilePath = newFilePath;
            this.SecureId = secureId;
            this.VersionToPatch = versionToPatch;
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
                    ", versionToPatch=" + this.VersionToPatch +
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

        public long getVersionToPatch() {
            return this.VersionToPatch;
        }

        public void setVersionToPatch(int versionToPatch) {
            this.VersionToPatch = versionToPatch;
        }

        public File serialize() throws IOException {

            // Create a temporary file
            File tempFile = File.createTempFile("event_serialized_", ".tmp");
            tempFile.deleteOnExit(); // Ensure the file is deleted when the JVM exits

            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(tempFile))) {
                byte[] fsephex = bytesToHex(FIELD_SEPARATOR).getBytes(StandardCharsets.UTF_8);
                // Write Flag and FileType
                bos.write(this.Flag.getBytes(StandardCharsets.UTF_8));
                bos.write(fsephex);
                bos.write(this.FileType.getBytes(StandardCharsets.UTF_8));
                bos.write(fsephex);

                // Write Delta if not null
                if (this.Delta != null) {
                    byte[] vsephex = bytesToHex(VALUE_SEPARATOR).getBytes(StandardCharsets.UTF_8);
                    byte[] instsephex = bytesToHex(INSTRUCTION_SEPARATOR).getBytes(StandardCharsets.UTF_8);
                    for (DeltaBinaire.DeltaInstruction instruction : this.Delta.Instructions) {
                        bos.write(instruction.InstructionType.getBytes(StandardCharsets.UTF_8));
                        bos.write(vsephex);

                        bos.write(instruction.Data);

                        bos.write(vsephex);

                        bos.write(String.valueOf(instruction.ByteIndex).getBytes(StandardCharsets.UTF_8));
                        if (this.Delta.Instructions.indexOf(instruction) < this.Delta.Instructions.size() - 1) {
                            bos.write(instsephex);
                        }

                    }
                    bos.write(fsephex);
                    bos.write(this.Delta.getFilePath().getBytes(StandardCharsets.UTF_8));
                } else {
                    bos.write(fsephex);
                }

                // Write other fields
                bos.write(fsephex);
                bos.write(this.FilePath.getBytes(StandardCharsets.UTF_8));
                bos.write(fsephex);
                bos.write(this.NewFilePath.getBytes(StandardCharsets.UTF_8));
                bos.write(fsephex);
                bos.write(String.valueOf(this.VersionToPatch).getBytes(StandardCharsets.UTF_8));
                bos.write(fsephex);
                bos.write(this.SecureId.getBytes(StandardCharsets.UTF_8));
            }

            // Return reference to the temporary file
            return tempFile;

        }

        public void deserializeQEvent(byte[] data) {
            ByteBuffer buffer = ByteBuffer.wrap(data);

            // Define separators as hexadecimal strings
            byte[] HEX_FIELD_SEPARATOR = bytesToHex(FIELD_SEPARATOR).getBytes(StandardCharsets.UTF_8);
            byte[] HEX_VALUE_SEPARATOR = bytesToHex(VALUE_SEPARATOR).getBytes(StandardCharsets.UTF_8);
            byte[] HEX_INSTRUCTION_SEPARATOR = bytesToHex(INSTRUCTION_SEPARATOR).getBytes(StandardCharsets.UTF_8);

            // Read Flag
            this.Flag = readString(buffer, HEX_FIELD_SEPARATOR,true);

            // Read FileType
            this.FileType = readString(buffer, HEX_FIELD_SEPARATOR,true);

            // Check if a binary delta is present
            if (buffer.remaining() > 0 && !startsWith(buffer, HEX_FIELD_SEPARATOR)) {
                List<DeltaBinaire.DeltaInstruction> instructions = new ArrayList<>();

                while (true) {
                    // Read Instruction Type
                    String instructionType = readString(buffer, HEX_VALUE_SEPARATOR,true);
                    Log.d("QEvent", "Instruction Type: " + instructionType);

                    // Read binary Data until VALUE_SEPARATOR
                    byte[] dataBytes = readBytesUntil(buffer, HEX_VALUE_SEPARATOR);


                    // Read Byte Index

                    // LAST SEPARATOR OF THE LAST INSTRUCTION IS IN FACT A FIELD SEPARATOR SO WE MUST
                    // USE A DERIVATIVE OF THE READSTRING METHOD

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    while (buffer.remaining() > 0) {
                        byte b = buffer.get();
                        baos.write(b);
                        if (startsWith(buffer, HEX_INSTRUCTION_SEPARATOR)) {
                            // don't skip last boundary as its existence reveal the presence of another instruction
                            break;

                            // last instruction of delta
                        }else if (startsWith(buffer,HEX_FIELD_SEPARATOR)){
                            buffer.position(buffer.position() + HEX_FIELD_SEPARATOR.length); // Skip the separator

                            break;
                        }
                    }
                    long byteIndex = Long.parseLong(baos.toString());
                    Log.d("QEvent", "Byte Index: " + byteIndex);

                    instructions.add(new DeltaBinaire.DeltaInstruction(instructionType, dataBytes, byteIndex));

                    // Check if there is another instruction (check for INSTRUCTION_SEPARATOR)
                    if (buffer.remaining() > 0 && startsWith(buffer, HEX_INSTRUCTION_SEPARATOR)) {
                        Log.d("QEvent", "Another instruction found");
                        buffer.position(buffer.position() + HEX_INSTRUCTION_SEPARATOR.length); // Skip the INSTRUCTION_SEPARATOR
                    } else {
                        break;
                    }
                }

                DeltaBinaire.Delta delta = new DeltaBinaire.Delta();
                delta.Instructions = instructions;

                // Read Delta File Path
                delta.setFilePath(readString(buffer, HEX_FIELD_SEPARATOR,true));

                this.Delta = delta;
            } else {
                buffer.position(buffer.position() + HEX_FIELD_SEPARATOR.length); // Skip FIELD_SEPARATOR for Delta
            }

            // Read other fields
            this.FilePath = readString(buffer, HEX_FIELD_SEPARATOR,true);
            this.NewFilePath = readString(buffer, HEX_FIELD_SEPARATOR,true);
            this.VersionToPatch = Long.parseLong(readString(buffer, HEX_FIELD_SEPARATOR,true));
            this.SecureId = readString(buffer, HEX_FIELD_SEPARATOR,true);
        }

        // Convert a hexadecimal string to a byte array
        private byte[] hexToByteArray(String hex) {
            int len = hex.length();
            byte[] data = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                        + Character.digit(hex.charAt(i+1), 16));
            }
            return data;
        }

        // Method to check if the buffer starts with a specific byte array
        private boolean startsWith(ByteBuffer buffer, byte[] separator) {
            if (buffer.remaining() < separator.length) {
                return false;
            }

            for (int i = 0; i < separator.length; i++) {
                if (buffer.get(buffer.position() + i) != separator[i]) {
                    return false;
                }
            }
            return true;
        }

        // Method to read a string from the buffer until a specific byte array is found
        private String readString(ByteBuffer buffer, byte[] separator,boolean skip) {

            // prevent stealing a byte from the next field if this one is empty
            if (startsWith(buffer, separator)) {
                buffer.position(buffer.position() + separator.length);
                return "";
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while (buffer.remaining() > 0) {
                byte b = buffer.get();
                baos.write(b);
                if (startsWith(buffer, separator)) {
                    if(skip){
                        buffer.position(buffer.position() + separator.length); // Skip the separator
                    }
                    break;
                }
            }
            Log.d("Globals",baos.toString());
            return baos.toString(); // Convert byte array to string using UTF-8 encoding
        }

        // Method to read bytes from the buffer until a specific byte array is found
        private byte[] readBytesUntil(ByteBuffer buffer, byte[] separator) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while (buffer.remaining() > 0) {
                byte b = buffer.get();
                baos.write(b);
                if (startsWith(buffer, separator)) {
                    buffer.position(buffer.position() + separator.length); // Skip the separator
                    break;
                }
            }
            return baos.toByteArray();
        }
        
        private static String bytesToHex(byte[] bytes) {
            StringBuilder hexString = new StringBuilder();
            for (byte b : bytes) {
                hexString.append(String.format("%02X", b));
            }
            return hexString.toString();
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


    public static class ToutEnUnConfig {
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
        mt.put("[CREATE]", "c");
        mt.put("[DELETE]", "d");
        mt.put("[PATCH]", "p");
        mt.put("[MOVE]", "m");

        return mt;

    }


    public static HashMap<String, String> modTypesReverse(){
        HashMap<String, String> mt = new HashMap<>();
        mt.put("c","[CREATE]");
        mt.put("d","[DELETE]");
        mt.put("p","[UPDATE]");
        mt.put("m","[MOVE]");

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


        public String toString(){
            return "{"+this.path+";"+this.secureId+";"+this.isApp+";"+this.name+";"+this.backup_mode+"}";
        }
    }

    /**
     * Replaces all special characters in the input string with underscores.
     * Only alphanumeric characters and underscores will remain in the final string.
     *
     * @param input the original string
     * @return the sanitized string
     */
    public static String replaceSpecialChars(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        // Replace all non-alphanumeric characters with an underscore
        return input.replaceAll("[^a-zA-Z0-9]", "_");
    }


}
