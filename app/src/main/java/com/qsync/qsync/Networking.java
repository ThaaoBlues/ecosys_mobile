/*
 * *
 *  * Created by Théo Mougnibas on 27/06/2024 17:18
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 27/06/2024 17:18
 *
 */

package com.qsync.qsync;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.documentfile.provider.DocumentFile;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

public class Networking {

    public static final int HEADER_LENGTH = 83;
    private static final String TAG = "QSync Server";
    private static ServerSocket serverSocket;
    private static Socket clientSocket;
    private static Context context;
    private static String QSYNC_WRITABLE_DIRECTORY;

    private static ActivityResultLauncher<Intent> selectFolderLauncher;

    private static String tmpSecureIdForCreation;

    private static boolean setupDlLock;

    private static ProcessExecutor.Function networkingCallForPicker;

    Networking(Context mcontext, String mFilesDir) {
        context = mcontext;
        QSYNC_WRITABLE_DIRECTORY = mFilesDir;

    }



    public static String getDeviceHostname(String ip_addr) {

        final String[] hostname = {""};
        ProcessExecutor.Function gethn = new ProcessExecutor.Function() {
            @Override
            public void execute() {
                try {
                    // Get the local host address
                    InetAddress inetAddress = InetAddress.getByName(ip_addr);
                    hostname[0] = inetAddress.getHostName();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }
        };

        ProcessExecutor.startProcess(gethn);

        return hostname[0];

    }

    public static String getTmpSecureIdForCreation() {
        return tmpSecureIdForCreation;
    }

    public static void setTmpSecureIdForCreation(String tmpSecureIdForCreation) {
        Networking.tmpSecureIdForCreation = tmpSecureIdForCreation;
    }

    public static boolean isSetupDlLock() {
        return setupDlLock;
    }

    public static void setSetupDlLock(boolean setupDlLock) {
        Networking.setupDlLock = setupDlLock;
    }


    public void ServerMainLoop(){
        try {
            serverSocket = new ServerSocket(8274);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        while (true) {
            try {
                clientSocket = serverSocket.accept();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            Log.d(TAG, "Client connected");
            new Thread(new ClientHandler(clientSocket)).start();
        }
    }

    public ActivityResultLauncher<Intent> getSelectFolderLauncher() {
        return selectFolderLauncher;
    }

    public void setSelectFolderLauncher(ActivityResultLauncher<Intent> selectFolderLauncher) {
        this.selectFolderLauncher = selectFolderLauncher;
    }

    public ProcessExecutor.Function getNetworkingCallForPicker() {
        return networkingCallForPicker;
    }

    public void setNetworkingCallForPicker(ProcessExecutor.Function networkingCallForPicker) {
        this.networkingCallForPicker = networkingCallForPicker;
    }

    public static class ClientHandler implements Runnable {
        private Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {


                Log.i(TAG,"In request handler !");
                AccesBdd acces = new AccesBdd(context);

                // get the device id and secure sync id from header
                char[] header_buff = new char[HEADER_LENGTH];
                InputStreamReader inputStreamReader = new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                bufferedReader.read(header_buff, 0, HEADER_LENGTH);
                String device_id;
                String secure_id;
                Log.i(TAG,"Header : "+ Arrays.toString(header_buff));


                try {
                   device_id = new String(header_buff, 0, HEADER_LENGTH).split(";")[0];
                   secure_id = new String(header_buff, 0, HEADER_LENGTH).split(";")[1];
                   Log.d(TAG,"successfully parsed ids from request");
                }catch (ArrayIndexOutOfBoundsException e){
                    Log.i(TAG,"Received a malformed request"+ Arrays.toString(header_buff));
                    return;
                }
                acces.SetSecureId(secure_id);

                // in case of a link packet, the device is not yet registered in the database
                // so it can throw an error
                if (acces.IsDeviceLinked(device_id)) {
                    // makes sure it is marked as connected
                    if (!acces.GetDevicedbState(device_id)) {
                        // needs split as RemoteAddr ads port to the address
                        acces.setDevicedbState(device_id, true, clientSocket.getInetAddress().getHostAddress());
                    }
                }

                // read the body of the request and store it in a buffer
                StringBuilder body_buff = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    body_buff.append(line);
                }

                Log.d(TAG, "Request body : " + body_buff);



                Globals.QEvent data = new Globals.QEvent(
                        "",
                        "",
                        null,
                        "",
                        "",
                        ""
                );
                data.deserializeQEvent(body_buff.toString());

                // check if this is a regular file event of a special request
                Log.d(TAG, "RECEIVING EVENT : " + data);

                DataOutputStream outputStream = new DataOutputStream(clientSocket.getOutputStream());

                switch (data.getFlag()) {
                    case "[MODIFICATION_DONE]":
                        setEventNetworkLockForDevice(device_id, false);
                        acces.removeDeviceFromRetardOneFile(device_id,data.FilePath,Long.parseLong(data.FileType));
                        break;

                    case "[BEGIN_UPDATE]":

                        if(acces.IsDeviceLinked(device_id)) {
                            acces.SetFileSystemPatchLockState(device_id, true);
                        }

                        break;

                    case "[END_OF_UPDATE]":
                        if(acces.IsDeviceLinked(device_id)) {
                            acces.SetFileSystemPatchLockState(device_id, false);
                        }

                        break;
                    case "[SETUP_DL]":

                        if(acces.IsDeviceLinked(device_id)){
                            Log.d(TAG, "GOT FLAG, BUILDING SETUP QUEUE...");
                            buildSetupQueue(secure_id, device_id);
                            String response = acces.getMyDeviceId() + ";" + acces.GetSecureId() + ";" + "[MODIFICATION_DONE]";
                            outputStream.writeBytes(response);
                        }

                        break;
                    case "[LINK_DEVICE]":
                        // as this is triggered by another machine telling this one to create a sync task,
                        // we must prepare the environnement to accept this
                        // by creating a new sync task with the same path (replace this later by asking to the user)
                        // and same secure_id
                        Log.d(TAG, "Initializing env to welcome the other end folder content");
                        acces.SetSecureId(secure_id);
                        setSetupDlLock(false);
                        if(Objects.equals(data.FileType, "[APPLICATION]")){






                            if(acces.checkAppExistenceFromName(data.getFilePath())){
                                String app_path = PathUtils.joinPaths(
                                        context.getExternalFilesDir(null).getPath(),
                                        data.getFilePath()
                                );
                                acces.getSecureIdFromRootPath(app_path);

                                long remote_task_creation_timestamp = Long.parseLong(data.getNewFilePath());
                                long local_task_creation_timestamp = acces.getSyncCreationDate();

                                if(remote_task_creation_timestamp > local_task_creation_timestamp){
                                    acces.updateSyncId(app_path,secure_id);
                                }else{
                                    // older sync on the other device, ask to link but the opposite way
                                    // so the dowload is made on this device
                                    sendLinkDeviceRequest(acces.getDeviceIP(device_id),acces);
                                    return;
                                }


                            }else{

                                Intent intent = new Intent(context,SelectAppToLinkActivity.class);
                                context.startActivity(intent);
                            }





                        }else{
                           //String path = BackendApi.askInput("[CHOOSELINKPATH]", "Choose a path where new sync files will be stored.",context,true);

                            setTmpSecureIdForCreation(secure_id);
                            setSetupDlLock(true);

                            ProcessExecutor.startProcess(networkingCallForPicker);

                        }

                        // wait for the file picker callback to unlocke
                        while (setupDlLock){

                        }

                        Log.d(TAG, "Linking device : " + device_id);
                        String ipAddr = clientSocket.getInetAddress().getHostAddress();
                        outputStream.flush();
                        outputStream.close();
                        acces.LinkDevice(device_id, ipAddr);
                        acces.setDevicedbState(device_id,true);

                        askSetupDownloadToOtherEnd(ipAddr,acces);


                        break;
                    case "[UNLINK_DEVICE]":
                        acces.UnlinkDevice(device_id);
                        break;
                    case "[OTDL]":
                        // signle-file largage aerien
                        handleLargageAerien(data,
                                clientSocket.getInetAddress().getHostAddress(),
                                R.string.largage_aerien_msg + data.FilePath,
                                false,
                                false
                                );
                        break;

                    case "[MOTDL]":
                        //unzip and then do the usual things on all the files
                        Log.d(TAG,"Handling MOTDL...");
                        handleLargageAerien(data,
                                clientSocket.getInetAddress().getHostAddress(),
                                R.string.multi_largage_aerien_msg + clientSocket.getInetAddress().getHostAddress() ,
                                false,
                                true
                        );
                        break;
                    default:
                        // regular file event
                        handleEvent(device_id, data);
                        // send back a modification confirmation, so the other end can remove this machine device_id
                        // from concerned sync task retard entries
                        String response = acces.getMyDeviceId() + ";" + acces.GetSecureId() + ";" + "[MODIFICATION_DONE]";
                        outputStream.writeBytes(response);
                        break;
                }
                outputStream.flush();
                outputStream.close();
                acces.closedb();
            } catch (IOException e) {
                Log.e(TAG, "Error in ClientHandler: ", e);
            }
        }
    }

    // used to process a request when it is a regular file event
    public static void handleEvent(String deviceId, Globals.QEvent event) {

        AccesBdd acces = new AccesBdd(context);
        acces.SetSecureId(event.getSecureId());
        // First, we lock the filesystem watcher to prevent a ping-pong effect
        acces.SetFileSystemPatchLockState(deviceId, true);

        // Get the necessary data from the JSON event
        String relativePath = event.FilePath;


        String newRelativePath = event.NewFilePath;
        String absoluteFilePath = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            absoluteFilePath = Paths.get(acces.GetRootSyncPath(), relativePath).toString();
        }

        String eventType = event.Flag;
        String fileType = event.FileType;
        Log.d(TAG,absoluteFilePath);

        DocumentFile root;
        if(acces.isApp()){
            root = DocumentFile.fromFile(new File(acces.GetRootSyncPath()));
        }else{
            root = DocumentFile.fromTreeUri(context,Uri.parse(acces.GetRootSyncPath()));
        }


        // as in backup mode, files can be supressed freely
        // the remote device can still have a file that no longer exists
        // in this filesystem


        // to be sure no bugs are being propagated if somehow a relative path
        // build as failed and kept a leading "/"
        if(relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        event.setFilePath(relativePath);


        if(!(acces.isSyncInBackupMode() && !acces.checkFileExists(relativePath))){
            switch (eventType) {
                case "[MOVE]":
                    acces.move(relativePath, newRelativePath, fileType);


                    StringBuilder newParentRelativePath = new StringBuilder();

                    for(String part : newRelativePath.split("/")){
                        if(!part.isEmpty()){
                            newParentRelativePath.append(part).append("/");
                        }
                    }


                    if(!acces.isApp()){
                        DocumentFile newParentFile = DocumentFile.fromTreeUri(
                                context,
                                Uri.withAppendedPath(
                                        root.getUri(),
                                        newParentRelativePath.toString()
                                )
                        );



                        DocumentFile currentFile;
                        if(fileType.equals("file")){
                            currentFile = DocumentFile.fromSingleUri(
                                    context,
                                    Uri.withAppendedPath(root.getUri(),relativePath)
                            );
                        }else{
                            currentFile = DocumentFile.fromTreeUri(
                                    context,
                                    Uri.withAppendedPath(root.getUri(),relativePath)
                            );
                        }

                        moveInFilesystem(currentFile,newParentFile,!fileType.equals("file"));

                    }else{
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                Files.copy(
                                        Paths.get(absoluteFilePath),
                                        Paths.get(PathUtils.joinPaths(root.getUri().getPath(),newRelativePath)),
                                        StandardCopyOption.REPLACE_EXISTING
                                        );
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                    }


                    break;
                case "[REMOVE]":
                    if ("file".equals(fileType)) {
                        acces.rmFile(absoluteFilePath);
                        removeFromFilesystem(root,relativePath,!acces.isApp(),false);
                    } else {
                        acces.rmFolder(absoluteFilePath);
                        removeFromFilesystem(root,relativePath,!acces.isApp(),true);
                    }
                    break;
                case "[CREATE]":

                    DocumentFile newFile = createFileWithContentResolver(root,relativePath,!fileType.equals("file"));

                    if ("file".equals(fileType)) {
                        acces.createFile(
                                relativePath,
                                newFile,
                                "[SENT_FROM_OTHER_DEVICE]");

                        if(event.Delta != null){
                            Log.d(TAG,"File create came with a delta, using patchFile().");
                            event.setFilePath(relativePath);
                            DeltaBinaire.patchFile(event,!acces.isApp(),context);
                        }

                    } else {
                        acces.createFolder(relativePath,"");
                    }


                    break;
                case "[UPDATE]":


                    acces.incrementFileVersion(relativePath);


                    // build path and get actual reference to the file we want
                    String[] parts = event.FilePath.split("/");
                    DocumentFile file = root;

                    // Traverse the path and create directories if necessary
                    // go to the last element of the relative path
                    for (int i = 0; i < parts.length; i++) {
                        if (!parts[i].isEmpty()) {
                            DocumentFile nextFile = file.findFile(parts[i]);
                            //Log.d(TAG,nextFile.getUri().toString());
                            file = nextFile;
                        }
                    }

                    event.getDelta().setFilePath(absoluteFilePath);
                    DeltaBinaire.Delta delta = event.getDelta();
                    delta.setFilePath(absoluteFilePath);
                    event.setDelta(delta);

                    DeltaBinaire.patchFile(event,!acces.isApp(),context);
                    acces.updateCachedFile(relativePath,file,!acces.isApp(),absoluteFilePath);

                    break;
                default:
                    Log.e("HandleEventAdapter", "Received unknown event type: " + eventType);
                    break;
            }

            try {
                // so the other end can clear its retard table entries
                sendModificationDoneEvent(
                        clientSocket.getInetAddress().getHostAddress(),
                        acces,
                        relativePath,
                        acces.getFileLastVersionId(relativePath)
                );
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }


        // Release the filesystem lock

        // make sure the polling interval tick at least one time to update filesystem map
        // so when we release the lock no event is triggered
        try {
            Thread.sleep(FileSystem.POLLING_INTERVAL);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        acces.SetFileSystemPatchLockState(deviceId, false);

        acces.closedb();
    }

    public static void askSetupDownloadToOtherEnd(String ipAddress,AccesBdd acces) throws IOException{
        Socket socket = new Socket(ipAddress, 8274);
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        String ser_event = new Globals.QEvent(
                "[SETUP_DL]",
                "",
                null,
                "",
                "",
                acces.GetSecureId()
        ).serialize();

        Log.d(TAG,"serialized Event");

        BufferedOutputStream bos = new BufferedOutputStream(out);
        // Send the message
        StringBuilder reqBuilder = new StringBuilder();
        reqBuilder.append(acces.getMyDeviceId()).append(";").append(acces.GetSecureId()).append(ser_event);
        bos.write(reqBuilder.toString().getBytes(StandardCharsets.UTF_8));
        bos.flush();

        Log.d(TAG,"Event Sent");


        bos.close();
        out.close();
        socket.close();
    }


    public static void sendStartOfUpdateEvent(String ipAddress,AccesBdd acces) throws IOException {
        // Initialize the connection
        Socket socket = new Socket(ipAddress, 8274);
        DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

        String ser_event = new Globals.QEvent(
                "[BEGIN_UPDATE]",
                "",
                null,
                "",
                "",
                acces.GetSecureId()
        ).serialize();

        Log.d(TAG,"serialized Event");

        BufferedOutputStream bos = new BufferedOutputStream(outputStream);
        // Send the message
        StringBuilder reqBuilder = new StringBuilder();
        reqBuilder.append(acces.getMyDeviceId()).append(";").append(acces.GetSecureId()).append(ser_event);
        bos.write(reqBuilder.toString().getBytes(StandardCharsets.UTF_8));
        bos.flush();

        // Close the connection
        bos.close();
        outputStream.close();
        socket.close();
    }


    public static void sendEndOfUpdateEvent(String ipAddress,AccesBdd acces) throws IOException {
        // Initialize the connection
        Socket socket = new Socket(ipAddress, 8274);
        DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

        String ser_event = new Globals.QEvent(
                "[END_OF_UPDATE]",
                "",
                null,
                "",
                "",
                acces.GetSecureId()
        ).serialize();

        Log.d(TAG,"serialized Event");

        BufferedOutputStream bos = new BufferedOutputStream(outputStream);
        // Send the message
        StringBuilder reqBuilder = new StringBuilder();
        reqBuilder.append(acces.getMyDeviceId()).append(";").append(acces.GetSecureId()).append(ser_event);
        bos.write(reqBuilder.toString().getBytes(StandardCharsets.UTF_8));
        bos.flush();

        // Close the connection
        bos.close();
        outputStream.close();
        socket.close();
    }

    public static void sendModificationDoneEvent(String ipAddress,AccesBdd acces,String relativePath,long versionId) throws IOException {
        // Initialize the connection
        Socket socket = new Socket(ipAddress, 8274);
        DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

        String ser_event = new Globals.QEvent(
                "[MODIFICATION_DONE]",
                String.valueOf(versionId),
                null,
                relativePath,
                "",
                acces.GetSecureId()
        ).serialize();

        Log.d(TAG,"serialized Event");

        BufferedOutputStream bos = new BufferedOutputStream(outputStream);
        // Send the message
        StringBuilder reqBuilder = new StringBuilder();
        reqBuilder.append(acces.getMyDeviceId()).append(";").append(acces.GetSecureId()).append(ser_event);
        bos.write(reqBuilder.toString().getBytes(StandardCharsets.UTF_8));
        bos.flush();

        // Close the connection
        bos.close();
        outputStream.close();
        socket.close();
    }


    public static void sendDeviceEventQueueOverNetwork(Globals.GenArray<String> connectedDevices, String secureId, Globals.GenArray<Globals.QEvent> eventQueue, String... ipAddress) {
        AccesBdd acces = new AccesBdd(context);
        acces.SetSecureId(secureId);


        for (int i=0;i<connectedDevices.size();i++) {
            String deviceId = connectedDevices.get(i);


            /*try{
                Log.d(TAG,"Sending start of update event");
                sendStartOfUpdateEvent(ipAddress.length > 0 ? ipAddress[0] : acces.getDeviceIP(deviceId),acces);

            } catch (IOException e ) {

                try{
                    Thread.sleep(1000);
                }catch (InterruptedException ignore){

                }
            }*/

            for (int j=0;j<eventQueue.size();j++) {


                Log.d("SendDeviceEvent", "SENDING EVENT : " +  eventQueue.get(j));

                setEventNetworkLockForDevice(deviceId, true);

                try {


                    // Initialize the connection
                    Socket socket = new Socket(ipAddress.length > 0 ? ipAddress[0] : acces.getDeviceIP(deviceId), 8274);
                    DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

                    String ser_event = eventQueue.get(j).serialize();

                    Log.d(TAG,"serialized Event");

                    BufferedOutputStream bos = new BufferedOutputStream(outputStream);
                    // Send the message
                    StringBuilder reqBuilder = new StringBuilder();
                    reqBuilder.append(acces.getMyDeviceId()).append(";").append(secureId).append(ser_event);
                    bos.write(reqBuilder.toString().getBytes(StandardCharsets.UTF_8));
                    bos.flush();

                    // Close the connection
                    bos.close();
                    outputStream.close();
                    socket.close();

                    Log.d("SendDeviceEvent", "Event sent !");
                    setEventNetworkLockForDevice(deviceId, false);

                    // Wait for the network lock to be released for this device
                    while (getEventNetworkLockForDevice(deviceId)) {
                        Thread.sleep(1000);
                    }
                } catch (IOException e) {
                    if(acces.IsDeviceLinked(deviceId)){
                        acces.setDevicedbState(deviceId,false);
                    }
                    Log.e("SendDeviceEvent", "Error occurred while sending event over network", e);

                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            /*try{
                Log.d(TAG,"Sending end of update event");
                sendEndOfUpdateEvent(ipAddress.length > 0 ? ipAddress[0] : acces.getDeviceIP(deviceId),acces);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }*/

        }



        acces.closedb();

    }

    public static void checkDeviceAvailability(String IpAddr,String deviceId){

        ProcessExecutor.Function cda = new ProcessExecutor.Function() {
            @Override
            public void execute() {
                try {
                    Socket socket = new Socket(IpAddr, 8274);
                    socket.close();
                } catch (IOException e) {
                    AccesBdd acces = new AccesBdd(context);
                    acces.setDevicedbState(deviceId,false);
                    acces.cleanNetworkMap();
                    acces.closedb();
                }
            }
        };

        ProcessExecutor.startProcess(cda);


    }



    public static void setEventNetworkLockForDevice(String deviceId, boolean value) {
        try {
            if (value) {
                // Create a network lock file
                File lockFile = new File(QSYNC_WRITABLE_DIRECTORY,deviceId + ".nlock");
                if(lockFile.exists()){
                    removeFromFilesystem(null,lockFile.getPath(),false,false);
                }
                if (!lockFile.createNewFile()) {
                    Log.e("SetEventNetworkLock", "Failed to create network lock file in directory : "+QSYNC_WRITABLE_DIRECTORY+"/"+deviceId+".nlock");
                }
                Log.d("SetEventNetworkLock", "CREATED network lock file in directory : "+QSYNC_WRITABLE_DIRECTORY+"/"+deviceId+".nlock");

            } else {
                // Remove the network lock file
                File lockFile = new File(QSYNC_WRITABLE_DIRECTORY,deviceId + ".nlock");
                if (!lockFile.delete()) {
                    Log.e("SetEventNetworkLock", "Failed to remove network lock file");
                }
            }
        } catch (Exception e) {
            Log.e("SetEventNetworkLock", "Error occurred in SetEventNetworkLockForDevice", e);
        }
    }

    public static boolean getEventNetworkLockForDevice(String deviceId) {
        try {
            // Check if the network lock file exists
            File lockFile = new File(QSYNC_WRITABLE_DIRECTORY,deviceId + ".nlock");
            return lockFile.exists();
        } catch (Exception e) {
            Log.e("GetEventNetworkLock", "Error occurred in GetEventNetworkLockForDevice", e);
            return false;
        }
    }



    public static void removeFromFilesystem(DocumentFile root,String relativePath,boolean needSAF,boolean isDir) {

        if(needSAF){

            Uri fileUri = Uri.withAppendedPath(root.getUri(),relativePath);


            DocumentFile f;
            if(isDir){
                f = DocumentFile.fromSingleUri(context,fileUri);
            }else{
                f = DocumentFile.fromTreeUri(context,fileUri);
            }

            f.delete();

        }else{

            try {
                File fileOrDir = new File(
                        root.getUri().getPath(),relativePath
                );


                if (fileOrDir.isDirectory()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Files.walk(fileOrDir.toPath())
                                .sorted(Comparator.reverseOrder())
                                .map(Path::toFile)
                                .forEach(File::delete);
                    }
                } else {
                    fileOrDir.delete();
                }
            } catch (IOException e) {
                Log.e("RemoveFromFilesystem", "Error while removing file/folder from filesystem", e);
            }
        }


    }

    public static boolean moveInFilesystem(DocumentFile source,DocumentFile newParentFile,boolean isDir) {

        if (!source.exists() || !newParentFile.exists() || !newParentFile.isDirectory()) {
            Log.e(TAG,"Error while moving a DocumentFile, one of the intermadiary one does not exists");
            return false;
        }

        if (source.isDirectory()) {
            // Create target directory
            DocumentFile newDir = newParentFile.createDirectory(source.getName());
            if (newDir == null) {
                Log.e(TAG,"Error while moving a DocumentFile, oDirectory creation failed");
                return false;
            }

            // Recursively move contents
            for (DocumentFile file : source.listFiles()) {
                if (!moveInFilesystem(file, newDir,file.isDirectory())) {
                    return false;
                }
            }

            // Delete the original directory after moving all contents
            return source.delete();
        } else {
            // Moving a single file
            DocumentFile newFile = newParentFile.createFile(source.getType(), source.getName());
            if (newFile == null) {
                return false;
            }

            try {
                copyFile(context, source.getUri(), newFile.getUri());
                return source.delete();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

    }


    private static void copyFile(Context context, Uri srcUri, Uri dstUri) throws IOException {
        ParcelFileDescriptor srcPfd = context.getContentResolver().openFileDescriptor(srcUri, "r");
        ParcelFileDescriptor dstPfd = context.getContentResolver().openFileDescriptor(dstUri, "w");

        if (srcPfd == null || dstPfd == null) {
            throw new IOException("Unable to open file descriptors");
        }

        FileInputStream inputStream = new FileInputStream(srcPfd.getFileDescriptor());
        FileOutputStream outputStream = new FileOutputStream(dstPfd.getFileDescriptor());

        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, length);
        }

        inputStream.close();
        outputStream.close();
        srcPfd.close();
        dstPfd.close();
    }


    public static DocumentFile createFileWithContentResolver(DocumentFile root, String relativePath, boolean isDir) {
        DocumentFile newFile = null;
        try {
            String[] parts = relativePath.split("/");
            DocumentFile currentDir = root;

            // Traverse the path and create directories if necessary

            // as Uri creation is a bit funky with appended path
            // we have to crawl to the desired end of the tree x')
            for (int i = 0; i < parts.length - 1; i++) {
                if (!parts[i].isEmpty()) {
                    DocumentFile nextDir = currentDir.findFile(parts[i]);
                    if (nextDir == null || !nextDir.isDirectory()) {
                        nextDir = currentDir.createDirectory(parts[i]);
                    }
                    currentDir = nextDir;
                }
            }

            // Create the final file or directory
            String finalPart = parts[parts.length - 1];
            if (isDir) {
                newFile = currentDir.createDirectory(finalPart);
            } else {
                newFile = currentDir.createFile("*/*", finalPart);
            }

            if (newFile != null) {
                // File created successfully
                Log.d("FileCreation", "File created successfully: " + newFile.getUri());
            } else {
                // File creation failed
                Log.e("FileCreation", "Failed to create file.");
            }
        } catch (Exception e) {
            Log.e("FileCreation", "Error creating file: ", e);
        }

        return newFile;
    }



    public static void buildSetupQueue(String secureId, String deviceId) {
        AccesBdd acces = new AccesBdd(context);
        acces.SetSecureId(secureId);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri rootUri = Uri.parse(acces.GetRootSyncPath());
            DocumentFile directory;
            if(acces.isApp()){
                directory = DocumentFile.fromFile(new File(acces.GetRootSyncPath()));
            }else{
                directory = DocumentFile.fromTreeUri(context, rootUri);
            }


            // At setup, we must send files one by one
            // so the queue is not huge and does not overflow the RAM
            traverseDocumentFileAndSendOneByOne(directory,rootUri,acces,deviceId,secureId);




        }

        acces.closedb();


    }


    private static void traverseDocumentFileAndSendOneByOne(DocumentFile directory, Uri rootUri, AccesBdd acces,String deviceId,String secureId) {

        Globals.GenArray<Globals.QEvent> queue = new Globals.GenArray<>();

        for (DocumentFile file : directory.listFiles()) {

            // always the same problem with non usefull directory uris
            String relativePath = PathUtils.getRelativePath(
                    rootUri.getPath(),
                    file.isDirectory() ? DocumentFile.fromSingleUri(context,file.getUri()).getUri().getPath() : file.getUri().getPath()
            );



            Globals.GenArray<String> devices = new Globals.GenArray<>();
            devices.add(deviceId);

            if (file.isDirectory()) {


                // Create event for directory
                Globals.QEvent event = new Globals.QEvent(
                        "[CREATE]",
                        "folder",
                        null,
                        relativePath,
                        "",
                        acces.GetSecureId()

                );


                queue.add(event);
                Log.d(TAG,"Notifying to create folder : "+event.FilePath);
                // send before recursive call so we don't take to many ram
                sendDeviceEventQueueOverNetwork(devices, secureId, queue);
                queue.popLast();

                // Recurse into subdirectory
                traverseDocumentFileAndSendOneByOne(file, rootUri, acces,deviceId,secureId);
            } else if (file.isFile()) {

                try {
                    InputStream inputStream =
                            context.getContentResolver().openInputStream(file.getUri());


                    ParcelFileDescriptor fileDescriptor = context.getContentResolver().openFileDescriptor(file.getUri() , "r");
                    long fileSize = fileDescriptor.getStatSize();



                    Globals.QEvent event = new Globals.QEvent(
                            "[CREATE]",
                            "file",
                            DeltaBinaire.buildDeltaFromInputStream(relativePath,fileSize,inputStream,0,new byte[0]),
                            relativePath,
                            "",
                            acces.GetSecureId()
                    );
                    inputStream.close();
                    Log.d(TAG,"Notifying to create file : "+event.FilePath);

                    queue.add(event);
                    sendDeviceEventQueueOverNetwork(devices, secureId, queue);
                    queue.popLast();

                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }


            }
        }
    }


    public static void handleLargageAerien(Globals.QEvent data, String ipAddress,String msg,boolean assumeYes,boolean multiple) {
        String fileName = new File(QSYNC_WRITABLE_DIRECTORY,"/largage_aerien/"+data.FilePath).getName();



        String userResponse = BackendApi.askInput("[OTDL]", msg,context,false);
        if (userResponse.equalsIgnoreCase("y") || userResponse.equalsIgnoreCase("yes") || userResponse.equalsIgnoreCase("oui") || assumeYes) {
            try {
                boolean directoryExists = new File(QSYNC_WRITABLE_DIRECTORY,"/largage_aerien").exists();
                if (!directoryExists) {
                    new File(QSYNC_WRITABLE_DIRECTORY,"/largage_aerien").mkdirs();
                }
                String filePath = PathUtils.joinPaths(QSYNC_WRITABLE_DIRECTORY,"/largage_aerien/" + fileName);
                data.setFilePath(filePath);
                data.Delta.setFilePath(filePath);


                DeltaBinaire.patchFile(data,false,context);
                //Log.d("LARGAGE AERIEN","CONTENU DU FICHIER APRES PATCH : "+ Arrays.toString(readBytesFromFile(filePath)));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                    // Now,we move the recently received file to the downloads folder
                    filePath = PathUtils.moveFileToDownloads(context,filePath);
                    BackendApi.displayToast(context,"The file is now available in your Downloads folder.");

                    if(multiple){
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            FileTar.untarFile(filePath,Path.of(filePath).getParent().toString());
                        }
                    }else{
                        BackendApi.openFile(context,
                                Uri.parse(
                                        filePath
                                )
                        );

                        // open textview if we are receiving a text file
                        if(fileName.endsWith(".txt")){
                            Intent myIntent = new Intent(context, TextViewActivity.class);
                            myIntent.putExtra(
                                    "file_path",
                                    filePath
                            );
                            context.startActivity(myIntent);

                        }
                    }

                }
            } catch (Exception e) {
                Log.e("HandleAirdrop", "Error while handling Largage Aerien", e);
            }



        }
    }







    public static void sendLargageAerien(Uri fileUri, String deviceIp,boolean multiple) {


        try {
            String fileName = PathUtils.getFileNameFromUri(context,fileUri);


            InputStream inputStream;
            long fileSize =0;
            if(PathUtils.needsContentProvider(fileUri)){
                inputStream =
                    context.getContentResolver().openInputStream(fileUri);
                ParcelFileDescriptor fileDescriptor = context.getContentResolver().openFileDescriptor(fileUri , "r");
                fileSize = fileDescriptor.getStatSize();
            }else{
                inputStream = new FileInputStream(new File(fileUri.getPath()));
                fileSize = new File(fileUri.getPath()).length();

            }





            DeltaBinaire.Delta delta = DeltaBinaire.buildDeltaFromInputStream(fileName,fileSize,inputStream,0,new byte[0]);
            Globals.QEvent event = new Globals.QEvent(
                    multiple ? "[MOTDL]":"[OTDL]",
                    "file",
                    delta,
                    fileName,
                    "",
                    "le_ciel_me_tombe_sur_la_tete_000000000000"
            );

            Globals.GenArray<String> dummyDevice = new Globals.GenArray<>();
            dummyDevice.add(deviceIp);
            Globals.GenArray<Globals.QEvent> eventQueue = new Globals.GenArray<>();
            eventQueue.add(event);
            sendDeviceEventQueueOverNetwork(dummyDevice, "le_ciel_me_tombe_sur_la_tete_000000000000", eventQueue, deviceIp);
        } catch (IOException e) {
            Log.e("SendAirdrop", "Error while sending Largage Aerien", e);
        }


    }

    public static boolean CheckIfDeviceOnline(String ipAddress, int port) {
        try {
            // Create a socket with a timeout
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(ipAddress, port), 2000); // Timeout in milliseconds

            // Close the socket
            socket.close();

            // If connection was successful, return true
            return true;
        } catch (IOException e) {
            // Connection failed or timeout occurred
            return false;
        }
    }


    public static void sendLinkDeviceRequest(String deviceIp,AccesBdd acces){


        // get root creation date so the other end can compare it
        // if his end is older, they will send the setup packet queue
        // if not, they will ask for yours
        /*String rootpath = acces.GetRootSyncPath();
        DocumentFile root;
        if(acces.isApp()){
            root = DocumentFile.fromFile(
                    new File(rootpath)
            );


        }else{
            root = DocumentFile.fromTreeUri(
                    context,
                    Uri.parse(rootpath)
            );
        }*/



        Globals.QEvent event = new Globals.QEvent(
                "[LINK_DEVICE]",
                acces.isApp() ? "[APPLICATION]" :"[CLASSIC]",
                null,
                acces.isApp() ? acces.getAppName() : "",
                String.valueOf(acces.getSyncCreationDate()),
                acces.GetSecureId()
        );
        Globals.GenArray<String> dummyDevice = new Globals.GenArray<>();
        dummyDevice.add(deviceIp);
        Globals.GenArray<Globals.QEvent> eventQueue = new Globals.GenArray<>();
        eventQueue.add(event);
        sendDeviceEventQueueOverNetwork(dummyDevice, acces.GetSecureId(), eventQueue, deviceIp);

    }


}
