package com.qsync.qsync;

import static com.qsync.qsync.DeltaBinaire.buildDelta;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.google.gson.Gson;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.*;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.zip.GZIPInputStream;
import java.util.HashMap;
import java.nio.file.SimpleFileVisitor;
import android.database.sqlite.SQLiteDatabase;

public class AccesBdd {

    private static final String QSYNC_WRITEABLE_DIRECTORY = "path_to_your_directory"; // Specify your directory path here

    private static final String SQLITE_DB_PATH = "qsync.db";

    private String secureId;
    private SQLiteDatabase db;
    private Context context;

    public void AccesBdd(Context context){
        this.context = context;
    }

    public void initdb() {
        SQLiteOpenHelper dbHelper = new SQLiteOpenHelper(context,"qsync",null,1) {
            @Override
            public void onCreate(SQLiteDatabase db) {

            }

            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

            }
        };
        db = dbHelper.getWritableDatabase();

        createTables();
        if (!isMyDeviceIdGenerated()) {
            generateMyDeviceId();
        }
    }

    public void closedb() {
        if (db != null) {
            db.close();
        }

    }

    private void createTables() {
        db.rawQuery("CREATE TABLE IF NOT EXISTS retard(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "version_id INTEGER," +
                    "path TEXT," +
                    "mod_type TEXT," +
                    "devices_to_patch TEXT DEFAULT ''," +
                    "type TEXT," +
                    "secure_id TEXT)",null);
            // CREATE TABLE delta
        db.rawQuery("CREATE TABLE IF NOT EXISTS delta(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "path TEXT," +
                    "version_id INTEGER," +
                    "delta TEXT," +
                    "secure_id TEXT)",null);

            // CREATE TABLE filesystem
            db.rawQuery("CREATE TABLE IF NOT EXISTS filesystem(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "path TEXT," +
                    "version_id INTEGER," +
                    "type TEXT," +
                    "size INTEGER," +
                    "secure_id TEXT," +
                    "content BLOB)",null);

            // CREATE TABLE sync
            db.rawQuery("CREATE TABLE IF NOT EXISTS sync(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "secure_id TEXT," +
                    "linked_devices_id TEXT DEFAULT ''," +
                    "root TEXT)",null);

            // CREATE TABLE linked_devices
            db.rawQuery("CREATE TABLE IF NOT EXISTS linked_devices(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "device_id TEXT," +
                    "is_connected BOOLEAN," +
                    "receiving_update TEXT DEFAULT ''," +
                    "ip_addr TEXT)",null);

            // CREATE TABLE mesid
            db.rawQuery("CREATE TABLE IF NOT EXISTS mesid(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "device_id TEXT," +
                    "accepte_largage_aerien BOOLEAN DEFAULT TRUE)",null);

            // CREATE TABLE apps
            db.rawQuery("CREATE TABLE IF NOT EXISTS apps(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT," +
                    "path TEXT," +
                    "version_id INTEGER," +
                    "type TEXT," +
                    "secure_id TEXT," +
                    "uninstaller_path TEXT)",null);

            // Additional initialization
            if (! IsMyDeviceIdGenerated()) {
                GenerateMyDeviceId();
            }

    }

    public boolean checkFileExists(String path){
        String[] args = {path,secureId};
        Cursor cursor = db.rawQuery("SELECT id FROM filesystem WHERE path=? AND secure_id=?",args);
        boolean ret = cursor.moveToFirst();
        return ret;
    }

    public boolean wasFile(String path) {
        String[] args = {path, secureId};
        Cursor cursor = db.rawQuery("SELECT type FROM filesystem WHERE path=? AND secure_id=?",args);
        boolean ret = cursor.moveToNext() && cursor.getString(0).equals("file");

        return ret;
    }

    public boolean isFile(String path) {
        return new File(path).isFile();
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

    public void createFile(String relativePath, String absolutePath, String flag){


        try {
            InputStream inputStream = new FileInputStream(absolutePath);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream);



            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Files.copy(Paths.get(absolutePath), gzipOutputStream);
            }
            byte[] contentBytes = outputStream.toByteArray();
            String[] args = {
                    relativePath,
                    String.valueOf(new File(absolutePath).length()),
                    secureId,
                    Arrays.toString(contentBytes)
            };

            Cursor cursor = db.rawQuery("INSERT INTO filesystem (path, version_id, type, size, secure_id, content) VALUES (?, 0, 'file', ?, ?, ?)",args);
            cursor.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        // Handle adding file to retard
        if ("[ADD_TO_RETARD]".equals(flag)) {
            // Build delta
            DeltaBinaire.Delta delta = buildDelta(relativePath, absolutePath, 0, new byte[0]);

            // Get offline devices
            Globals.GenArray<String> offlineDevices = getSyncOfflineDevices();

            // Insert delta into delta table
            Gson gson = new Gson();
            String jsonData = gson.toJson(delta);
            String deltaInsertQuery = "INSERT INTO delta (path, version_id, delta, secure_id) VALUES (?, ?, ?, ?)";
            Cursor cursor = db.rawQuery(deltaInsertQuery,new String[]{
                    relativePath,
                    String.valueOf(getFileLastVersionId(relativePath) + 1),
                    jsonData,
                    secureId
            });
            cursor.close();




        // Insert into retard table

            HashMap<String, String> modtypes = Globals.modTypes();

            StringBuilder strIds = new StringBuilder();
            for (int i = 0; i < offlineDevices.size(); i++) {
                strIds.append(offlineDevices.get(i)).append(";");
            }
            strIds.deleteCharAt(strIds.length() - 1);

            String retardInsertQuery = "INSERT INTO retard (version_id, path, mod_type, devices_to_patch, type, secure_id) VALUES (?, ?, ?, ?, 'file', ?)";
            cursor = db.rawQuery(retardInsertQuery,new String[]{
                    String.valueOf(getFileLastVersionId(relativePath) + 1),
                    relativePath,
                    modtypes.get("creation"),
                    strIds.toString(),
                    secureId
            });

            cursor.close();

        }


        UpdateCachedFile(absolutePath);
    }

    public boolean isMyDeviceIdGenerated() {
        Cursor cursor = db.rawQuery("SELECT id FROM mesid",null);
        boolean ret = cursor.moveToFirst();

        cursor.close();

        return ret;
    }

    public void generateMyDeviceId() {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
        StringBuilder secureIdBuilder = new StringBuilder();
        for (int i = 0; i < 41; i++) {
            int index = (int) (Math.random() * alphabet.length());
            secureIdBuilder.append(alphabet.charAt(index));
        }
        this.secureId = secureIdBuilder.toString();
        Cursor cursor = db.rawQuery("INSERT INTO mesid(device_id) VALUES(?)",new String[]{secureId});
        cursor.close();
    }

    public String getMyDeviceId() {

        Cursor cursor = db.rawQuery("SELECT device_id FROM mesid",null)
        String ret = null;
        if (cursor.moveToFirst()) {
            ret = cursor.getString(0);
        }

        cursor.close();

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
            boolean isConnected = cursor.getInt(0) == 1;
            if (isConnected) {
                onlineDevices.add(deviceId);
            }
        }
        cursor.close();
        return onlineDevices;
    }

    public void setDeviceIP(String deviceId, String value) {
        Cursor cursor = db.rawQuery("UPDATE linked_devices SET ip_addr=? WHERE secure_id=? AND device_id=?",
                new String[]{
                        value,
                        secureId,
                        deviceId
                });
        cursor.close();
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
        Cursor cursor = db.rawQuery(
        "SELECT device_id, is_connected FROM linked_devices WHERE device_id IN (SELECT device_id FROM linked_devices)",
            null
        );
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

    public void updateFile(String path, DeltaBinaire.Delta delta) {
        Globals.GenArray<String> offlineDevices = getSyncOfflineDevices();
        if (!offlineDevices.isEmpty()) {
            long newVersionId = getFileLastVersionId(path) + 1;
            incrementFileVersion(path);
            try {
                Gson gson = new Gson();
                String jsonDelta = gson.toJson(delta);
                PreparedStatement statement = connection.prepareStatement("INSERT INTO delta (path, version_id, delta, secure_id) VALUES (?, ?, ?, ?)");
                statement.setString(1, path);
                statement.setLong(2, newVersionId);
                statement.setString(3, jsonDelta);
                statement.setString(4, secureId);
                db.rawQuery();
                String modType = "p"; // Assuming 'patch' for demonstration
                String strIds = offlineDevices.join(";"); // Implement 'join' method in GenArray class
                statement = connection.prepareStatement("INSERT INTO retard (version_id, path, mod_type, devices_to_patch, type, secure_id) VALUES (?, ?, ?, ?, 'file', ?)");
                statement.setLong(1, newVersionId);
                statement.setString(2, path);
                statement.setString(3, modType);
                statement.setString(4, strIds);
                statement.setString(5, secureId);
                db.rawQuery();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        updateCachedFile(path);
    }

    private void updateCachedFile(String path) {
        try {
            byte[] fileContent = readFromFile(path);
            byte[] compressedContent = compressData(fileContent);
            PreparedStatement statement = connection.prepareStatement("UPDATE filesystem SET content=? WHERE path=? AND secure_id=?");
            statement.setBytes(1, compressedContent);
            statement.setString(2, path);
            statement.setString(3, secureId);
            db.rawQuery();
        } catch (IOException | SQLException e) {
            e.printStackTrace();
        }
    }

    private byte[] readFromFile(String path) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(path)) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            return outputStream.toByteArray();
        }
    }

    private byte[] compressData(byte[] data) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            gzipOutputStream.write(data);
            gzipOutputStream.close();
            return outputStream.toByteArray();
        }
    }

    private db db;

    // Constructor
    public AccesBdd(db db) {
        this.db = db;
    }

    // GetFileContent retrieves the content of a file from the database.
    // Returned as byte array
    public byte[] getFileContent(String path) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT content FROM filesystem WHERE path=? AND secure_id=?")) {
            statement.setString(1, path);
            statement.setString(2, secureId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    byte[] compressedContent = resultSet.getBytes("content");
                    try (InputStream inputStream = new GZIPInputStream(new ByteArrayInputStream(compressedContent));
                         ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, length);
                        }
                        return outputStream.toByteArray();
                    }
                }
            }
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    // RmFile deletes a file from the database and adds it in delete mode to the retard table.
    public void rmFile(String path) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM filesystem WHERE path=? AND secure_id=?")) {
            statement.setString(1, path);
            statement.setString(2, secureId);
            db.rawQuery();
            // Now, purge all data involving this file from retard table
            try (PreparedStatement retardStatement = connection.prepareStatement("DELETE FROM retard WHERE path=? AND secure_id=?")) {
                retardStatement.setString(1, path);
                retardStatement.setString(2, secureId);
                retarddb.rawQuery();
                // And finally, add it in delete mode to the retard table
                HashMap<String, String> modtypes = Globals.modTypes();
                String modType = modtypes.get("delete");
                String linkedDevices = getSyncLinkedDevices();
                try (PreparedStatement insertRetardStatement = connection.prepareStatement("INSERT INTO retard (version_id,path,mod_type,devices_to_patch,type,secure_id) VALUES(?,?,?,?,?,?)")) {
                    insertRetardStatement.setInt(1, 0);
                    insertRetardStatement.setString(2, path);
                    insertRetardStatement.setString(3, modType);
                    insertRetardStatement.setString(4, linkedDevices);
                    insertRetardStatement.setString(5, "file");
                    insertRetardStatement.setString(6, secureId);
                    insertRetarddb.rawQuery();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // CreateFolder adds a folder to the database.
    public void createFolder(String path) {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO filesystem (path, version_id, type, size, secure_id) VALUES (?, 0, 'folder', 0, ?)")) {
            statement.setString(1, path);
            statement.setString(2, secureId);
            db.rawQuery();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // RmFolder deletes a folder from the database and adds it in delete mode to the retard table.
    public void rmFolder(String path) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM filesystem WHERE path LIKE ? AND secure_id=?")) {
            statement.setString(1, path + "%");
            statement.setString(2, secureId);
            db.rawQuery();
            // Now, purge all data involving this folder from retard table
            try (PreparedStatement retardStatement = connection.prepareStatement("DELETE FROM retard WHERE path LIKE ? AND secure_id=?")) {
                retardStatement.setString(1, path + "%");
                retardStatement.setString(2, secureId);
                retarddb.rawQuery();
                // Purge all data from delta table involving this folder
                try (PreparedStatement deltaStatement = connection.prepareStatement("DELETE FROM delta WHERE path LIKE ? AND secure_id=?")) {
                    deltaStatement.setString(1, path + "%");
                    deltaStatement.setString(2, secureId);
                    deltadb.rawQuery();
                    // And finally, add it in delete mode to the retard table
                    HashMap<String, String> modtypes = Globals.modTypes();
                    String modType = modtypes.get("delete");
                    String linkedDevices = getSyncLinkedDevices();
                    try (PreparedStatement insertRetardStatement = connection.prepareStatement("INSERT INTO retard (version_id,path,mod_type,devices_to_patch,type,secure_id) VALUES(?,?,?,?,?,?)")) {
                        insertRetardStatement.setInt(1, 0);
                        insertRetardStatement.setString(2, path);
                        insertRetardStatement.setString(3, modType);
                        insertRetardStatement.setString(4, linkedDevices);
                        insertRetardStatement.setString(5, "folder");
                        insertRetardStatement.setString(6, secureId);
                        insertRetarddb.rawQuery();
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Move updates the path of a file or folder in the database and adds a move event to the retard table.
    public void move(String path, String newPath, String fileType) {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE filesystem SET path=? WHERE path=? AND secure_id=?")) {
            statement.setString(1, newPath);
            statement.setString(2, path);
            statement.setString(3, secureId);
            db.rawQuery();
            // Add the move event to retard file
            HashMap<String, String> modtypes = Globals.modTypes();
            String modType = modtypes.get("move");
            String linkedDevices = getSyncLinkedDevices();
            try (PreparedStatement insertRetardStatement = connection.prepareStatement("INSERT INTO retard (version_id,path,mod_type,devices_to_patch,type,secure_id) VALUES(?,?,?,?,?,?)")) {
                insertRetardStatement.setInt(1, 0);
                insertRetardStatement.setString(2, path);
                insertRetardStatement.setString(3, modType);
                insertRetardStatement.setString(4, linkedDevices);
                insertRetardStatement.setString(5, fileType);
                insertRetardStatement.setString(6, secureId);
                insertRetarddb.rawQuery();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
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

    @RequiresApi(api = Build.VERSION_CODES.O)
    private class FileVisitor extends SimpleFileVisitor<Path> {
        private final String rootPath;

        public FileVisitor(String rootPath) {
            this.rootPath = rootPath;
        }

        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            String relativePath = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                relativePath = rootPath + File.separator + file.getFileName().toString();
            }
            // Add file to the database or perform other actions as needed
            createFile(relativePath, file.toAbsolutePath().toString(), "[ADD_TO_RETARD]");
            System.out.println("Registering: " + relativePath);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return FileVisitResult.CONTINUE;
            }else{
                return null;
            }        }

        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            String relativePath = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                relativePath = rootPath + File.separator + dir.getFileName().toString();
            }
            // Add folder to the database or perform other actions as needed
            createFolder(relativePath);
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

        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO sync (secure_id, root) VALUES(?,?)")) {
            statement.setString(1, secureId);
            statement.setString(2, rootPath);
            db.rawQuery();
            // ICI AJOUTER LA CARTOGRAPHIE DU SYSTEME DE FICHIERS
            addFilesToFileSystem(rootPath);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // CreateSyncFromOtherEnd creates a synchronization entry in the database with the given info.
    // Used to connect from an existing task from another device
    // Filesystem is not mapped by the function as a remote setup procedure is made around this call
    public void CreateSyncFromOtherEnd(String rootPath, String secureId) {
        try {
            this.secureId = secureId;
            // Add synchronization entry to the database
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO sync (secure_id, root) VALUES (?, ?)")) {
                statement.setString(1, secureId);
                statement.setString(2, rootPath);
                db.rawQuery();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void RmSync() {
        try {
            // Remove synchronization entry from the database
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM sync WHERE secure_id=?")) {
                statement.setString(1, secureId);
                db.rawQuery();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void LinkDevice(String deviceId, String ipAddress) {
        try {
            // Link a device to the synchronization entry
            try (PreparedStatement statement = connection.prepareStatement("UPDATE sync SET linked_devices_id=IFNULL(linked_devices_id, '') || ? WHERE secure_id=?")) {
                statement.setString(1, deviceId + ";");
                statement.setString(2, secureId);
                db.rawQuery();
            }
            if (!IsDeviceLinked(deviceId)) {
                // If the device is not registered as a target, register it
                try (PreparedStatement insertStatement = connection.prepareStatement("INSERT INTO linked_devices (device_id,is_connected,ip_addr) VALUES(?,TRUE,?)")) {
                    insertStatement.setString(1, deviceId);
                    insertStatement.setString(2, ipAddress);
                    insertdb.rawQuery();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void UnlinkDevice(String deviceId) {
        try {
            // Unlink a device from the synchronization entry
            try (PreparedStatement deleteStatement = connection.prepareStatement("DELETE FROM linked_devices WHERE device_id=?")) {
                deleteStatement.setString(1, deviceId);
                deletedb.rawQuery();
            }
            try (PreparedStatement updateStatement = connection.prepareStatement("UPDATE sync SET linked_devices_id=REPLACE(linked_devices_id,?, '')")) {
                updateStatement.setString(1, deviceId + ";");
                updatedb.rawQuery();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String GetRootSyncPath() {
        String rootPath = null;
        try {
            // Retrieve the root path associated with the synchronization entry
            try (PreparedStatement statement = connection.prepareStatement("SELECT root FROM sync WHERE secure_id=?")) {
                statement.setString(1, secureId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        rootPath = resultSet.getString("root");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return rootPath;
    }

    public void SetDevicedbState(String deviceId, boolean value, String... ipAddr) {
        try {
            if (ipAddr.length == 0) {
                try (PreparedStatement statement = connection.prepareStatement("UPDATE linked_devices SET is_connected=? WHERE device_id=?")) {
                    statement.setBoolean(1, value);
                    statement.setString(2, deviceId);
                    db.rawQuery();
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement("UPDATE linked_devices SET is_connected=?,ip_addr=? WHERE device_id=?")) {
                    statement.setBoolean(1, value);
                    statement.setString(2, ipAddr[0]);
                    statement.setString(3, deviceId);
                    db.rawQuery();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean GetDevicedbState(String deviceId) {
        boolean dbState = false;
        try {
            try (PreparedStatement statement = connection.prepareStatement("SELECT is_connected FROM linked_devices WHERE device_id=?")) {
                statement.setString(1, deviceId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        dbState = resultSet.getBoolean("is_connected");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return dbState;
    }

    public boolean IsThisFileSystemBeingPatched() {
        boolean isPatched = false;
        try {
            String idsStr;
            try (PreparedStatement statement = connection.prepareStatement("SELECT IFNULL(receiving_update, '')FROM linked_devices")) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        idsStr = resultSet.getString(1);
                        String[] idsArr = idsStr.split(";");
                        for (String id : idsArr) {
                            if (id.equals(secureId)) {
                                isPatched = true;
                                break;
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return isPatched;
    }

    public void SetFileSystemPatchLockState(String deviceId, boolean value) {
        try {
            if (value) {
                try (PreparedStatement statement = connection.prepareStatement("UPDATE linked_devices SET receiving_update=IFNULL(receiving_update, '') || ?")) {
                    statement.setString(1, secureId + ";");
                    db.rawQuery();
                }
            } else {
                String idsStr;
                try (PreparedStatement statement = connection.prepareStatement("SELECT receiving_update FROM linked_devices")) {
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            idsStr = resultSet.getString(1);
                            String[] idsArr = idsStr.split(";");
                            StringBuilder newIdsStr = new StringBuilder();
                            for (String id : idsArr) {
                                if (!id.equals(secureId)) {
                                    newIdsStr.append(id).append(";");
                                }
                            }
                            try (PreparedStatement updateStatement = connection.prepareStatement("UPDATE linked_devices SET receiving_update= ?")) {
                                updateStatement.setString(1, newIdsStr.toString());
                                updatedb.rawQuery();
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public long GetFileSizeFromBdd(String path) {
        long size = 0;
        try {
            try (PreparedStatement statement = connection.prepareStatement("SELECT size FROM filesystem WHERE path=? AND secure_id=?")) {
                statement.setString(1, path);
                statement.setString(2, secureId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        size = resultSet.getLong("size");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return size;
    }

    public String GetFileDelta(long version, String path) {
        String jsonDelta = null;
        try {
            try (PreparedStatement statement = connection.prepareStatement("SELECT delta FROM delta WHERE path=? AND version_id=?")) {
                statement.setString(1, path);
                statement.setLong(2, version);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        jsonDelta = resultSet.getString("delta");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return jsonDelta;
    }

    private boolean IsDeviceLinked(String deviceId) {
        boolean isLinked = false;
        try {
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) AS count FROM linked_devices WHERE device_id=?")) {
                statement.setString(1, deviceId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        int count = resultSet.getInt("count");
                        isLinked = count > 0;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return isLinked;
    }


    public void IncrementFileVersion(String path) {
        try {
            // Get the latest version of the file and increment it
            long newVersionId = GetFileLastVersionId(path) + 1;

            // Update version number in the database
            try (PreparedStatement statement = connection.prepareStatement("UPDATE filesystem SET version_id=? WHERE path=? AND secure_id=?")) {
                statement.setLong(1, newVersionId);
                statement.setString(2, path);
                statement.setString(3, secureId);
                db.rawQuery();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void UpdateCachedFile(String path) {
        try {
            // Read the current state of the given file and update it in the database
            try (FileInputStream fis = new FileInputStream(path);
                 ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 GZIPOutputStream gzipos = new GZIPOutputStream(bos)) {

                byte[] buffer = new byte[1024];
                int length;
                while ((length = fis.read(buffer)) != -1) {
                    gzipos.write(buffer, 0, length);
                }
                gzipos.close();
                byte[] compressedData = bos.toByteArray();

                try (PreparedStatement statement = connection.prepareStatement("UPDATE filesystem SET content=? WHERE path=? AND secure_id=?")) {
                    statement.setBytes(1, compressedData);
                    statement.setString(2, path);
                    statement.setString(3, secureId);
                    db.rawQuery();
                }
            }
        } catch (IOException | SQLException e) {
            e.printStackTrace();
        }
    }

    public Map<String, Globals.SyncInfos> ListSyncAllTasks() {
        // Used to get all sync task secure_id and root path listed
        // Returns a map containing the secure_id as key and SyncInfos as value
        Map<String, Globals.SyncInfos> list = new HashMap<>();
        try {
            try (PreparedStatement statement = connection.prepareStatement("SELECT secure_id,root FROM sync");
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Globals.SyncInfos info = new Globals.SyncInfos("","");
                    info.setSecureId(resultSet.getString("secure_id"));
                    info.setPath(resultSet.getString("root"));
                    list.put(info.getSecureId(), info);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public Map<String, Globals.GenArray<Globals.QEvent>> BuildEventQueueFromRetard(String deviceId) {
        // As the device can be late on many tasks, we must create a map with all
        // the different deltas on all different tasks it's late on


        // FILES
        Gson gson = new Gson();
        Map<String, Globals.GenArray<Globals.QEvent>> queue = new HashMap<>();
        try {
            try (PreparedStatement statement = connection.prepareStatement("SELECT r.secure_id,d.delta,r.mod_type,r.path,r.type FROM retard AS r JOIN delta AS d ON r.path=d.path AND r.version_id=d.version_id AND r.secure_id=d.secure_id WHERE r.devices_to_patch LIKE ?")) {
                statement.setString(1, "%" + deviceId + "%");
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        // build delta
                        Globals.QEvent event = new Globals.QEvent("","",null,"","","");
                        event.setFlag(Globals.modTypesReverse().get(resultSet.getString("mod_type")));
                        event.setFileType(resultSet.getString("type"));
                        event.setDelta(gson.fromJson(resultSet.getString("delta"),DeltaBinaire.Delta.class)));
                        event.setFilePath(resultSet.getString("path"));
                        event.setSecureId(secureId);

                        if (!queue.containsKey(event.getSecureId())) {
                            queue.put(event.getSecureId(), new Globals.GenArray<>());
                        }
                        Globals.GenArray<Globals.QEvent> events = queue.get(event.getSecureId());
                        assert events != null;
                        events.add(event);
                        queue.put(event.getSecureId(), events);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }


        // FOLDERS

        try {
            try (PreparedStatement statement = connection.prepareStatement("SELECT r.secure_id,r.mod_type,r.path,r.type FROM retard AS r WHERE r.devices_to_patch LIKE ? AND r.type='folder'")) {
                statement.setString(1, "%" + deviceId + "%");
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        // build delta
                        Globals.QEvent event = new Globals.QEvent("","",null,"","","");
                        event.setFlag(Globals.modTypesReverse().get(resultSet.getString("mod_type")));
                        event.setFileType(resultSet.getString("type"));
                        event.setFilePath(resultSet.getString("path"));
                        event.setSecureId(secureId);

                        if (!queue.containsKey(event.getSecureId())) {
                            queue.put(event.getSecureId(), new Globals.GenArray<>());
                        }
                        Globals.GenArray<Globals.QEvent> events = queue.get(event.getSecureId());
                        assert events != null;
                        events.add(event);
                        queue.put(event.getSecureId(), events);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return queue;
    }



    public void removeDeviceFromRetard(String deviceId) {
        Cursor cursor = connection.rawQuery("SELECT devices_to_patch FROM retard WHERE devices_to_patch LIKE ?", new String[]{"%" + deviceId + "%"});
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
                connection.execSQL("UPDATE retard SET devices_to_patch= ? WHERE devices_to_patch LIKE ?", new String[]{newIds.toString(), "%" + deviceId + "%"});
            } else {
                connection.execSQL("DELETE FROM retard WHERE devices_to_patch LIKE ?", new String[]{"%" + deviceId + "%"});
            }
        }
        cursor.close();
    }

    public boolean needsUpdate(String deviceId) {
        Cursor cursor = connection.prepareStatement("SELECT devices_to_patch FROM retard WHERE devices_to_patch LIKE ?", new String[]{"%" + deviceId + "%"});
        boolean result = cursor.getCount() > 0;
        cursor.close();
        return result;
    }

    public void addToutEnUn(Globals.ToutEnUnConfig data) {
        connection.execSQL("INSERT INTO apps (name,path,version_id,type,secure_id,uninstaller_path) VALUES(?,?,?,?,?,?)",
                new Object[]{data.getAppName(), data.getAppLauncherPath(), 1, "toutenun", secureId, data.getAppUninstallerPath()});
    }

    public void addGrapin(Globals.GrapinConfig data) {
        connection.execSQL("INSERT INTO apps (name,path,version_id,type,uninstaller_path) VALUES(?,?,?,?,?)",
                new Object[]{data.getAppName(), "[GRAPIN]", 1, "grapin", secureId, "[GRAPIN]"});
    }

    public Globals.GenArray<Globals.MinGenConfig> listInstalledApps() {
        Globals.GenArray<Globals.MinGenConfig> configs = new Globals.GenArray<>();
        Cursor cursor = connection.rawQuery("SELECT name,id,path,type FROM apps", null);
        while (cursor.moveToNext()) {
            Globals.MinGenConfig config = new Globals.MinGenConfig();
            config.setAppName(cursor.getString(0));
            config.setId(cursor.getInt(1));
            config.setPath(cursor.getString(2));
            config.setType(cursor.getString(3));
            configs.add(config);
        }
        cursor.close();
        return configs;
    }

    public MinGenConfig getAppConfig(int appId) {
        MinGenConfig config = new MinGenConfig();
        Cursor cursor = db_handler.rawQuery("SELECT name,id,path,type,uninstaller_path FROM apps WHERE id=?", new String[]{String.valueOf(appId)});
        if (cursor.moveToFirst()) {
            config.setAppName(cursor.getString(0));
            config.setId(cursor.getInt(1));
            config.setPath(cursor.getString(2));
            config.setType(cursor.getString(3));
            config.setUninstallerPath(cursor.getString(4));
        }
        cursor.close();
        return config;
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


}
