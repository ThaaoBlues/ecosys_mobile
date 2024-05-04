package com.qsync.qsync;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Networking {

    public static final int HEADER_LENGTH = 83;
    private static final String TAG = "Networking";
    private static ServerSocket serverSocket;
    private static Socket clientSocket;
    private static AccesBdd acces;

    public static void main(String[] args) {
        try {
            serverSocket = new ServerSocket(8274);
            Log.d(TAG, "Server started on port 8274");
            while (true) {
                clientSocket = serverSocket.accept();
                Log.d(TAG, "Client connected");
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error while initializing socket server : ", e);
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
                acces = new AccesBdd();
                acces.InitConnection();

                // get the device id and secure sync id from header
                char[] header_buff = new char[HEADER_LENGTH];
                InputStreamReader inputStreamReader = new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                bufferedReader.read(header_buff, 0, HEADER_LENGTH);

                String device_id = new String(header_buff, 0, HEADER_LENGTH).split(";")[0];
                String secure_id = new String(header_buff, 0, HEADER_LENGTH).split(";")[1];

                acces.SetSecureId(secure_id);

                // in case of a link packet, the device is not yet registered in the database
                // so it can throw an error
                if (acces.IsDeviceLinked(device_id)) {
                    // makes sure it is marked as connected
                    if (!acces.GetDevicedbState(device_id)) {
                        // needs split as RemoteAddr ads port to the address
                        acces.SetDevicedbState(device_id, true, clientSocket.getInetAddress().getHostAddress());
                    }
                }

                // read the body of the request and store it in a buffer
                StringBuilder body_buff = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    body_buff.append(line);
                }

                Log.d(TAG, "Request body : " + body_buff);

                // Parse the JSON
                Gson gson = new Gson();
                Globals.QEvent data = gson.fromJson(body_buff.toString(),Globals.QEvent.class);

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
                        String path = askInput("[CHOOSELINKPATH]", "Choose a path where new sync files will be stored.");
                        Log.d(TAG, "Future sync will be stored at : " + path);
                        acces.CreateSyncFromOtherEnd(path, secure_id);
                        Log.d(TAG, "Linking device : " + device_id);
                        acces.LinkDevice(device_id, clientSocket.getInetAddress().getHostAddress());
                        break;
                    case "[UNLINK_DEVICE]":
                        acces.UnlinkDevice(device_id);
                        break;
                    case "[OTDL]":
                        handleLargageAerien(data, clientSocket.getInetAddress().getHostAddress());
                        break;
                    default:
                        // regular file event
                        handleEvent(secure_id, device_id, body_buff.toString());
                        // send back a modification confirmation, so the other end can remove this machine device_id
                        // from concerned sync task retard entries
                        String response = acces.getMyDeviceId() + ";" + acces.GetSecureId() + ";" + "[MODIFICATION_DONE]";
                        DataOutputStream outputStream = new DataOutputStream(clientSocket.getOutputStream());
                        outputStream.writeBytes(response);
                        break;
                }

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


            // First, we lock the filesystem watcher to prevent a ping-pong effect
            setFileSystemPatchLockState(deviceId, true);

            // Get the necessary data from the JSON event
            String relativePath = jsonEvent.getString("FilePath");
            String newRelativePath = jsonEvent.getString("NewFilePath");
            String absoluteFilePath = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                absoluteFilePath = Paths.get(acces.GetRootSyncPath(), relativePath).toString();
            }
            String newAbsoluteFilePath = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                newAbsoluteFilePath = Paths.get(acces.GetRootSyncPath(), newRelativePath).toString();
            }
            String eventType = jsonEvent.getString("Flag");
            String fileType = jsonEvent.getString("FileType");

            switch (eventType) {
                case "MOVE":
                    acces.move(relativePath, newRelativePath, fileType);
                    moveInFileSystem(absoluteFilePath, newAbsoluteFilePath);
                    break;
                case "REMOVE":
                    if ("file".equals(fileType)) {
                        acces.rmFile(absoluteFilePath);
                    } else {
                        acces.rmFolder(absoluteFilePath);
                    }
                    removeFromFileSystem(absoluteFilePath);
                    break;
                case "CREATE":
                    if ("file".equals(fileType)) {
                        acces.createFile(relativePath,absoluteFilePath,"[SENT_FROM_OTHER_DEVICE]");
                    } else {
                        createFolder(absoluteFilePath);
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
            setFileSystemPatchLockState(deviceId, false);
        } catch (JSONException e) {
            Log.e("HandleEventAdapter", "Error decoding JSON data from request buffer", e);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    public static void sendDeviceEventQueueOverNetwork(Globals.GenArray<String> connected_devices, String secure_id, Globals.GenArray<Globals.QEvent> event_queue, String... ip_addr) {
        // ...
    }

    public static void setEventNetworkLockForDevice(String device_id, boolean value) {
        // ...
    }

    public static boolean getEventNetworkLockForDevice(String device_id) {
        // ...
        return false;
    }

    public static void removeFromFilesystem(String path) {
        // ...
    }

    public static void moveInFilesystem(String old_path, String new_path) {
        // ...
    }

    public static void buildSetupQueue(String secure_id, String device_id) {
        // ...
    }

    public static void handleLargageAerien(Globals.QEvent data, String ip_addr) {
        // ...
    }

    public static void sendLargageAerien(String file_path, String device_ip) {
        // ...
    }

    public static String askInput(final String title, final String message) {
        final Handler handler = new Handler(Looper.getMainLooper());
        final String[] result = new String[1];
        handler.post(new Runnable() {
            @Override
            public void run() {
                // Show your dialog here to get user input
                // Store the result in result[0]
            }
        });
        // Wait for user input
        while (result[0] == null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return result[0];
    }
}
