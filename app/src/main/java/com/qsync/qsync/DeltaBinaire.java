/*
 * *
 *  * Created by Théo Mougnibas on 27/06/2024 17:18
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 27/06/2024 17:18
 *
 */

package com.qsync.qsync;

import android.util.Log;
import java.io.*;
import java.util.*;

public class DeltaBinaire {
    private static final String TAG = "DeltaBinaire";

    static class DeltaInstruction implements Serializable{
        String InstructionType;
        byte[] Data;
        long ByteIndex;


        DeltaInstruction(String instructionType, byte[] data, long byteIndex) {
            this.InstructionType = instructionType;
            this.Data = data;
            this.ByteIndex = byteIndex;
        }

        @Override
        public String toString() {
            return "Instruction{" +
                    "instructionType='" + this.InstructionType + '\'' +
                    ", data=" + Arrays.toString(this.Data) +
                    ", byteIndex=" + this.ByteIndex +
                    '}';
        }




    }

    static class Delta implements Serializable{

        private String FilePath;



        public void setFilePath(String filePath) {
            this.FilePath = filePath;
        }
        public String getFilePath() {
            return FilePath;
        }
        List<DeltaInstruction> Instructions = new ArrayList<>();
        @Override
        public String toString() {
            return "Delta{" +
                    "instructions=" + this.Instructions +
                    ", filePath='" + this.FilePath + '\'' +
                    '}';
        }



    }


    /*public static Delta buildDelta(String relativePath, String absolutePath,
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
            long blockStartIndex = 0;
            int i = 0;

            Log.d(TAG, "old file size : " + oldFileSize);
            Log.d(TAG, "old file content : " + Arrays.toString(oldFileContent));
            Log.d(TAG, "new file size : " + newFileSize);

            while ((i < oldFileSize || byteIndex < newFileSize)) {
                int bytesRead = newFileInputStream.read(newFileBuff);
                if (bytesRead == -1) {
                    break;
                }

                byte oldFileBuff = (i < oldFileSize) ? oldFileContent[i] : 0;

                int deltaIndex = fileDelta.isEmpty() ? 0 : fileDelta.size() - 1;

                boolean byteIndexCond = fileDelta.isEmpty() || fileDelta.get(deltaIndex).ByteIndex != blockStartIndex;

                if ( ( (newFileBuff[0] != oldFileBuff) || i >= oldFileSize) && byteIndexCond) {

                    // To avoid implicit pointer usage, we create a new byte array in the argument
                    DeltaInstruction instruction = new DeltaInstruction("ab", new byte[]{
                            newFileBuff[0]
                    }, byteIndex);
                    fileDelta.add(instruction);

                    byteIndex++;
                } else {
                    if ((newFileBuff[0] != oldFileBuff) || i >= oldFileSize) {
                        fileDelta.get(deltaIndex).Data = Arrays.copyOf(fileDelta.get(deltaIndex).Data,
                                fileDelta.get(deltaIndex).Data.length + 1);

                        fileDelta.get(deltaIndex).Data[fileDelta.get(deltaIndex).Data.length-1] = newFileBuff[0];
                        byteIndex++;
                    } else {
                        byteIndex++;
                        blockStartIndex = byteIndex;
                    }
                }
                i++;
            }

            if (needsTruncature) {
                DeltaInstruction instruction = new DeltaInstruction("t", new byte[]{0}, newFileSize);
                fileDelta.add(instruction);
            }

            delta.Instructions.addAll(fileDelta);
            delta.setFilePath(relativePath);

            newFileInputStream.close();
        } catch (IOException e) {
            Log.e(TAG, "Error building delta: " + e.getMessage());
        }

        Log.d("Qsync Server : binary delta","build this delta : ");

        return delta;
    }*/

    private static int calculateBufferSize(long fileSize){
        // do not make chunk of more than 100Mb
        // we stop when we have a chunk size
        // that is the maximum one
        // that can still fit 2 times in the file
        int c = 100;
        if(fileSize > 100<<10){
            c = c<<10;
        }else{
            while((c<(fileSize>>2)) ){
                c = c << 1;
            }
        }


        return c;
    }
    public static DeltaBinaire.Delta buildDeltaFromInputStream(String filename,long newFileSize,InputStream newFileStream,
                                                             long oldFileSize, byte[] oldFileContent){
        Delta delta = new Delta();

        boolean needsTruncature = oldFileSize > newFileSize;

        int BUFF_SIZE = calculateBufferSize(newFileSize);
        Log.d(TAG,"BUFFER SIZE :"+BUFF_SIZE);

        byte[] newFileBuff = new byte[BUFF_SIZE];
        long byteIndex = 0;

        // blocking byte index is used to concatenate
        // multiples consecutives bytes change
        // into a single delta instruction
        long blockStartIndex = 0;
        int globalIndex = 0;

        Log.d(TAG, "old file size : " + oldFileSize);
        Log.d(TAG, "old file content : " + Arrays.toString(oldFileContent));
        Log.d(TAG, "new file size : " + newFileSize);


        while ((globalIndex < oldFileSize || byteIndex < newFileSize)) {
            int bytesRead = 0;
            try {
                bytesRead = newFileStream.read(newFileBuff);
                //Log.i("Qsync Server","byte index : "+byteIndex);
            } catch (IOException e) {
                Log.i("Qsync Server","Error while reading File for Largage Aerien"+e.getMessage());
            }
            if (bytesRead == -1 ) {
                break;
            }


            // we read a block and loop in the buffer that we just filled
            // it is quicker than reading byte by byte
            ByteArrayOutputStream dataStream = new ByteArrayOutputStream(bytesRead);
            for(int buff_index = 0; buff_index < bytesRead; buff_index++){
                byte oldFileBuff = (globalIndex < oldFileSize) ? oldFileContent[globalIndex] : 0;

                int deltaIndex = delta.Instructions.isEmpty() ? 0 : delta.Instructions.size() - 1;

                // when we hit the end of a block, blockStartIndex will be changed, so the condition will be true
                // and we will start to build a new instruction
                boolean byteIndexCond = delta.Instructions.isEmpty() || delta.Instructions.get(deltaIndex).ByteIndex != blockStartIndex;

                if ((newFileBuff[buff_index] != oldFileBuff) && byteIndexCond) {
                    //Log.d(TAG,String.valueOf(newFileBuff[buff_index]));
                    DeltaInstruction instruction = new DeltaInstruction("ab",
                            new byte[]{0},
                            byteIndex
                    );

                    dataStream.write(newFileBuff[buff_index]);

                    // To avoid implicit pointer usage, we create a new byte array in the argument

                    delta.Instructions.add(instruction);

                    byteIndex++;
                } else {
                    if (newFileBuff[buff_index] != oldFileBuff) {
                        //Log.d(TAG,String.valueOf(newFileBuff[buff_index]));

                        dataStream.write(newFileBuff[buff_index]);
                        //Log.d(TAG, String.valueOf(dataStream.toString()));


                        byteIndex++;


                        // check if we are at EOF and flush the buffer
                        if(byteIndex == newFileSize){
                            // this operation removes the need to clone a byte array to extend it at each new byte in a block
                            if(dataStream.size() > 0){
                                Log.d(TAG,"On flush tout");

                                delta.Instructions.get(deltaIndex).Data = dataStream.toByteArray();

                                dataStream.reset();
                            }
                        }

                        // end of block with changes or just not any changes for this byte
                    } else {
                        // check if we are at the end of a block change ( i.e the data stream has bytes in it )
                        // this operation removes the need to clone a byte array to extend it at each new byte in a block

                        if(dataStream.size() > 0){
                            Log.d(TAG,"On flush tout");

                            delta.Instructions.get(deltaIndex).Data = dataStream.toByteArray();

                            dataStream.reset();
                        }
                        byteIndex++;
                        // we prepare to start a new block
                        blockStartIndex = byteIndex;
                    }
                }

                globalIndex += 1;

                //Log.i("Qsync Server","Loop condition : "+(i < oldFileSize || byteIndex < newFileSize));

            }
        }



        if (needsTruncature) {
            DeltaInstruction instruction = new DeltaInstruction(
                    "t",
                    new byte[]{0},
                    newFileSize
            );

            delta.Instructions.add(instruction);
        }

        delta.setFilePath(filename);

        Log.d("FILEDELTA","build this delta : "+delta);

        return delta;
    }


    public static void patchFile(Delta delta) {
        try {
            File file = new File(delta.getFilePath());
            RandomAccessFile fileHandler = new RandomAccessFile(file, "rw");

            for (DeltaInstruction instruction : delta.Instructions) {
                switch (instruction.InstructionType) {
                    case "ab":
                        fileHandler.seek(instruction.ByteIndex);
                        for(int i = 0;i<instruction.Data.length;i++){
                            fileHandler.write(instruction.Data[i]);
                        }
                        //fileHandler.write(instruction.Data);
                        break;
                    case "t":
                        fileHandler.setLength(instruction.ByteIndex);
                        break;
                }
            }

            fileHandler.close();
        } catch (IOException e) {
            Log.e(TAG, "Error patching file: " + e.getMessage());
        }
    }



}
