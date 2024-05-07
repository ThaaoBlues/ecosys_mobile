package com.qsync.qsync;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.util.*;

public class DeltaBinaire {
    private static final String TAG = "DeltaBinaire";

    static class DeltaInstruction {
        String instructionType;
        byte[] data;
        long byteIndex;


        DeltaInstruction(String instructionType, byte[] data, long byteIndex) {
            this.instructionType = instructionType;
            this.data = data;
            this.byteIndex = byteIndex;
        }


    }

    static class Delta {

        private String filePath;

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }
        public String getFilePath() {
            return filePath;
        }
        List<DeltaInstruction> instructions = new ArrayList<>();
    }


    public static Delta buildDelta(String relativePath, String absolutePath,
                                   long oldFileSize, byte[] oldFileContent) {
        Delta delta = new Delta();

        try {
            File newFile = new File(absolutePath);
            FileInputStream newFileInputStream = new FileInputStream(newFile);
            long newFileSize = newFile.length();

            boolean needsTruncature = oldFileSize > newFileSize;

            byte[] newFileBuff = new byte[1];
            List<DeltaInstruction> fileDelta = new ArrayList<>();
            long byteIndex = 0;

            // blocking byte index is used to concatenate
            // multiples consecutives bytes change
            // into a single delta instruction
            long blockingByteIndex = 0;
            int i = 0;

            Log.d(TAG, "old file size : " + oldFileSize);
            Log.d(TAG, "old file content : " + Arrays.toString(oldFileContent));

            while ((i < oldFileSize || byteIndex < newFileSize)) {
                int bytesRead = newFileInputStream.read(newFileBuff);
                if (bytesRead == -1 || newFileBuff[0] == 0) {
                    break;
                }

                byte oldFileBuff = (i < oldFileSize) ? oldFileContent[i] : 0;

                int deltaIndex = fileDelta.isEmpty() ? 0 : fileDelta.size() - 1;

                boolean byteIndexCond = fileDelta.isEmpty() || fileDelta.get(deltaIndex).byteIndex != blockingByteIndex;

                if ((newFileBuff[0] != oldFileBuff) && byteIndexCond) {

                    // To avoid implicit pointer usage, we create a new byte array in the argument
                    DeltaInstruction instruction = new DeltaInstruction("ab", new byte[]{newFileBuff[0]}, byteIndex);
                    fileDelta.add(instruction);

                    byteIndex++;
                } else {
                    if (newFileBuff[0] != oldFileBuff) {
                        fileDelta.get(deltaIndex).data = Arrays.copyOf(fileDelta.get(deltaIndex).data,
                                fileDelta.get(deltaIndex).data.length + 1);

                        fileDelta.get(deltaIndex).data[fileDelta.get(deltaIndex).data.length-1] = newFileBuff[0];
                        byteIndex++;
                    } else {
                        byteIndex++;
                        blockingByteIndex = byteIndex;
                    }
                }
                i++;
            }

            if (needsTruncature) {
                DeltaInstruction instruction = new DeltaInstruction("t", new byte[]{0}, newFileSize);
                fileDelta.add(instruction);
            }

            delta.instructions.addAll(fileDelta);
            delta.setFilePath(relativePath);

            newFileInputStream.close();
        } catch (IOException e) {
            Log.e(TAG, "Error building delta: " + e.getMessage());
        }

        Log.d("FILEDELTA","build this delta : ");

        return delta;
    }

    public static void patchFile(Delta delta) {
        try {
            File file = new File(delta.getFilePath());
            RandomAccessFile fileHandler = new RandomAccessFile(file, "rw");

            for (DeltaInstruction instruction : delta.instructions) {
                switch (instruction.instructionType) {
                    case "ab":
                        fileHandler.seek(instruction.byteIndex);
                        fileHandler.write(instruction.data);
                        break;
                    case "t":
                        fileHandler.setLength(instruction.byteIndex);
                        break;
                }
            }

            fileHandler.close();
        } catch (IOException e) {
            Log.e(TAG, "Error patching file: " + e.getMessage());
        }
    }
}
