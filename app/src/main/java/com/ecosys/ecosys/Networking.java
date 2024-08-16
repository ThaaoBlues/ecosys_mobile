/*
 * *
 *  * Created by Théo Mougnibas on 27/06/2024 17:18
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 27/06/2024 17:18
 *
 */

package com.ecosys.ecosys;

import static androidx.activity.result.ActivityResultCallerKt.registerForActivityResult;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.documentfile.provider.DocumentFile;


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
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
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
    private static final String TAG = "Ecosys Server";
    private static ServerSocket serverSocket;
    private static Socket clientSocket;
    private static Context context;
    private static String ECOSYS_WRITABLE_DIRECTORY;

    private static ActivityResultLauncher<Intent> selectFolderLauncher;

    private static String tmpSecureIdForCreation;

    private static boolean setupDlLock;

    private static ProcessExecutor.Function networkingCallForPicker;

    private static String broadcastresult = null;

    Networking(Context mcontext, String mFilesDir) {
        context = mcontext;
        ECOSYS_WRITABLE_DIRECTORY = mFilesDir;

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
                acces.setSecureId(secure_id);

                // in case of a link packet, the device is not yet registered in the database
                // so it can throw an error
                if (acces.isDeviceLinked(device_id)) {
                    // makes sure it is marked as connected
                    if (!acces.getDevicedbState(device_id)) {
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
                        "",
                        0
                );
                data.deserializeQEvent(body_buff.toString());

                // check if this is a regular file event of a special request
                Log.d(TAG, "RECEIVING EVENT : " + data);

                DataOutputStream outputStream = new DataOutputStream(clientSocket.getOutputStream());

                switch (data.getFlag()) {
                    case "[MODIFICATION_DONE]":
                        acces.removeDeviceFromRetardOneFile(device_id,data.FilePath,Long.parseLong(data.FileType));
                        break;

                    case "[BEGIN_UPDATE]":

                        if(acces.isDeviceLinked(device_id)) {
                            acces.setFileSystemPatchLockState(device_id, true);
                        }

                        break;

                    case "[END_OF_UPDATE]":
                        if(acces.isDeviceLinked(device_id)) {
                            acces.setFileSystemPatchLockState(device_id, false);
                        }

                        break;
                    case "[SETUP_DL]":

                        if(acces.isDeviceLinked(device_id)){
                            Log.d(TAG, "GOT FLAG, BUILDING SETUP QUEUE...");
                            buildSetupQueue(secure_id, device_id);

                        }

                        break;
                    case "[LINK_DEVICE]":
                        // as this is triggered by another machine telling this one to create a sync task,
                        // we must prepare the environnement to accept this
                        // by creating a new sync task with the same path (replace this later by asking to the user)
                        // and same secure_id
                        acces.setSecureId(secure_id);
                        setSetupDlLock(false);
                        if(Objects.equals(data.FileType, "[APPLICATION]")){






                            if(acces.checkAppExistenceFromName(data.getFilePath())){

                                DocumentFile root = DocumentFile.fromFile(context.getExternalFilesDir(null)).findFile("apps");
                                String app_path = PathUtils.joinPaths(
                                        root.getUri().getPath(),
                                        data.getFilePath()
                                );
                                acces.getSecureIdFromRootPath(app_path);

                                long remote_task_creation_timestamp = Long.parseLong(data.getNewFilePath());
                                long local_task_creation_timestamp = acces.getSyncCreationDate();

                                if(remote_task_creation_timestamp < local_task_creation_timestamp){
                                    Log.d(TAG, "Initializing env to welcome the other end folder content");
                                    acces.updateSyncId(app_path,secure_id);
                                }else{
                                    // older sync on the other device, ask to link but the opposite way
                                    // so the dowload is made on this device
                                    Log.d(TAG,"Other end task is more recent, inverting the linking process..");
                                    String ipAddr = clientSocket.getInetAddress().getHostAddress();
                                    sendLinkDeviceRequest(ipAddr,acces);
                                    acces.linkDevice(device_id,ipAddr);
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
                        acces.linkDevice(device_id, ipAddr);
                        acces.setDevicedbState(device_id,true);
                        askSetupDownloadToOtherEnd(ipAddr,acces);


                        break;
                    case "[UNLINK_DEVICE]":
                        acces.unlinkDevice(device_id);
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


                        // Check if update has already been made or not
                        // as multiple devices may send the same patch

                        switch (data.getFileType()) {
                            case "file":

                                // voir ici parce que dans certains evenements ça pourrait foirer
                                // par exemple un remove puis un update reçu en retard ne passerait pas
                                // et resterait dans les retards chez l'autre  :/
                                if (acces.checkFileExists(data.getFilePath())) {
                                    // remove event has always a version_id of 0
                                    if (acces.getFileLastVersionId(data.getFilePath()) > data.getVersionToPatch() && !data.getFlag().equals("[REMOVE]")) {
                                        // don't do outdated modifications
                                        sendModificationDoneEvent(
                                                clientSocket.getInetAddress().getHostAddress(),
                                                acces,
                                                data.FilePath,
                                                data.VersionToPatch+1
                                        );
                                        return;
                                    }
                                } else {
                                    // un ev qui arrive en retard apres une suppression
                                    if (!data.getFlag().equals("[CREATE]")) {
                                        // don't do outdated modifications
                                        sendModificationDoneEvent(
                                                clientSocket.getInetAddress().getHostAddress(),
                                                acces,
                                                data.FilePath,
                                                data.VersionToPatch
                                        );
                                        return;
                                    }
                                }

                                break;

                            case "folder":
                                // as a folder event is always related with moves/deletions or creation
                                if (acces.checkFileExists(data.getFilePath()) == data.getFlag().equals("[CREATE]")) {
                                    // don't do outdated modifications
                                    sendModificationDoneEvent(
                                            clientSocket.getInetAddress().getHostAddress(),
                                            acces,
                                            data.FilePath,
                                            data.VersionToPatch
                                    );
                                    return;
                                }
                                break;

                            default:
                                break;
                        }


                        //store the event for offline linked devices
                        // so the update does not have to come from the same device for every others
                        acces.storeReceivedEventForOthersDevices(data);



                        // regular file event
                        handleEvent(device_id, data);
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
        acces.setSecureId(event.getSecureId());
        // First, we lock the filesystem watcher to prevent a ping-pong effect
        acces.setFileSystemPatchLockState(deviceId, true);

        // Get the necessary data from the JSON event
        String relativePath = event.FilePath;


        String newRelativePath = event.NewFilePath;
        String absoluteFilePath = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            absoluteFilePath = Paths.get(acces.getRootSyncPath(), relativePath).toString();
        }

        String eventType = event.Flag;
        String fileType = event.FileType;
        Log.d(TAG,absoluteFilePath);

        DocumentFile root;
        if(acces.isApp()){
            root = DocumentFile.fromFile(new File(acces.getRootSyncPath()));
        }else{
            root = DocumentFile.fromTreeUri(context,Uri.parse(acces.getRootSyncPath()));
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
                    if(!acces.checkFileExists(relativePath)){
                        break;
                    }
                    if ("file".equals(fileType)) {
                        acces.rmFile(absoluteFilePath);
                    } else {
                        acces.rmFolder(absoluteFilePath);
                    }

                    removeFromFilesystem(root,relativePath,!acces.isApp());

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
                            DeltaBinaire.Delta delta = event.getDelta();
                            delta.setFilePath(absoluteFilePath);
                            event.setDelta(delta);

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


                    DeltaBinaire.Delta delta = event.getDelta();

                    // sometimes empty delta are sent
                    if(delta != null){
                        delta.setFilePath(absoluteFilePath);
                        event.setDelta(delta);

                        DeltaBinaire.patchFile(event,!acces.isApp(),context);
                        acces.updateCachedFile(relativePath,file,!acces.isApp(),absoluteFilePath);
                    }


                    break;
                default:
                    Log.e("HandleEventAdapter", "Received unknown event type: " + eventType);
                    break;
            }

            try {

                long fileVersion;

                switch (event.getFlag()){
                    case "[MOVE]":
                        fileVersion = acces.getFileLastVersionId(event.getNewFilePath());
                        break;

                    case "[REMOVE]":
                        fileVersion = 0;
                        break;

                    default:
                        fileVersion = acces.getFileLastVersionId(relativePath);
                        break;
                }
                // so the other end can clear its retard table entries
                sendModificationDoneEvent(
                        clientSocket.getInetAddress().getHostAddress(),
                        acces,
                        relativePath,
                        fileVersion
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
        acces.setFileSystemPatchLockState(deviceId, false);

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
                acces.getSecureId(),
                0
        ).serialize();

        Log.d(TAG,"serialized Event");

        BufferedOutputStream bos = new BufferedOutputStream(out);
        // Send the message
        StringBuilder reqBuilder = new StringBuilder();
        reqBuilder.append(acces.getMyDeviceId()).append(";").append(acces.getSecureId()).append(ser_event);
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
                acces.getSecureId(),
                0
        ).serialize();

        Log.d(TAG,"serialized Event");

        BufferedOutputStream bos = new BufferedOutputStream(outputStream);
        // Send the message
        StringBuilder reqBuilder = new StringBuilder();
        reqBuilder.append(acces.getMyDeviceId()).append(";").append(acces.getSecureId()).append(ser_event);
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
                acces.getSecureId(),
                0
        ).serialize();

        Log.d(TAG,"serialized Event");

        BufferedOutputStream bos = new BufferedOutputStream(outputStream);
        // Send the message
        StringBuilder reqBuilder = new StringBuilder();
        reqBuilder.append(acces.getMyDeviceId()).append(";").append(acces.getSecureId()).append(ser_event);
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
                acces.getSecureId(),
                acces.getFileLastVersionId(relativePath)-1
        ).serialize();

        Log.d(TAG,"serialized Event");

        BufferedOutputStream bos = new BufferedOutputStream(outputStream);
        // Send the message
        StringBuilder reqBuilder = new StringBuilder();
        reqBuilder.append(acces.getMyDeviceId()).append(";").append(acces.getSecureId()).append(ser_event);
        bos.write(reqBuilder.toString().getBytes(StandardCharsets.UTF_8));
        bos.flush();

        // Close the connection
        bos.close();
        outputStream.close();
        socket.close();
    }


    public static void sendDeviceEventQueueOverNetwork(Globals.GenArray<String> connectedDevices, String secureId, Globals.GenArray<Globals.QEvent> eventQueue, String... ipAddress) {
        AccesBdd acces = new AccesBdd(context);
        acces.setSecureId(secureId);


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
                    Socket socket = new Socket();
                    // use 1s timeout to avoid ghost devices
                    socket.connect(
                            new InetSocketAddress(ipAddress.length > 0 ? ipAddress[0] : acces.getDeviceIP(deviceId), 8274),
                            1000

                    );
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

                    if(acces.isDeviceLinked(deviceId) && isEventFilesystemRelated(eventQueue.get(j).Flag)) {
                        acces.setDevicedbState(deviceId, false);

                        String modType;
                        switch (eventQueue.get(j).Flag) {
                            case "[UPDATE]":
                                modType = "p";
                                break;
                            case "[CREATE]":
                                modType = "c";
                                break;
                            case "[REMOVE]":
                                modType = "d";
                                break;
                            case "[MOVE]":
                                modType = "m";
                                break;
                            default:
                                throw new RuntimeException("Error while refreshing retard table : Unknown flag passed the flag filter : " + eventQueue.get(j).Flag);
                        }
                        acces.refreshCorrespondingRetardRow(eventQueue.get(j).FilePath, modType);
                        Log.e("SendDeviceEvent", "Error occurred while sending event over network", e);
                    }
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
                File lockFile = new File(ECOSYS_WRITABLE_DIRECTORY,deviceId + ".nlock");
                if(lockFile.exists()){
                    removeFromFilesystem(null,lockFile.getPath(),false);
                }
                if (!lockFile.createNewFile()) {
                    Log.e("SetEventNetworkLock", "Failed to create network lock file in directory : "+ECOSYS_WRITABLE_DIRECTORY+"/"+deviceId+".nlock");
                }
                Log.d("SetEventNetworkLock", "CREATED network lock file in directory : "+ECOSYS_WRITABLE_DIRECTORY+"/"+deviceId+".nlock");

            } else {
                // Remove the network lock file
                File lockFile = new File(ECOSYS_WRITABLE_DIRECTORY,deviceId + ".nlock");
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
            File lockFile = new File(ECOSYS_WRITABLE_DIRECTORY,deviceId + ".nlock");
            return lockFile.exists();
        } catch (Exception e) {
            Log.e("GetEventNetworkLock", "Error occurred in GetEventNetworkLockForDevice", e);
            return false;
        }
    }



    public static void removeFromFilesystem(DocumentFile root,String relativePath,boolean needSAF) {

        if(needSAF){

            Uri fileUri = Uri.withAppendedPath(root.getUri(),relativePath);



            String[] parts = relativePath.split("/");
            DocumentFile currentFile = root;

            // Traverse the path and create directories if necessary

            // as Uri creation is a bit funky with appended path
            // we have to crawl to the desired end of the tree x')
            for (String part : parts) {
                if (!part.isEmpty()) {
                    currentFile = currentFile.findFile(part);
                    if (currentFile == null) {
                        Log.d(TAG, "Could not find the file to delete :  " + fileUri.getPath());
                        return;
                    }
                }
            }

            currentFile.delete();
            Log.d(TAG,"File deleted : "+currentFile.getUri().getPath());


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
        acces.setSecureId(secureId);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri rootUri = Uri.parse(acces.getRootSyncPath());
            DocumentFile directory;
            if(acces.isApp()){
                directory = DocumentFile.fromFile(new File(acces.getRootSyncPath()));
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
                        acces.getSecureId(),
                        0

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


                    InputStream inputStream;
                    long fileSize;
                    if(acces.isApp()){
                        File og_file_object = new File(file.getUri().getPath());
                        inputStream = new FileInputStream(og_file_object);
                        fileSize = og_file_object.length();
                    }else{
                        inputStream = context.getContentResolver().openInputStream(file.getUri());
                        ParcelFileDescriptor fileDescriptor = context.getContentResolver().openFileDescriptor(file.getUri() , "r");
                         fileSize = fileDescriptor.getStatSize();
                    }

                    Globals.QEvent event = new Globals.QEvent(
                            "[CREATE]",
                            "file",
                            DeltaBinaire.buildDeltaFromInputStream(relativePath,fileSize,inputStream,0,new byte[0]),
                            relativePath,
                            "",
                            acces.getSecureId(),
                            0
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
        String fileName = new File(ECOSYS_WRITABLE_DIRECTORY,"/largage_aerien/"+data.FilePath).getName();



        //String userResponse = BackendApi.askInput("[OTDL]", msg,context,false);


        // Now you can call the method to start the InputActivity
        BackendApi.launchInputActivityAndBroadCastResult("[SINGLE_LINE_INPUT_OR_CONFIRMATION_DIALOG]", msg, context, false);
        // get result from startup service

        while (broadcastresult == null){
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        String userResponse = broadcastresult;

        Log.d(TAG,"Reçu le resultat : "+userResponse);


        if (userResponse.equalsIgnoreCase("y") || userResponse.equalsIgnoreCase("yes") || userResponse.equalsIgnoreCase("oui") || assumeYes) {

            resetBroadcastResult();
            try {
                boolean directoryExists = new File(ECOSYS_WRITABLE_DIRECTORY,"/largage_aerien").exists();
                if (!directoryExists) {
                    new File(ECOSYS_WRITABLE_DIRECTORY,"/largage_aerien").mkdirs();
                }
                String filePath = PathUtils.joinPaths(ECOSYS_WRITABLE_DIRECTORY,"/largage_aerien/" + fileName);
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


                        // open textview if we are receiving a text file
                        if(fileName.endsWith(".txt")){
                            Log.d(TAG,"Opening textview activity to view text file : "+filePath);
                            Intent myIntent = new Intent(context, TextViewActivity.class);
                            myIntent.putExtra(
                                    "file_path",
                                    filePath
                            );
                            myIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(myIntent);

                        }else {
                            BackendApi.openFile(context,
                                    Uri.parse(
                                            filePath
                                    )
                            );
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
                    "le_ciel_me_tombe_sur_la_tete_000000000000",
                    0
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
                acces.getSecureId(),
                0
        );
        Globals.GenArray<String> dummyDevice = new Globals.GenArray<>();
        dummyDevice.add(deviceIp);
        Globals.GenArray<Globals.QEvent> eventQueue = new Globals.GenArray<>();
        eventQueue.add(event);
        sendDeviceEventQueueOverNetwork(dummyDevice, acces.getSecureId(), eventQueue, deviceIp);

    }
    
    public static void resetBroadcastResult(){
        broadcastresult = null;
    }
    public static void setBroadcastResult(String result){
        broadcastresult = result;
    }

    private static boolean isEventFilesystemRelated(String flag) {
        boolean ret = !flag.equals("[MOTDL]");
        ret = ret && !flag.equals("[OTDL]");
        ret = ret && !flag.equals("[LINK_DEVICE]");
        ret = ret && !flag.equals("[UNLINK_DEVICE]");
        ret = ret && !flag.equals("[MODIFICATION_DONE]");
        return ret;
    }

}
