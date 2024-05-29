package com.qsync.qsync;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;

public class Networking {

    public static final int HEADER_LENGTH = 83;
    private static final String TAG = "QSync Server";
    private static ServerSocket serverSocket;
    private static Socket clientSocket;
    private static Context context;
    private static String QSYNC_WRITABLE_DIRECTORY;


    public Networking(Context mcontext, String mFilesDir) {
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
                String device_id = "";
                String secure_id = "";
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
                switch (data.getFlag()) {
                    case "[MODIFICATION_DONE]":
                        setEventNetworkLockForDevice(device_id, false);
                        break;
                    case "[SETUP_DL]":
                        Log.d(TAG, "GOT FLAG, BUILDING SETUP QUEUE...");
                        buildSetupQueue(secure_id, device_id);
                        break;
                    case "[LINK_DEVICE]":
                        // as this is triggered by another machine telling this one to create a sync task,
                        // we must prepare the environnement to accept this
                        // by creating a new sync task with the same path (replace this later by asking to the user)
                        // and same secure_id
                        Log.d(TAG, "Initializing env to welcome the other end folder content");
                        acces.SetSecureId(secure_id);
                        String path = BackendApi.askInput("[CHOOSELINKPATH]", "Choose a path where new sync files will be stored.",context,true);
                        Log.d(TAG, "Future sync will be stored at : " + path);
                        acces.CreateSyncFromOtherEnd(path, secure_id);
                        Log.d(TAG, "Linking device : " + device_id);
                        acces.LinkDevice(device_id, clientSocket.getInetAddress().getHostAddress());
                        break;
                    case "[UNLINK_DEVICE]":
                        acces.UnlinkDevice(device_id);
                        break;
                    case "[OTDL]":
                        // signle-file largage aerien
                        handleLargageAerien(data,
                                clientSocket.getInetAddress().getHostAddress(),
                                "Accept the Largage Aerien ? (coming from " + clientSocket.getInetAddress().getHostAddress() + ")\n File name: " + data.FilePath,
                                false,
                                false
                                );
                        break;

                    case "[MOTDL]":
                        //unzip and then do the usual things on all the files
                        Log.d(TAG,"Handling MOTDL...");
                        handleLargageAerien(data,
                                clientSocket.getInetAddress().getHostAddress(),
                                "Accept the Multi Largage Aerien ? (coming from " + clientSocket.getInetAddress().getHostAddress() + ")\n Zip File name: " + data.FilePath,
                                false,
                                true
                        );
                        break;
                    default:
                        // regular file event
                        handleEvent(secure_id, device_id, body_buff.toString().getBytes());
                        // send back a modification confirmation, so the other end can remove this machine device_id
                        // from concerned sync task retard entries
                        String response = acces.getMyDeviceId() + ";" + acces.GetSecureId() + ";" + "[MODIFICATION_DONE]";
                        DataOutputStream outputStream = new DataOutputStream(clientSocket.getOutputStream());
                        outputStream.writeBytes(response);
                        break;
                }

                acces.closedb();
            } catch (IOException e) {
                Log.e(TAG, "Error in ClientHandler: ", e);
            }
        }
    }

    // used to process a request when it is a regular file event
    public static void handleEvent(String secureId, String deviceId, byte[] buffer) {
        try {
            String bufferData = new String(buffer);
            JSONObject jsonEvent = new JSONObject(bufferData);

            AccesBdd acces = new AccesBdd(context);
            // First, we lock the filesystem watcher to prevent a ping-pong effect
            acces.SetFileSystemPatchLockState(deviceId, true);

            // Get the necessary data from the JSON event
            String relativePath = jsonEvent.getString("FilePath");
            String newRelativePath = jsonEvent.getString("NewFilePath");
            String absoluteFilePath = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                absoluteFilePath = Paths.get(acces.GetRootSyncPath(), relativePath).toString();
            }
            String newAbsoluteFilePath = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                newAbsoluteFilePath = Paths.get(acces.GetRootSyncPath(), newRelativePath).toString();
            }
            String eventType = jsonEvent.getString("Flag");
            String fileType = jsonEvent.getString("FileType");

            switch (eventType) {
                case "MOVE":
                    acces.move(relativePath, newRelativePath, fileType);
                    moveInFilesystem(absoluteFilePath, newAbsoluteFilePath);
                    break;
                case "REMOVE":
                    if ("file".equals(fileType)) {
                        acces.rmFile(absoluteFilePath);
                    } else {
                        acces.rmFolder(absoluteFilePath);
                    }
                    removeFromFilesystem(absoluteFilePath);
                    break;
                case "CREATE":
                    if ("file".equals(fileType)) {
                        acces.createFile(relativePath,absoluteFilePath,"[SENT_FROM_OTHER_DEVICE]");
                    } else {
                        acces.createFolder(absoluteFilePath);
                    }
                    break;
                case "UPDATE":
                    acces.incrementFileVersion(relativePath);
                    break;
                default:
                    Log.e("HandleEventAdapter", "Received unknown event type: " + eventType);
                    break;
            }

            // Release the filesystem lock
            acces.SetFileSystemPatchLockState(deviceId, false);

            acces.closedb();
        } catch (JSONException e) {
            Log.e("HandleEventAdapter", "Error decoding JSON data from request buffer", e);
        }
    }


    public static void sendDeviceEventQueueOverNetwork(Globals.GenArray<String> connectedDevices, String secureId, Globals.GenArray<Globals.QEvent> eventQueue, String... ipAddress) {
        AccesBdd acces = new AccesBdd(context);
        for (int i=0;i<connectedDevices.size();i++) {
            String deviceId = connectedDevices.get(i);

            for (int j=0;j<eventQueue.size();j++) {


                Globals.QEvent event = eventQueue.get(j);
                Log.d("SendDeviceEvent", "SENDING EVENT : " + event);

                setEventNetworkLockForDevice(deviceId, true);

                try {

                    /*Gson gson = new Gson();
                    String eventJson = gson.toJson(event);*/

                    String serializedEvent = event.serialize();

                    // Initialize the connection
                    Socket socket = new Socket(ipAddress.length > 0 ? ipAddress[0] : acces.getDeviceIP(deviceId), 8274);
                    DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

                    // Construct the message to be sent
                    String message = acces.getMyDeviceId() + ";" + secureId + serializedEvent;

                    // Send the message
                    outputStream.writeBytes(message);
                    outputStream.flush();

                    // Close the connection
                    outputStream.close();
                    socket.close();

                    Log.d("SendDeviceEvent", "Event sent !");
                    setEventNetworkLockForDevice(deviceId, false);

                    // Wait for the network lock to be released for this device
                    while (getEventNetworkLockForDevice(deviceId)) {
                        Thread.sleep(1000);
                    }
                } catch (IOException | InterruptedException e) {
                    if(acces.IsDeviceLinked(deviceId)){
                        acces.setDevicedbState(deviceId,false);
                    }
                    Log.e("SendDeviceEvent", "Error occurred while sending event over network", e);
                }
            }
        }
    }



    public static void setEventNetworkLockForDevice(String deviceId, boolean value) {
        try {
            if (value) {
                // Create a network lock file
                File lockFile = new File(QSYNC_WRITABLE_DIRECTORY,deviceId + ".nlock");
                if(lockFile.exists()){
                    removeFromFilesystem(lockFile.getPath());
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



    public static void removeFromFilesystem(String path) {
        try {
            File fileOrDir = new File(path);
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

    public static void moveInFilesystem(String oldPath, String newPath) {
        try {
            File oldFileOrDir = new File(oldPath);
            File newFileOrDir = new File(newPath);

            if (oldFileOrDir.isDirectory()) {
                if (!newFileOrDir.exists()) {
                    newFileOrDir.mkdirs();
                }
                File[] contents = oldFileOrDir.listFiles();
                if (contents != null) {
                    for (File content : contents) {
                        String newFilePath = newPath + File.separator + content.getName();
                        moveInFilesystem(content.getPath(), newFilePath);
                    }
                }
                oldFileOrDir.delete();
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Files.move(Path.of(oldPath), Path.of(newPath), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            Log.e("MoveInFilesystem", "Error while moving entity in filesystem", e);
        }
    }



    public static void buildSetupQueue(String secureId, String deviceId) {
        AccesBdd acces = new AccesBdd(context);
        acces.SetSecureId(secureId);



        Globals.GenArray<Globals.QEvent> queue = new Globals.GenArray<>();
        // TODO : utiliser documentfiletree comme and FIleSystem.populateState


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri rootUri = Uri.parse(acces.GetRootSyncPath());
            DocumentFile directory = DocumentFile.fromTreeUri(context, rootUri);

            traverseDocumentFile(directory,rootUri,acces,queue);

            Globals.GenArray<String> devices = new Globals.GenArray<>();
            devices.add(deviceId);
            sendDeviceEventQueueOverNetwork(devices, secureId, queue);

            acces.closedb();

        }



    }


    private static void traverseDocumentFile(DocumentFile directory, Uri rootUri, AccesBdd acces, Globals.GenArray<Globals.QEvent> queue) {
        for (DocumentFile file : directory.listFiles()) {
            String relativePath = PathUtils.getRelativePath(rootUri, file.getUri());

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
                // Recurse into subdirectory
                traverseDocumentFile(file, rootUri, acces,queue);
            } else if (file.isFile()) {

                try {
                    InputStream inputStream =
                            context.getContentResolver().openInputStream(file.getUri());


                    ParcelFileDescriptor fileDescriptor = context.getContentResolver().openFileDescriptor(file.getUri() , "r");
                    long fileSize = fileDescriptor.getStatSize();

                    DeltaBinaire.Delta delta = DeltaBinaire.buildDeltaFromInputStream(relativePath,fileSize,inputStream,0,new byte[0]);


                    Globals.QEvent event = new Globals.QEvent(
                            "[CREATE]",
                            "file",
                            delta,
                            relativePath,
                            "",
                            acces.GetSecureId()
                    );
                    queue.add(event);

                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }


    private static byte[] readBytesFromFile(DocumentFile file) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return Files.readAllBytes(Paths.get(file.getUri().getPath()));
            }
        } catch (Exception e) {
            Log.e("ReadBytesFromFile", "Error while reading bytes from file", e);
            return new byte[0];
        }
        return new byte[0];
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


                DeltaBinaire.patchFile(data.Delta);
                //Log.d("LARGAGE AERIEN","CONTENU DU FICHIER APRES PATCH : "+ Arrays.toString(readBytesFromFile(filePath)));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                    // Now,we move the recently received file to the downloads folder
                    filePath = PathUtils.moveFileToDownloads(context,filePath);
                    BackendApi.displayToast(context,"The file is now available in your Downloads folder.");

                    if(multiple){
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            FileZipper.unzipFile(filePath,Path.of(filePath).getParent().toString());
                        }
                    }else{
                        BackendApi.openFile(context,
                                Uri.parse(
                                        filePath
                                )
                        );
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

            InputStream inputStream =
                    context.getContentResolver().openInputStream(fileUri);


            ParcelFileDescriptor fileDescriptor = context.getContentResolver().openFileDescriptor(fileUri , "r");
            //long fileSize = context.getContentResolver().query(fileUri,null,null,null,null).getColumnIndex(OpenableColumns.SIZE);
            long fileSize = fileDescriptor.getStatSize();

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

        Globals.QEvent event = new Globals.QEvent(
                "[LINK_DEVICE]",
                "",
                null,
                "",
                "",
                acces.GetSecureId()
        );
        Globals.GenArray<String> dummyDevice = new Globals.GenArray<>();
        dummyDevice.add(deviceIp);
        Globals.GenArray<Globals.QEvent> eventQueue = new Globals.GenArray<>();
        eventQueue.add(event);
        sendDeviceEventQueueOverNetwork(dummyDevice, acces.GetSecureId(), eventQueue, deviceIp);

    }





}
