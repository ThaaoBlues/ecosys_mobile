/*
 * *
 *  * Created by Théo Mougnibas on 27/06/2024 17:18
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 27/06/2024 17:18
 *
 */

package com.ecosys.ecosys;

import static com.ecosys.ecosys.DeltaBinaire.buildDeltaFromInputStream;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;


import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.zip.GZIPInputStream;
import java.util.HashMap;
import java.nio.file.SimpleFileVisitor;

import android.util.Log;

public class AccesBdd {


    private String secureId;
    private SQLiteDatabase db;
    private Context context;

    private String TAG = "Ecosys Server : AccesBdd";


    public AccesBdd(Context context){
        this.context = context;
        initConnection();
    }

    public boolean isMyDeviceIdGenerated(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery("SELECT id FROM mesid",null);
        boolean ret = cursor.moveToFirst();

        cursor.close();

        return ret;
    }

    public void generateMyDeviceId(SQLiteDatabase db) {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
        StringBuilder secureIdBuilder = new StringBuilder();
        for (int i = 0; i < 41; i++) {
            int index = (int) (Math.random() * alphabet.length());
            secureIdBuilder.append(alphabet.charAt(index));
        }
        this.secureId = secureIdBuilder.toString();
        db.execSQL("INSERT INTO mesid(device_id) VALUES(?)",new String[]{secureId});

    }
    public String getSecureId(){
        return this.secureId;
    }
    public void setSecureId(String new_secureId){
        this.secureId = new_secureId;
    }
    private void initConnection() {
        SQLiteOpenHelper dbHelper = new SQLiteOpenHelper(context,"ecosys",null,1) {
            @Override
            public void onCreate(SQLiteDatabase db) {
                createTables(db);

            }
            @Override
            public void onOpen(SQLiteDatabase db){
                createTables(db);

            }

            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

            }
        };
        db = dbHelper.getWritableDatabase();

        if(!isMyDeviceIdGenerated(db)){
            generateMyDeviceId(db);
        }



    }

    public void closedb() {
        if (db != null) {

            db.close();
        }

    }


    // to store different objects in the database
    // this one is specifically made for the binary delta object
    public byte[] serialize(DeltaBinaire.Delta delta) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(delta);
        objectOutputStream.flush();
        return byteArrayOutputStream.toByteArray();
    }

    // Deserialization method
    public static DeltaBinaire.Delta deserialize(String data) throws IOException, ClassNotFoundException {

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data.getBytes());
        ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
        return (DeltaBinaire.Delta) objectInputStream.readObject();

    }

    private void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS retard(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "version_id INTEGER," +
                    "path TEXT," +
                    "mod_type TEXT," +
                    "devices_to_patch TEXT DEFAULT ''," +
                    "type TEXT," +
                    "secure_id TEXT)");
         // CREATE TABLE delta
        db.execSQL("CREATE TABLE IF NOT EXISTS delta(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "path TEXT," +
                    "version_id INTEGER," +
                    "delta TEXT," +
                    "secure_id TEXT)");

            // CREATE TABLE filesystem
        db.execSQL("CREATE TABLE IF NOT EXISTS filesystem(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "path TEXT," +
                    "version_id INTEGER," +
                    "type TEXT," +
                    "size INTEGER," +
                    "secure_id TEXT," +
                    "content BLOB)");

            // CREATE TABLE sync
        db.execSQL("CREATE TABLE IF NOT EXISTS sync(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "secure_id TEXT," +
                    "linked_devices_id TEXT DEFAULT ''," +
                    "root TEXT," +
                    "backup_mode BOOLEAN DEFAULT 0,"+
                    "is_being_patch BOOLEAN DEFAULT 0," +
                "creation_date TIMESTAMP DEFAULT (strftime('%s','now')))"
        );

            // CREATE TABLE linked_devices
        db.execSQL("CREATE TABLE IF NOT EXISTS linked_devices(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "device_id TEXT," +
                    "is_connected BOOLEAN," +
                    "ip_addr TEXT)");
            // CREATE TABLE mesid
        db.execSQL("CREATE TABLE IF NOT EXISTS mesid(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "device_id TEXT," +
                    "accepte_largage_aerien BOOLEAN DEFAULT TRUE)");

            // CREATE TABLE apps
        db.execSQL("CREATE TABLE IF NOT EXISTS apps(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT," +
                    "path TEXT," +
                    "version_id INTEGER," +
                    "type TEXT," +
                    "secure_id TEXT," +
                    "uninstaller_path TEXT)"
                );


        db.execSQL("CREATE TABLE IF NOT EXISTS reseau(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "device_id TEXT," +
                "hostname TEXT,"+
                "ip_addr TEXT)"
        );

        if (!isMyDeviceIdGenerated(db)) {
            generateMyDeviceId(db);
        }
    }

    public void incrementFileVersion(String path) {
        // Get the latest version of the file and increment it
        long newVersionId = getFileLastVersionId(path) + 1;

        // Update version number in the database
        db.execSQL("UPDATE filesystem SET version_id=? WHERE path=? AND secure_id=?",
                new String[]{
                        String.valueOf(newVersionId),
                        path,
                        secureId
                }
        );
    }



    public boolean checkFileExists(String path){
        String[] args = {path,secureId};
        Cursor cursor = db.rawQuery("SELECT id FROM filesystem WHERE path=? AND secure_id=?",args);
        boolean ret = cursor.moveToFirst();
        cursor.close();
        return ret;
    }

    public boolean wasFile(String path) {
        String[] args = {path, secureId};
        Cursor cursor = db.rawQuery("SELECT type FROM filesystem WHERE path=? AND secure_id=?",args);
        boolean ret = cursor.moveToNext() && cursor.getString(0).equals("file");
        cursor.close();
        return ret;
    }

    public void getSecureIdFromRootPath(String rootPath) {
        String[] args = {rootPath};
        Cursor cursor = db.rawQuery("SELECT secure_id FROM sync WHERE root=?",args);
        if (cursor.moveToFirst()) {
            secureId = cursor.getString(0);
        } else {
            throw new IllegalStateException("No secure ID found for the root path: " + rootPath);
        }
        cursor.close();

    }

    public void createFile(String relativePath, DocumentFile file, String flag){
        try {

            InputStream inputStream = context.getContentResolver().openInputStream(file.getUri());

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream);


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                copyFileToGZipOutputStream( inputStream,gzipOutputStream);
            }
            byte[] contentBytes = outputStream.toByteArray();


            ContentValues fileValues = new ContentValues();
            fileValues.put("path", relativePath);
            fileValues.put("version_id", 0);
            fileValues.put("type", "file");
            fileValues.put("size", file.length());
            fileValues.put("secure_id", secureId);
            fileValues.put("content", contentBytes);

            db.insert("filesystem", null, fileValues);



            //db.execSQL("INSERT INTO filesystem (path, version_id, type, size, secure_id, content) VALUES (?,?,?, ?, ?, ?)",args);
            Log.d("Ecosys Server : Database","Added file to filesystem map. return value : ");


            // Handle adding file to retard
            if ("[ADD_TO_RETARD]".equals(flag)) {
                // Build delta
                DeltaBinaire.Delta delta = buildDeltaFromInputStream(relativePath, file.length(), inputStream, 0, new byte[0]);

                // Get offline devices
                Globals.GenArray<String> offlineDevices = getSyncOfflineDevices();

                // Insert delta into delta table

                byte[] serializedData = serialize(delta);
                //String deltaInsertQuery = "INSERT INTO delta (path, version_id, delta, secure_id) VALUES (?, ?, ?, ?)";

                ContentValues deltaValues = new ContentValues();
                deltaValues.put("path", relativePath);
                deltaValues.put("version_id", getFileLastVersionId(relativePath) + 1);
                deltaValues.put("delta", serializedData);
                deltaValues.put("secure_id", secureId);

                db.insert("delta",null,deltaValues);





                // Insert into retard table
                if(!offlineDevices.isEmpty()){
                    HashMap<String, String> modtypes = Globals.modTypes();

                    StringBuilder strIds = new StringBuilder();
                    for (int i = 0; i < offlineDevices.size(); i++) {
                        strIds.append(offlineDevices.get(i)).append(";");
                    }
                    strIds.deleteCharAt(strIds.length() - 1);

                    String retardInsertQuery = "INSERT INTO retard (version_id, path, mod_type, devices_to_patch, type, secure_id) VALUES (?, ?, ?, ?, 'file', ?)";
                    db.execSQL(retardInsertQuery,new String[]{
                            String.valueOf(getFileLastVersionId(relativePath) + 1),
                            relativePath,
                            modtypes.get("[CREATE]"),
                            strIds.toString(),
                            secureId
                    });
                }


            }

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }




        updateCachedFile(file,relativePath);
    }




    public String getMyDeviceId() {

        Cursor cursor = db.rawQuery("SELECT device_id FROM mesid",null);
        String ret = null;
        if (cursor.moveToNext()) {
            ret = cursor.getString(0);
        }

        cursor.close();
        Log.d("DEVICE ID","device_id="+ret);
        return ret;
    }

    public Globals.GenArray<String> getOfflineDevices() {
        Globals.GenArray<String> offlineDevices = new Globals.GenArray<>();
        Cursor cursor = db.rawQuery("SELECT device_id, is_connected FROM linked_devices",null);
        while (cursor.moveToNext()) {
            String deviceId = cursor.getString(0);
            boolean isConnected = cursor.getInt(1) == 1;
            if (!isConnected) {
                offlineDevices.add(deviceId);
            }
        }

        cursor.close();

        return offlineDevices;
    }

    public Globals.GenArray<String> getOnlineDevices() {
        Globals.GenArray<String> onlineDevices = new Globals.GenArray<String>();
        Cursor cursor = db.rawQuery("SELECT device_id, is_connected FROM linked_devices",null);
        while (cursor.moveToNext()) {
            String deviceId = cursor.getString(0);
            boolean isConnected = cursor.getInt(1) == 1;
            if (isConnected) {
                onlineDevices.add(deviceId);
            }
        }
        cursor.close();
        return onlineDevices;
    }

    public void setDeviceIP(String deviceId, String value) {
        db.execSQL("UPDATE linked_devices SET ip_addr=? WHERE secure_id=? AND device_id=?",
                new String[]{
                        value,
                        secureId,
                        deviceId
                });
    }

    public String getDeviceIP(String deviceId) {

        String ret = null;
        Cursor cursor = db.rawQuery("SELECT ip_addr FROM linked_devices WHERE device_id=?",
            new String[]{deviceId}
        );
        if (cursor.moveToFirst()) {
            ret = cursor.getString(0);
        }

        cursor.close();
        return ret;
    }

    public long getFileLastVersionId(String path) {

        Long ret = null;
        Cursor cursor = db.rawQuery("SELECT version_id FROM filesystem WHERE path=? AND secure_id=?",
                new String[]{path,secureId}
        );

        if (cursor.moveToFirst()) {
            ret = cursor.getLong(0);
        }
        cursor.close();
        return ret;
    }

    public Globals.GenArray<String> getSyncOfflineDevices() {
        Globals.GenArray<String> offlineDevices = new Globals.GenArray<>();
        /*Globals.GenArray<String> linkedDevices = getSyncLinkedDevices();

        if (linkedDevices.isEmpty()) {
            return offlineDevices; // Return empty if there are no linked devices
        }

        // Convert linkedDevices to a String array for selection arguments
        String[] selectionArgs = new String[linkedDevices.size()];
        for (int i = 0; i < linkedDevices.size(); i++) {
            selectionArgs[i] = linkedDevices.get(i);
        }

        // Construct the selection string with placeholders
        StringBuilder selectionBuilder = new StringBuilder();
        selectionBuilder.append("device_id IN (");
        for (int i = 0; i < selectionArgs.length; i++) {
            selectionBuilder.append("?");
            if (i < selectionArgs.length - 1) {
                selectionBuilder.append(",");
            }
        }
        selectionBuilder.append(")");

        // Construct the final selection string
        String selection = selectionBuilder.toString();

        // Execute the query using parameterized selection
        Cursor cursor = db.query(
                "linked_devices",                 // Table name
                new String[]{"device_id", "is_connected"}, // Columns to return
                selection,                       // Selection
                selectionArgs,                   // Selection arguments
                null,                            // Group by
                null,                            // Having
                null                             // Order by
        );

        while (cursor.moveToNext()) {
            String deviceId = cursor.getString(0);
            boolean isConnected = cursor.getInt(1) == 1;
            if (!isConnected) {
                offlineDevices.add(deviceId);
            }
        }
        cursor.close();*/

        offlineDevices = getOfflineDevices();

        Globals.GenArray<String> syncOnlineDevices = new Globals.GenArray<>();
        for(int i=0;i<offlineDevices.size();i++){
            if(isDeviceLinked(offlineDevices.get(i))){
                syncOnlineDevices.add(offlineDevices.get(i));
            }
        }


        return offlineDevices;
    }

    public Globals.GenArray<String> getSyncOnlineDevices() {
        Globals.GenArray<String> onlineDevices = new Globals.GenArray<>();
        /*Globals.GenArray<String> linkedDevices = getSyncLinkedDevices();

        if (linkedDevices.isEmpty()) {
            return onlineDevices; // Return empty if there are no linked devices
        }



        // Construct the selection string with placeholders
        StringBuilder selectionBuilder = new StringBuilder();
        selectionBuilder.append("(");

        for (int i = 0; i < linkedDevices.size(); i++) {

            selectionBuilder.append("'").append(linkedDevices.get(i)).append("'");
            if (i < linkedDevices.size() - 1) {
                selectionBuilder.append(",");
            }
        }
        selectionBuilder.append(")");


        // Execute the query using parameterized selection
        Cursor cursor = db.rawQuery("SELECT device_id,is_connected FROM linked_devices WHERE device_id IN "+selectionBuilder.toString(), new String[]{});

        while (cursor.moveToNext()) {
            String deviceId = cursor.getString(0);
            boolean isConnected = cursor.getInt(1) == 1;
            if (isConnected) {
                onlineDevices.add(deviceId);
            }
        }
        cursor.close();*/



        /*Log.d(TAG,"Linked devices : "+linkedDevices.size());
        for(int i=0;i<linkedDevices.size();i++){
            if(isDeviceConnected(linkedDevices.get(i))){
                onlineDevices.add(linkedDevices.get(i));
            }
        }*/

        onlineDevices = getOnlineDevices();

        Globals.GenArray<String> syncOnlineDevices = new Globals.GenArray<>();
        for(int i=0;i<onlineDevices.size();i++){
            if(isDeviceLinked(onlineDevices.get(i))){
                syncOnlineDevices.add(onlineDevices.get(i));
            }
        }

        return syncOnlineDevices;
    }

    public void updateFile(String path, DeltaBinaire.Delta delta,DocumentFile file,boolean needSAF) {
        Globals.GenArray<String> offlineDevices = getSyncOfflineDevices();
        if (!offlineDevices.isEmpty()) {
            long newVersionId = getFileLastVersionId(path) + 1;
            incrementFileVersion(path);
                try{
                    byte[] SerializedData = serialize(delta);
                    db.execSQL(
                            "INSERT INTO delta (path, version_id, delta, secure_id) VALUES (?, ?, ?, ?)",
                            new Object[]{path,
                                    String.valueOf(newVersionId),
                                    SerializedData,
                                    secureId
                            }
                    );
                }catch (IOException e){

                }

                String modType = "p";
                String strIds = offlineDevices.join(";");
                db.execSQL("INSERT INTO retard (version_id, path, mod_type, devices_to_patch, type, secure_id) VALUES (?, ?, ?, ?, 'file', ?)",
                        new String[]{
                                String.valueOf(newVersionId),
                                path,
                                modType,
                                strIds,
                                secureId
                        }
                        );
        }
        updateCachedFile(path,file,needSAF,"");
    }

    public void updateCachedFile(String relativePath,DocumentFile file,boolean needSAF,String absoluteFilePath) {
        byte[] fileContent = new byte[0];
        try {
            fileContent = readFromFile(absoluteFilePath,file,needSAF);
            byte[] compressedContent = compressData(fileContent);
            db.execSQL("UPDATE filesystem SET content=?,size=? WHERE path=? AND secure_id=?",
                    new Object[]{
                            compressedContent,
                            file.length(),
                            relativePath,
                            secureId
                    }
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private byte[] readFromFile(String path,DocumentFile file,boolean needSAF) throws IOException {

        if(!needSAF){
            try (FileInputStream inputStream = new FileInputStream(path)) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, length);
                }
                return outputStream.toByteArray();
            }
        }else{
            InputStream inputStream =
                    context.getContentResolver().openInputStream(file.getUri());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return inputStream.readAllBytes();
            }
        }

        return null;

    }

    private byte[] compressData(byte[] data) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            gzipOutputStream.write(data);
            gzipOutputStream.close();
            return outputStream.toByteArray();
        }
    }


    // GetFileContent retrieves the content of a file from the database.
    // Returned as byte array
    public byte[] getFileContent(String path) {
        Cursor cursor = db.rawQuery("SELECT content FROM filesystem WHERE path=? AND secure_id=?",new String[]{
                path,
                secureId
        });
        if (cursor.moveToFirst()) {
            byte[] compressedContent = cursor.getBlob(0);
            try (InputStream inputStream = new GZIPInputStream(new ByteArrayInputStream(compressedContent));
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, length);
                }
                return outputStream.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        cursor.close();

        return null;
    }

    // RmFile deletes a file from the database and adds it in delete mode to the retard table.
    public void rmFile(String path) {


        db.execSQL("DELETE FROM filesystem WHERE path=? AND secure_id=?",new String[]{
                path,
                secureId
        });

            // Now, purge all data involving this file from retard table
       db.execSQL("DELETE FROM retard WHERE path=? AND secure_id=?",
                new String[]{
                        path,
                        secureId
                }
        );

        // And finally, add it in delete mode to the retard table
        HashMap<String, String> modtypes = Globals.modTypes();
        String modType = modtypes.get("delete");
        String linkedDevices = getSyncLinkedDevices().join(";");
        db.execSQL("INSERT INTO retard (version_id,path,mod_type,devices_to_patch,type,secure_id) VALUES(?,?,?,?,?,?)",
                new String[]{
                        "0",
                        path,
                        modType,
                        linkedDevices,
                        "file",
                        secureId
                    }
                );

    }


    // CreateFolder adds a folder to the database.
    public void createFolder(String path,String flag) {
        db.execSQL("INSERT INTO filesystem (path, version_id, type, size, secure_id) VALUES (?, 0, 'folder', 0, ?)",
                new String[]{
                        path,
                        secureId
                    }
                );


        if(flag.equals("[ADD_TO_RETARD]")){
            Globals.GenArray<String> offlineDevices = getSyncOfflineDevices();

            // Insert into retard table
            if(!offlineDevices.isEmpty()){
                HashMap<String, String> modtypes = Globals.modTypes();

                StringBuilder strIds = new StringBuilder();
                for (int i = 0; i < offlineDevices.size(); i++) {
                    strIds.append(offlineDevices.get(i)).append(";");
                    Log.d(TAG,"Adding folder to retard table for device : "+offlineDevices.get(i));
                }
                strIds.deleteCharAt(strIds.length() - 1);

                String retardInsertQuery = "INSERT INTO retard (version_id, path, mod_type, devices_to_patch, type, secure_id) VALUES (?, ?, ?, ?, 'folder', ?)";
                db.execSQL(retardInsertQuery,new String[]{
                        String.valueOf(getFileLastVersionId(path) + 1),
                        path,
                        modtypes.get("creation"),
                        strIds.toString(),
                        secureId
                });
            }

        }
    }

    // RmFolder deletes a folder from the database and adds it in delete mode to the retard table.
    public void rmFolder(String path) {
        db.execSQL("DELETE FROM filesystem WHERE path LIKE ? AND secure_id=?",
            new String[]{
                    path+"%",
                    secureId
            });

        // Now, purge all data involving this folder from retard table
        db.execSQL("DELETE FROM retard WHERE path LIKE ? AND secure_id=?",
                new String[]{
                        path+"%",
                        secureId
                }
                );

                // Purge all data from delta table involving this folder
       db.execSQL("DELETE FROM delta WHERE path LIKE ? AND secure_id=?",
                new String[]{
                        path+"%",
                        secureId
                }
                );


        // And finally, add it in delete mode to the retard table
        HashMap<String, String> modtypes = Globals.modTypes();
        String modType = modtypes.get("delete");
        String linkedDevices = getSyncLinkedDevices().join(";");
        db.execSQL("INSERT INTO retard (version_id,path,mod_type,devices_to_patch,type,secure_id) VALUES(?,?,?,?,?,?)",
                new String[]{
                        "0",
                        path,
                        modType,
                        linkedDevices,
                        "folder",
                        secureId
                });


    }

    // Move updates the path of a file or folder in the database and adds a move event to the retard table.
    public void move(String path, String newPath, String fileType) {


        db.execSQL("UPDATE filesystem SET path=? WHERE path=? AND secure_id=?",
                new String[]{
                        newPath,
                        path,
                        secureId
                }
                );

        // Add the move event to retard file
        HashMap<String, String> modtypes = Globals.modTypes();
        String modType = modtypes.get("move");
        String linkedDevices = getSyncLinkedDevices().join(";");
        db.execSQL("INSERT INTO retard (version_id,path,mod_type,devices_to_patch,type,secure_id) VALUES(?,?,?,?,?,?)",
                new String[]{
                        "0",
                        path,
                        linkedDevices,
                        fileType,
                        secureId
                }
                );

    }

    public void addFilesToFileSystem(String rootPath) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Files.walkFileTree(Paths.get(rootPath), new FileVisitor(rootPath));
            }
            System.out.println("Finished mapping the folder.");
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error while registering files and folders for the first time.");
        }
    }

    public boolean checkAppExistenceFromSecureId(String secureId) {
        Cursor cursor = db.rawQuery("SELECT * FROM apps WHERE secure_id=?",new String[]{
                secureId
        });

        boolean ex = cursor.moveToFirst();

        cursor.close();

        return ex;
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    private class FileVisitor extends SimpleFileVisitor<Path> {
        private final String rootPath;

        public FileVisitor(String rootPath) {
            this.rootPath = rootPath;
        }

        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {


            DocumentFile dcf;
            if(file.toFile().isDirectory()){
                dcf = DocumentFile.fromTreeUri(context,Uri.parse(file.toUri().toString()));

            }else{
                dcf = DocumentFile.fromSingleUri(context,Uri.parse(file.toUri().toString()));

            }

            String relativePath = dcf.getUri().getPath().replace(getRootSyncPath(),"");

            // Add file to the database or perform other actions as needed
            createFile(relativePath,dcf, "");
            System.out.println("Registering: " + relativePath);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return FileVisitResult.CONTINUE;
            }else{
                return null;
            }        }

        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            String relativePath = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                relativePath = rootPath + File.separator + dir.getFileName().toString();
            }
            // Add folder to the database or perform other actions as needed
            createFolder(relativePath,"");
            System.out.println("Registering: " + relativePath);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return FileVisitResult.CONTINUE;
            }else{
                return null;
            }
        }

        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            System.err.println("Error accessing path: " + file.toString() + " " + exc.getMessage());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return FileVisitResult.CONTINUE;
            }else{
                return null;
            }
        }
    }

    // CreateSync initializes a new synchronization entry in the database.
    public void createSync(String rootPath) {
        // Generate secure_id
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
        StringBuilder secureIdBuilder = new StringBuilder();
        for (int i = 0; i < 41; i++) {
            int index = (int) (Math.random() * alphabet.length());
            secureIdBuilder.append(alphabet.charAt(index));
        }
        String secureId = secureIdBuilder.toString();

        db.execSQL("INSERT INTO sync (secure_id, root) VALUES(?,?)",
                new String[]{
                        secureId,
                        rootPath
                }
        );

        this.secureId = secureId;


        // ICI AJOUTER LA CARTOGRAPHIE DU SYSTEME DE FICHIERS
        addFilesToFileSystem(rootPath);
    }

    // CreateSyncFromOtherEnd creates a synchronization entry in the database with the given info.
    // Used to connect from an existing task from another device
    // Filesystem is not mapped by the function as a remote setup procedure is made around this call
    public void createSyncFromOtherEnd(String rootPath, String secureId) {
        this.secureId = secureId;
        // Add synchronization entry to the database
        db.execSQL("INSERT INTO sync (secure_id, root) VALUES (?, ?)",
                new String[]{
                        secureId,
                        rootPath
                }
                );
    }

    public void rmSync() {
            // Remove synchronization entry from the database
        db.execSQL("DELETE FROM sync WHERE secure_id=?",
                new String[]{
                        secureId
                }
                );

        db.execSQL("DELETE FROM delta WHERE secure_id=?",
                new String[]{
                        secureId
                }
        );

        db.execSQL("DELETE FROM filesystem WHERE secure_id=?",
                new String[]{
                        secureId
                }
        );

        db.execSQL("DELETE FROM retard WHERE secure_id=?",
                new String[]{
                        secureId
                }
        );
    }

    public void linkDevice(String deviceId, String ipAddress) {
        // Link a device to the synchronization entry
        db.execSQL("UPDATE sync SET linked_devices_id=IFNULL(linked_devices_id, '') || ? WHERE secure_id=?",
                new String[]{
                        deviceId+";",
                        secureId
                }
                );


        if (!isDeviceLinked(deviceId)) {
            // If the device is not registered as a target, register it
            db.execSQL("INSERT INTO linked_devices (device_id,is_connected,ip_addr) VALUES(?,TRUE,?)",
                    new String[]{
                            deviceId,
                            ipAddress
                    }
                    );
        }


    }

    public void unlinkDevice(String deviceId) {
        // Unlink a device from the synchronization entry
        db.execSQL("DELETE FROM linked_devices WHERE device_id=?",
                new String[]{
                        deviceId
                }
                );

        db.execSQL("UPDATE sync SET linked_devices_id=REPLACE(linked_devices_id,?, '')",
                new String[]{deviceId+";"}
                );

    }

    public String getRootSyncPath() {
        String rootPath = null;
        // Retrieve the root path associated with the synchronization entry
        Cursor cursor = db.rawQuery("SELECT root FROM sync WHERE secure_id=?",
                new String[]{
                        secureId
                }
                );
        if (cursor.moveToFirst()) {
            rootPath = cursor.getString(0);
        }

        cursor.close();

        return rootPath;
    }

    public void setDevicedbState(String deviceId, boolean value, String... ipAddr) {


        db.execSQL("UPDATE linked_devices SET is_connected=?,ip_addr=? WHERE device_id=?",
                new  Object[]{
                        value,
                        ipAddr[0],
                        deviceId
                }
                );

    }

    public void setDevicedbState(String deviceId, boolean value) {

        db.execSQL("UPDATE linked_devices SET is_connected=? WHERE device_id=?",
                new Object[]{
                        value,
                        deviceId
                }
        );
    }
    public boolean getDevicedbState(String deviceId) {
        boolean dbState = false;
        Cursor cursor = db.rawQuery("SELECT is_connected FROM linked_devices WHERE device_id=?",
                new String[]{
                        deviceId
                }
                );
        if(cursor.moveToNext()) {
            dbState = cursor.getInt(0)== 1;
        }

        cursor.close();

        return dbState;
    }

    public boolean isThisFileSystemBeingPatched() {

        Cursor cursor = db.rawQuery("SELECT is_being_patch FROM sync WHERE secure_id=? AND is_being_patch=1",
                new String[]{
                        secureId
                }
                );

        return cursor.moveToFirst();
    }

    public void setFileSystemPatchLockState(String deviceId, boolean value) {

        db.execSQL("UPDATE sync SET is_being_patch=? WHERE secure_id=?",
                new Object[]{
                        value ? 1: 0,
                        secureId
                }
        );


    }

    public long getFileSizeFromBdd(String path) {
        long size = 0;
        Cursor cursor = db.rawQuery("SELECT size FROM filesystem WHERE path=? AND secure_id=?",
                new String[]{
                        path,
                        secureId
                }
                );
        if (cursor.moveToNext()) {
            size = cursor.getLong(0);
        }

        cursor.close();
        return size;
    }

    public DeltaBinaire.Delta getFileDelta(long version, String path) {
        byte[] SerializedData = null;
        Cursor cursor = db.rawQuery("SELECT delta FROM delta WHERE path=? AND version_id=?",
                    new String[]{
                            path,
                            String.valueOf(version)
                    }
                    );
        if (cursor.moveToNext()) {
            SerializedData = cursor.getBlob(0);

            try (cursor) {
                return deserialize(SerializedData.toString());

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }

        }

        return  null;


    }

    public boolean isDeviceLinked(String deviceId) {
        boolean isLinked = false;
        Cursor cursor = db.rawQuery("SELECT COUNT(*) AS count FROM linked_devices WHERE device_id=?",
                new String[]{
                        deviceId
                }
        );

        if (cursor.moveToNext()) {
            int count = cursor.getInt(0);
            isLinked = count > 0;
        }

        cursor.close();

        return isLinked;
    }




    public void updateCachedFile(DocumentFile file, String relativePath) {
        try {
            // Read the current state of the given file and update it in the database
            try (InputStream fis = context.getContentResolver().openInputStream(file.getUri());
                 ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 GZIPOutputStream gzipos = new GZIPOutputStream(bos)) {

                byte[] buffer = new byte[1024];
                int length;
                while ((length = fis.read(buffer)) != -1) {
                    gzipos.write(buffer, 0, length);
                }
                gzipos.close();
                byte[] compressedData = bos.toByteArray();


                db.execSQL("UPDATE filesystem SET content=?,size=? WHERE path=? AND secure_id=?",
                        new Object[]{
                                compressedData,
                                file.length(),
                                relativePath,
                                secureId
                        }
                        );
                }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Map<String, Globals.SyncInfos> listSyncAllTasks() {
        // Used to get all sync task secure_id and root path listed
        // Returns a map containing the secure_id as key and SyncInfos as value
        Map<String, Globals.SyncInfos> list = new HashMap<>();
        Cursor cursor = db.rawQuery("SELECT secure_id,root FROM sync", null);

        while (cursor.moveToNext()) {
            Globals.SyncInfos info = new Globals.SyncInfos("","");
            info.setSecureId(cursor.getString(0));
            this.secureId = info.getSecureId();

            info.setApp(isApp());

            if(info.isApp()){
                info.setName("(application) "+getAppName());
            }
            info.setPath(cursor.getString(1));
            info.setBackup_mode(isSyncInBackupMode());
            Log.d("Ecosys server : Syncinfos",info.toString());
            list.put(info.getSecureId(), info);
        }

        secureId = null;

        cursor.close();

        return list;
    }

    public boolean isApp(){
        Cursor cursor = db.rawQuery("SELECT * FROM apps WHERE secure_id=?",new String[]{
                this.secureId
        });

        boolean ex = cursor.moveToFirst();

        cursor.close();

        return ex;
    }

    public String getAppName(){
        Cursor cursor = db.rawQuery("SELECT name FROM apps WHERE secure_id=?",new String[]{
                this.secureId
        });

        cursor.moveToFirst();

        String name = cursor.getString(0);

        cursor.close();

        return name;
    }

    public Map<String, Globals.GenArray<Globals.QEvent>> buildEventQueueFromRetard(String deviceId) {
        // As the device can be late on many tasks, we must create a map with all
        // the different deltas on all different tasks it's late on


        // FILES
        Map<String, Globals.GenArray<Globals.QEvent>> queue = new HashMap<>();
        Cursor cursor = db.rawQuery("SELECT r.secure_id,d.delta,r.mod_type,r.path,r.type,r.version_id FROM retard AS r JOIN delta AS d ON r.path=d.path AND r.version_id=d.version_id AND r.secure_id=d.secure_id WHERE r.devices_to_patch LIKE ?",

                new String[]{
                        "%"+deviceId+"%"
                }
                );
        while (cursor.moveToNext()) {
            // build delta
            Globals.QEvent event = new Globals.QEvent(
                    "",
                    "",
                    null,
                    "",
                    "",
                    "",
                    0
                    );
            event.setFlag(Globals.modTypesReverse().get(cursor.getString(2)));
            event.setFileType(cursor.getString(4));
            int versionId = cursor.getInt(4);
            // as ion creation version_id will be set to zero
            event.setVersionToPatch(  versionId == 0 ? versionId:versionId-1 );

            try{
                ObjectInputStream obj = new ObjectInputStream(new
                        ByteArrayInputStream(cursor.getBlob(1))
                        );

                DeltaBinaire.Delta delta = (DeltaBinaire.Delta) obj.readObject();
                obj.close();

                event.setDelta(delta);
                event.setFilePath(cursor.getString(3));
            }catch (IOException | ClassNotFoundException e){
                Log.d(TAG,"Error while adding delta to event");
                e.printStackTrace();
            }

            event.setSecureId(cursor.getString(0));

            if (!queue.containsKey(event.getSecureId())) {
                queue.put(event.getSecureId(), new Globals.GenArray<>());
            }
            Globals.GenArray<Globals.QEvent> events = queue.get(event.getSecureId());
            assert events != null;
            events.add(event);
            queue.put(event.getSecureId(), events);
        }


        // FOLDERS

        cursor = db.rawQuery(
                "SELECT r.secure_id,r.mod_type,r.path,r.type FROM retard AS r WHERE r.devices_to_patch LIKE ? AND r.type='folder'",

                new String[]{
                        "%"+deviceId+"%"
                }
                );
        while (cursor.moveToNext()) {
            // build delta
            Globals.QEvent event = new Globals.QEvent(
                    "",
                    "",
                    null,
                    "",
                    "",
                    "",
                    0
            );
            event.setFlag(Globals.modTypesReverse().get(cursor.getString(1)));
            event.setFileType(cursor.getString(3));
            event.setFilePath(cursor.getString(2));
            event.setSecureId(cursor.getString(0));
            // the stored version_id is the version resulting of the patch, not the version to which apply the patch
            event.setVersionToPatch(0);

            if (!queue.containsKey(event.getSecureId())) {
                queue.put(event.getSecureId(), new Globals.GenArray<>());
            }
            Globals.GenArray<Globals.QEvent> events = queue.get(event.getSecureId());
            assert events != null;
            events.add(event);
            queue.put(event.getSecureId(), events);
        }

        cursor.close();

        return queue;
    }



    public void removeDeviceFromRetard(String deviceId) {
        Cursor cursor = db.rawQuery("SELECT devices_to_patch FROM retard WHERE devices_to_patch LIKE ?",
                new String[]{
                    "%" + deviceId + "%"
                }
        );
        if (cursor.moveToFirst()) {
            String idsStr = cursor.getString(0);
            String[] idsList = idsStr.split(";");
            StringBuilder newIds = new StringBuilder();
            for (String id : idsList) {
                if (!id.equals(deviceId)) {
                    newIds.append(id).append(";");
                }
            }
            if (newIds.length() > 0) {
                newIds.deleteCharAt(newIds.length() - 1);
                db.execSQL("UPDATE retard SET devices_to_patch= ? WHERE devices_to_patch LIKE ?",
                        new String[]{
                                newIds.toString(),
                                "%" + deviceId + "%"
                        }
                );
            } else {
                db.execSQL("DELETE FROM retard WHERE devices_to_patch LIKE ?",
                        new String[]{
                            "%" + deviceId + "%"
                        }
                );
                db.execSQL("DELETE FROM delta WHERE secure_id=?",
                        new String[]{
                                secureId
                        }
                );
            }
        }
        cursor.close();
    }

    public void removeDeviceFromRetardOneFile(String deviceId,String relativePath,long versionId) {


        Cursor cursor = db.rawQuery(
                "SELECT devices_to_patch FROM retard " +
                        "WHERE devices_to_patch LIKE ? " +
                        "AND path=? " +
                        "AND version_id="+versionId+" "+
                        "AND secure_id=?",
                new String[]{
                        "%" + deviceId + "%",
                        relativePath,
                        secureId
                }
        );


        // check if a row has been returned
        // if not
        // main reason : a problem led to unsychronised files versioning,
        // retrying with the oldest version present in retard table
        // associated with the device id
        if (!cursor.moveToFirst()) {
            cursor.close();
            cursor = db.rawQuery(
                    "SELECT min(version_id) FROM retard " +
                            "WHERE devices_to_patch LIKE ? " +
                            "AND path=? " +
                            "AND secure_id=?",
                    new String[]{
                            "%" + deviceId + "%",
                            relativePath,
                            secureId
                    }
            );

            if(cursor.moveToFirst()){
                // update version id with the closest one to a true match
                // as the event that came first has the best chance to be patched first
                // (not assured but the others are coming very soon if there are any so not really a problem )
                versionId = cursor.getLong(0);
            }else {
                // no rows to delete, can happen if all devices are online
                return;
            }

            cursor.close();

            // now, we can retry :
            cursor = db.rawQuery(
                    "SELECT devices_to_patch FROM retard " +
                            "WHERE devices_to_patch LIKE ? " +
                            "AND path=? " +
                            "AND version_id="+versionId+" "+
                            "AND secure_id=?",
                    new String[]{
                            "%" + deviceId + "%",
                            relativePath,
                            secureId
                    }
            );

            if(!cursor.moveToFirst()){
                // no rows to delete, can happen if all devices are online
                return;
            }

        }


        String idsStr = cursor.getString(0);
        String[] idsList = idsStr.split(";");
        StringBuilder newIds = new StringBuilder();
        for (String id : idsList) {
            if (!id.equals(deviceId)) {
                newIds.append(id).append(";");
            }
        }
        if (newIds.length() > 0) {
            newIds.deleteCharAt(newIds.length() - 1);
            db.execSQL("UPDATE retard SET devices_to_patch= ?" +
                            " WHERE devices_to_patch LIKE ?" +
                            "AND path=? " +
                            "AND version_id="+versionId+" "+
                            "AND secure_id=?",
                    new String[]{
                            newIds.toString(),
                            "%" + deviceId + "%",
                            relativePath,
                            secureId
                    }
            );
        } else {
            db.execSQL("DELETE FROM retard " +
                    "WHERE devices_to_patch LIKE ?"+
                    "AND path=? " +
                    "AND version_id="+versionId+" "+
                    "AND secure_id=?",
                    new String[]{
                            "%" + deviceId + "%",
                            relativePath,
                            secureId
                    }
            );
            db.execSQL("DELETE FROM delta " +
                            "WHERE secure_id=?"+
                            "AND version_id="+versionId+" "+
                            "AND path=?",
                    new String[]{
                            secureId,
                            relativePath
                    }
            );
        }

        cursor.close();
    }

    public boolean needsUpdate(String deviceId) {
        Cursor cursor = db.rawQuery("SELECT devices_to_patch FROM retard WHERE devices_to_patch LIKE ?",
                new String[]{
                    "%" + deviceId + "%"
                }
        );
        boolean result = cursor.getCount() > 0;
        cursor.close();
        return result;
    }

    public void addToutEnUn(Globals.ToutEnUnConfig data) {
        db.execSQL("INSERT INTO apps (name,path,version_id,type,secure_id,uninstaller_path) VALUES(?,?,?,?,?,?)",
                new Object[]{
                        data.getAppName(),
                        data.getAppLauncherPath(),
                        1,
                        "toutenun",
                        secureId,
                        data.getAppUninstallerPath()
                }
        );
    }

    /*public void addGrapin(Globals.GrapinConfig data) {
        db.execSQL("INSERT INTO apps (name,path,version_id,type,uninstaller_path) VALUES(?,?,?,?,?)",
                new Object[]{data.getAppName(), "[GRAPIN]", 1, "grapin", secureId, "[GRAPIN]"});
    }*/

    public Globals.GenArray<Globals.MinGenConfig> listInstalledApps() {
        Globals.GenArray<Globals.MinGenConfig> configs = new Globals.GenArray<>();
        Cursor cursor = db.rawQuery("SELECT name,id,path,type FROM apps", null);
        while (cursor.moveToNext()) {
            Globals.MinGenConfig config = new Globals.MinGenConfig(
                    cursor.getString(0),
                    cursor.getInt(1),
                    cursor.getString(2),
                    cursor.getString(3),
                    secureId,
                    ""
            );
            configs.add(config);
        }
        cursor.close();
        return configs;
    }

    public Globals.MinGenConfig getAppConfig(int appId) {
        Cursor cursor = db.rawQuery("SELECT name,id,path,type,secure_id,uninstaller_path FROM apps WHERE id=?",
                new String[]{
                        String.valueOf(appId)
                }
        );
        if (cursor.moveToFirst()) {
            Globals.MinGenConfig config = new Globals.MinGenConfig(
                    cursor.getString(0),
                    cursor.getInt(1),
                    cursor.getString(2),
                    cursor.getString(3),
                    cursor.getString(4),
                    cursor.getString(5)
            );

            return config;

        }
        cursor.close();
        return null;
    }

    public void deleteApp(int appId) {
        db.execSQL("DELETE FROM apps WHERE id=?", new Object[]{appId});
    }

    public boolean areLargageAerienAllowed() {
        boolean ret = false;
        Cursor cursor = db.rawQuery("SELECT accepte_largage_aerien FROM mesid", null);
        if (cursor.moveToFirst()) {
            ret = cursor.getInt(0) == 1;
        }
        cursor.close();
        return ret;
    }

    public boolean switchLargageAerienAllowingState() {
        db.execSQL("UPDATE mesid SET accepte_largage_aerien=NOT accepte_largage_aerien");
        return !areLargageAerienAllowed();
    }
    public Globals.GenArray<String> getSyncLinkedDevices() {
        Globals.GenArray<String> devicesList = new Globals.GenArray<>();

        // Define the SQL query to retrieve linked devices
        Cursor cursor = db.rawQuery("SELECT linked_devices_id FROM sync WHERE secure_id=?",
                new String[]{
                        secureId
                }
                );

        if (cursor != null) {
            try {
                // Move the cursor to the first row
                if (cursor.moveToFirst()) {
                    do {
                        // Get the linked_devices_id from the cursor
                        String devices_id = cursor.getString(0);

                        // Split the string and add each device to the devicesList
                        String[] devices = devices_id.split(";");
                        for (String device : devices) {
                            devicesList.add(device);
                        }
                    } while (cursor.moveToNext());
                }
            } finally {
                cursor.close(); // Close the cursor when done
            }
        } else {
            // as the secure_id could change while the files watcher is running
            // in the case of an app link
            // no rows could match the select at the create event of the setup for example
            // and the create event is still trigered as the filesystem lock does not
            // match the old secure_id
            Log.d("AccesBdd", "Cursor is null, the secure_id must have changed while ecosys is running");
        }

        // Remove the last slot (empty space) in the array
        if (!devicesList.isEmpty()) {
            devicesList.popLast();
        }

        return devicesList;
    }


    public Globals.GenArray<String> getLinkedDevices() {
        Globals.GenArray<String> devicesList = new Globals.GenArray<>();

        // Define the SQL query to retrieve linked devices
        Cursor cursor = db.rawQuery("SELECT device_id FROM linked_devices",null);

        if (cursor != null) {
            try {
                // Move the cursor to the first row
                if (cursor.moveToFirst()) {
                    do {
                        // Get the linked_devices_id from the cursor
                        String device_id = cursor.getString(0);
                        devicesList.add(device_id);

                    } while (cursor.moveToNext());
                }
            } finally {
                cursor.close(); // Close the cursor when done
            }
        } else {
            Log.e("AccesBdd", "Cursor is null");
        }

        return devicesList;
    }



    public void removeDeviceFromNetworkMap(String device_id, String ip_addr){

        db.execSQL("DELETE FROM reseau WHERE device_id=? and ip_addr=?",
                new String[]{
                        device_id,
                        ip_addr
                }
                );

    }



    public void addDeviceInNetworkMap(String device_id, String ip_addr,String hostname){
        db.execSQL("INSERT INTO reseau(device_id,ip_addr,hostname) VALUES(?,?,?)",
                new String[]{
                        device_id,
                        ip_addr,
                        hostname
                }
        );
    }

    public Globals.GenArray<Map<String, String>> getNetworkMap(){
        Cursor cursor = db.rawQuery("SELECT device_id,ip_addr,hostname FROM reseau",null);


        Globals.GenArray<Map<String, String>> ret = new Globals.GenArray<>();
        while(cursor.moveToNext()){
            Map<String,String> device = new HashMap<>();
            device.put("device_id",cursor.getString(0));
            device.put("ip_addr",cursor.getString(1));
            device.put("hostname",cursor.getString(2));

            ret.add(device);

        }

        cursor.close();
        return ret;
    }


    public boolean isDeviceOnNetworkMap(String ip_addr){
        Cursor cursor = db.rawQuery("SELECT * FROM reseau WHERE ip_addr=?",new String[]{
                ip_addr
        });


        boolean ret = cursor.moveToFirst();

        cursor.close();
        return ret;
    }

    public void cleanNetworkMap(){
        db.execSQL("DELETE FROM reseau");
    }


    public static void copyFileToGZipOutputStream(InputStream in, GZIPOutputStream out) throws IOException {
            // Transfer bytes from in to out
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }


    }

    public boolean isSyncInBackupMode() {
        Cursor cursor = db.rawQuery("SELECT backup_mode FROM sync WHERE secure_id=?",
                new String[]{
                        this.secureId
                    }
                );
        boolean bcp_mode = false;

        if(cursor.moveToFirst()){
            bcp_mode = cursor.getInt(0) == 1;
        }

        cursor.close();

        return bcp_mode;

    }
    public void toggleBackupMode() {
        db.execSQL("UPDATE sync SET backup_mode = NOT backup_mode WHERE secure_id=?", new String[]{
                this.secureId
        });


    }


    public boolean checkAppExistenceFromName(String name){
        Cursor cursor = db.rawQuery("SELECT * FROM apps WHERE name=?",new String[]{
                name
        });

        boolean ex = cursor.moveToFirst();

        cursor.close();

        return ex;
    }


    public void updateSyncId(String root,String secure_id) {

        // we don't need to add a % on mobile as the root path is exactly the one built
        // before calling this method
        String[] args = {root};
        Cursor cursor = db.rawQuery("SELECT secure_id FROM sync WHERE root=?",args);
        String old_secure_id = null;
        if (cursor.moveToFirst()) {
            old_secure_id = cursor.getString(0);
        } else {
            throw new IllegalStateException("No secure ID found for the root path: " + root);
        }
        cursor.close();




        db.execSQL("UPDATE sync SET secure_id = ? WHERE secure_id=?", new String[]{
                secure_id,
                old_secure_id
        });

        db.execSQL("UPDATE filesystem SET secure_id = ? WHERE secure_id=?", new String[]{
                secure_id,
                old_secure_id
        });
        db.execSQL("UPDATE apps SET secure_id = ? WHERE secure_id=?", new String[]{
                secure_id,
                old_secure_id
        });
    }

    public long getSyncCreationDate(){
        Cursor cursor = db.rawQuery("SELECT creation_date FROM sync WHERE secure_id=?",new String[]{
                secureId
        });

        long timestamp = 0;
        if(cursor.moveToFirst()){
            timestamp = cursor.getLong(0);
        }

        cursor.close();
        return timestamp;
    }

    public void cleanFilesystemLocksFromDb(){
        db.execSQL("UPDATE sync SET is_being_patch=0");
    }


    public void refreshCorrespondingRetardRow(String relativePath, String modtype) {
        long versionId = getFileLastVersionId(relativePath);

        // Remove the outdated retard entry
        db.execSQL(
                "DELETE FROM retard WHERE path=? AND version_id=? AND secure_id=?",
                new String[]{
                        relativePath,
                        String.valueOf(versionId),
                        secureId
                }
        );

        // Get only offline devices
        Globals.GenArray<String> offlineDevices = getSyncOfflineDevices();
        if (!offlineDevices.isEmpty()) {
            // Add a line in retard table with all devices linked and the version number
            StringBuilder strIdsBuilder = new StringBuilder();
            for (int i =0;i<offlineDevices.size();i++) {
                strIdsBuilder.append(offlineDevices.get(i)).append(";");
            }
            // Remove the last semicolon
            strIdsBuilder.setLength(strIdsBuilder.length() - 1);
            String strIds = strIdsBuilder.toString();

            try {
                db.execSQL(
                        "INSERT INTO retard (version_id, path, mod_type, devices_to_patch, type, secure_id) VALUES(?,?,?,?,?,?)",
                        new Object[]{
                                versionId,
                                relativePath,
                                modtype,
                                strIds,
                                "file",
                                secureId
                        }
                );
            } catch (SQLException e) {
                System.err.println("Error in refreshCorrespondingRetardRow() " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Update the cached file content to build the next delta (needs absolute path to the file)

        DocumentFile file;

        if(isApp()){
            file = DocumentFile.fromFile(new File(getRootSyncPath(),relativePath));
        }else{
            file = DocumentFile.fromTreeUri(
                    context,
                    Uri.parse(getRootSyncPath())
            );

            String[] parts = relativePath.split("/");

            // null is supposed impossible as we are treating an event on this exact path
            // well, I might regret not handling this...
            for(String part : parts){
                file = file.findFile(part);
                if(file == null){
                    throw new RuntimeException("Broken path (part does not exists) while refreshing retard table : "+relativePath );
                }
            }
        }
        updateCachedFile(file, relativePath);
    }






    public void storeReceivedEventForOthersDevices(Globals.QEvent event) {
        // Map of modification types
        HashMap<String, String> MODTYPES = new HashMap<>();
        MODTYPES.put("creation", "c");
        MODTYPES.put("delete", "d");
        MODTYPES.put("patch", "p");
        MODTYPES.put("move", "m");

        // Get only offline devices
        Globals.GenArray<String> offlineDevices = getSyncOfflineDevices();
        if (!offlineDevices.isEmpty()) {
            StringBuilder strIdsBuilder = new StringBuilder();
            for (int i = 0; i < offlineDevices.size(); i++) {
                strIdsBuilder.append(offlineDevices.get(i)).append(";");
            }
            // Remove the last semicolon
            String strIds = strIdsBuilder.substring(0, strIdsBuilder.length() - 1);

            if ("file".equals(event.getFileType())) {
                switch (event.getFlag()) {
                    case "[UPDATE]":
                        try {
                            db.execSQL("INSERT INTO delta (path,version_id,delta,secure_id) VALUES(?,?,?,?)",
                                    new Object[]{
                                            event.getFilePath(),
                                            event.getVersionToPatch() + 1,
                                            serialize(event.getDelta()),
                                            secureId
                                    });

                            db.execSQL("INSERT INTO retard (version_id,path,mod_type,devices_to_patch,type,secure_id) VALUES(?,?,?,?,\"file\",?)",
                                    new Object[]{
                                            event.getVersionToPatch() + 1,
                                            event.getFilePath(),
                                            MODTYPES.get("patch"),
                                            strIds,
                                            secureId
                                    });
                        } catch (SQLException e) {
                            Log.e("DB_ERROR", "Error while storing binary delta in database: " + e.getMessage());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        break;

                    case "[CREATE]":
                        try {
                            db.execSQL("INSERT INTO retard (version_id,path,mod_type,devices_to_patch,type,secure_id) VALUES(?,?,?,?,\"file\",?)",
                                    new Object[]{
                                            event.getVersionToPatch() + 1,
                                            event.getFilePath(),
                                            MODTYPES.get("creation"),
                                            strIds,
                                            secureId
                                    });
                        } catch (SQLException e) {
                            Log.e("DB_ERROR", "Error while inserting new retard: " + e.getMessage());
                        }
                        break;

                    case "[REMOVE]":
                        try {
                            db.execSQL("INSERT INTO retard (version_id,path,mod_type,devices_to_patch,type,secure_id) VALUES(?,?,?,?,\"file\",?)",
                                    new Object[]{
                                            event.getVersionToPatch() + 1,
                                            event.getFilePath(),
                                            MODTYPES.get("delete"),
                                            strIds,
                                            secureId
                                    });
                        } catch (SQLException e) {
                            Log.e("DB_ERROR", "Error while inserting new retard: " + e.getMessage());
                        }
                        break;

                    case "[MOVE]":
                        try {
                            db.execSQL("INSERT INTO retard (version_id,path,mod_type,devices_to_patch,type,secure_id) VALUES(?,?,?,?,?,?)",
                                    new Object[]{
                                            event.getVersionToPatch() + 1,
                                            event.getFilePath(),
                                            MODTYPES.get("move"),
                                            strIds,
                                            event.getFileType(),
                                            secureId
                                    });
                        } catch (SQLException e) {
                            Log.e("DB_ERROR", "Error while inserting new retard: " + e.getMessage());
                        }
                        break;
                }
            } else {
                switch (event.getFlag()) {
                    case "[CREATE]":
                        try {
                            db.execSQL("INSERT INTO retard (version_id,path,mod_type,devices_to_patch,type,secure_id) VALUES(?,?,?,?,\"folder\",?)",
                                    new Object[]{
                                            0,
                                            event.getFilePath(),
                                            MODTYPES.get("creation"),
                                            strIds,
                                            secureId
                                    });
                        } catch (SQLException e) {
                            Log.e("DB_ERROR", "Error while inserting new retard in AddFolderToRetard: " + e.getMessage());
                        }
                        break;

                    case "[MOVE]":
                        try {
                            db.execSQL("INSERT INTO retard (version_id,path,mod_type,devices_to_patch,type,secure_id) VALUES(?,?,?,?,?,?)",
                                    new Object[]{
                                            0,
                                            event.getFilePath(),
                                            MODTYPES.get("move"),
                                            strIds,
                                            event.getFileType(),
                                            secureId
                                    });
                        } catch (SQLException e) {
                            Log.e("DB_ERROR", "Error while inserting new retard: " + e.getMessage());
                        }
                        break;

                    case "[REMOVE]":
                        try {
                            db.execSQL("INSERT INTO retard (version_id,path,mod_type,devices_to_patch,type,secure_id) VALUES(?,?,?,?,\"folder\",?)",
                                    new Object[]{
                                            0,
                                            event.getFilePath(),
                                            MODTYPES.get("delete"),
                                            strIds,
                                            secureId
                                    });
                        } catch (SQLException e) {
                            Log.e("DB_ERROR", "Error while inserting new retard: " + e.getMessage());
                        }
                        break;
                }
            }
        }
    }

}
