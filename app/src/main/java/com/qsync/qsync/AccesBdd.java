package com.qsync.qsync;

import static com.qsync.qsync.DeltaBinaire.buildDelta;

import android.os.Build;

import com.google.gson.Gson;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.zip.GZIPOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.zip.GZIPInputStream;
import java.util.HashMap;


public class AccesBdd {

    private static final String QSYNC_WRITEABLE_DIRECTORY = "path_to_your_directory"; // Specify your directory path here

    private static final String SQLITE_DB_PATH = "qsync.db";

    private String secureId;
    private Connection connection;

    public void initConnection() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + SQLITE_DB_PATH);
            createTables();
            if (!isMyDeviceIdGenerated()) {
                generateMyDeviceId();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void closeConnection() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void createTables() {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS retard(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "version_id INTEGER," +
                    "path TEXT," +
                    "mod_type TEXT," +
                    "devices_to_patch TEXT DEFAULT ''," +
                    "type TEXT," +
                    "secure_id TEXT)");
            // CREATE TABLE delta
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS delta(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "path TEXT," +
                    "version_id INTEGER," +
                    "delta TEXT," +
                    "secure_id TEXT)");

            // CREATE TABLE filesystem
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS filesystem(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "path TEXT," +
                    "version_id INTEGER," +
                    "type TEXT," +
                    "size INTEGER," +
                    "secure_id TEXT," +
                    "content BLOB)");

            // CREATE TABLE sync
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS sync(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "secure_id TEXT," +
                    "linked_devices_id TEXT DEFAULT ''," +
                    "root TEXT)");

            // CREATE TABLE linked_devices
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS linked_devices(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "device_id TEXT," +
                    "is_connected BOOLEAN," +
                    "receiving_update TEXT DEFAULT ''," +
                    "ip_addr TEXT)");

            // CREATE TABLE mesid
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS mesid(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "device_id TEXT," +
                    "accepte_largage_aerien BOOLEAN DEFAULT TRUE)");

            // CREATE TABLE apps
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS apps(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT," +
                    "path TEXT," +
                    "version_id INTEGER," +
                    "type TEXT," +
                    "secure_id TEXT," +
                    "uninstaller_path TEXT)");

            // Additional initialization
            if (! IsMyDeviceIdGenerated()) {
                GenerateMyDeviceId();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean checkFileExists(String path) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM filesystem WHERE path=? AND secure_id=?")) {
            statement.setString(1, path);
            statement.setString(2, secureId);
            ResultSet resultSet = statement.executeQuery();
            return resultSet.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean wasFile(String path) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT type FROM filesystem WHERE path=? AND secure_id=?")) {
            statement.setString(1, path);
            statement.setString(2, secureId);
            ResultSet resultSet = statement.executeQuery();
            return resultSet.next() && resultSet.getString("type").equals("file");
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean isFile(String path) {
        return new File(path).isFile();
    }

    public void getSecureIdFromRootPath(String rootPath) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT secure_id FROM sync WHERE root=?")) {
            statement.setString(1, rootPath);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                secureId = resultSet.getString("secure_id");
            } else {
                throw new IllegalStateException("No secure ID found for the root path: " + rootPath);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void createFile(String relativePath, String absolutePath, String flag) {
        try (InputStream inputStream = new FileInputStream(absolutePath);
             OutputStream outputStream = new ByteArrayOutputStream();
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream);
             PreparedStatement statement = connection.prepareStatement("INSERT INTO filesystem (path, version_id, type, size, secure_id, content) VALUES (?, 0, 'file', ?, ?, ?)")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Files.copy(Paths.get(absolutePath), gzipOutputStream);
            }
            byte[] contentBytes = ((ByteArrayOutputStream) outputStream).toByteArray();
            statement.setString(1, relativePath);
            statement.setLong(2, new File(absolutePath).length());
            statement.setString(3, secureId);
            statement.setBytes(4, contentBytes);
            statement.executeUpdate();
            if (flag.equals("[ADD_TO_RETARD]")) {
                DeltaBinaire.Delta delta = buildDelta(relativePath, absolutePath, 0, new byte[0]);
                // Handle delta and other operations
            }
        } catch (IOException | SQLException e) {
            e.printStackTrace();
        }


        // Handle adding file to retard
        if ("[ADD_TO_RETARD]".equals(flag)) {
            // Build delta
            DeltaBinaire.Delta delta = buildDelta(relativePath, absolutePath, 0, new byte[0]);

            // Get offline devices
            Globals.GenArray<String> offlineDevices = getSyncOfflineDevices();

            // Insert delta into delta table
            try {
                Gson gson = new Gson();
                String jsonData = gson.toJson(delta);
                String deltaInsertQuery = "INSERT INTO delta (path, version_id, delta, secure_id) VALUES (?, ?, ?, ?)";
                try (PreparedStatement deltaStatement = connection.prepareStatement(deltaInsertQuery)) {
                    deltaStatement.setString(1, relativePath);
                    deltaStatement.setInt(2, getFileLastVersionId(relativePath) + 1);
                    deltaStatement.setString(3, jsonData);
                    deltaStatement.setString(4, secureId);
                    deltaStatement.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return;
            }

            // Insert into retard table
            try {

                HashMap<String, String> modtypes = Globals.modTypes();

                StringBuilder strIds = new StringBuilder();
                for (int i = 0; i < offlineDevices.size(); i++) {
                    strIds.append(offlineDevices.get(i)).append(";");
                }
                strIds.deleteCharAt(strIds.length() - 1);

                String retardInsertQuery = "INSERT INTO retard (version_id, path, mod_type, devices_to_patch, type, secure_id) VALUES (?, ?, ?, ?, 'file', ?)";
                try (PreparedStatement retardStatement = connection.prepareStatement(retardInsertQuery)) {
                    retardStatement.setInt(1, (int) (getFileLastVersionId(relativePath) + 1));
                    retardStatement.setString(2, relativePath);
                    retardStatement.setString(3, modtypes.get("creation"));
                    retardStatement.setString(4, strIds.toString());
                    retardStatement.setString(5, secureId);
                    retardStatement.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }


        UpdateCachedFile(absolutePath);
    }

    public boolean isMyDeviceIdGenerated() {
        try (ResultSet resultSet = connection.createStatement().executeQuery("SELECT id FROM mesid")) {
            return resultSet.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void generateMyDeviceId() {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
        StringBuilder secureIdBuilder = new StringBuilder();
        for (int i = 0; i < 41; i++) {
            int index = (int) (Math.random() * alphabet.length());
            secureIdBuilder.append(alphabet.charAt(index));
        }
        String secureId = secureIdBuilder.toString();
        try {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO mesid(device_id) VALUES(?)");
            statement.setString(1, secureId);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getMyDeviceId() {
        try (ResultSet resultSet = connection.createStatement().executeQuery("SELECT device_id FROM mesid")) {
            if (resultSet.next()) {
                return resultSet.getString("device_id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Globals.GenArray<String> getOfflineDevices() {
        Globals.GenArray<String> offlineDevices = new Globals.GenArray<>();
        try (ResultSet resultSet = connection.createStatement().executeQuery("SELECT device_id, is_connected FROM linked_devices")) {
            while (resultSet.next()) {
                String deviceId = resultSet.getString("device_id");
                boolean isConnected = resultSet.getBoolean("is_connected");
                if (!isConnected) {
                    offlineDevices.add(deviceId);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return offlineDevices;
    }

    public Globals.GenArray<String> getOnlineDevices() {
        Globals.GenArray<String> onlineDevices = new Globals.GenArray<String>();
        try (ResultSet resultSet = connection.createStatement().executeQuery("SELECT device_id, is_connected FROM linked_devices")) {
            while (resultSet.next()) {
                String deviceId = resultSet.getString("device_id");
                boolean isConnected = resultSet.getBoolean("is_connected");
                if (isConnected) {
                    onlineDevices.add(deviceId);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return onlineDevices;
    }

    public void setDeviceIP(String deviceId, String value) {
        try {
            PreparedStatement statement = connection.prepareStatement("UPDATE linked_devices SET ip_addr=? WHERE secure_id=? AND device_id=?");
            statement.setString(1, value);
            statement.setString(2, secureId);
            statement.setString(3, deviceId);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getDeviceIP(String deviceId) {
        try {
            PreparedStatement statement = connection.prepareStatement("SELECT ip_addr FROM linked_devices WHERE device_id=?");
            statement.setString(1, deviceId);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString("ip_addr");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public long getFileLastVersionId(String path) {
        try {
            PreparedStatement statement = connection.prepareStatement("SELECT version_id FROM filesystem WHERE path=? AND secure_id=?");
            statement.setString(1, path);
            statement.setString(2, secureId);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getLong("version_id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public Globals.GenArray<String> getSyncOfflineDevices() {
        Globals.GenArray<String> offlineDevices = new Globals.GenArray<>();
        try {
            PreparedStatement statement = connection.prepareStatement("SELECT device_id, is_connected FROM linked_devices WHERE device_id IN (SELECT device_id FROM linked_devices)");
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                String deviceId = resultSet.getString("device_id");
                boolean isConnected = resultSet.getBoolean("is_connected");
                if (!isConnected) {
                    offlineDevices.add(deviceId);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
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
                statement.executeUpdate();
                String modType = "p"; // Assuming 'patch' for demonstration
                String strIds = offlineDevices.join(";"); // Implement 'join' method in GenArray class
                statement = connection.prepareStatement("INSERT INTO retard (version_id, path, mod_type, devices_to_patch, type, secure_id) VALUES (?, ?, ?, ?, 'file', ?)");
                statement.setLong(1, newVersionId);
                statement.setString(2, path);
                statement.setString(3, modType);
                statement.setString(4, strIds);
                statement.setString(5, secureId);
                statement.executeUpdate();
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
            statement.executeUpdate();
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

    private Connection connection;

    // Constructor
    public AccesBdd(Connection connection) {
        this.connection = connection;
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
            statement.executeUpdate();
            // Now, purge all data involving this file from retard table
            try (PreparedStatement retardStatement = connection.prepareStatement("DELETE FROM retard WHERE path=? AND secure_id=?")) {
                retardStatement.setString(1, path);
                retardStatement.setString(2, secureId);
                retardStatement.executeUpdate();
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
                    insertRetardStatement.executeUpdate();
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
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // RmFolder deletes a folder from the database and adds it in delete mode to the retard table.
    public void rmFolder(String path) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM filesystem WHERE path LIKE ? AND secure_id=?")) {
            statement.setString(1, path + "%");
            statement.setString(2, secureId);
            statement.executeUpdate();
            // Now, purge all data involving this folder from retard table
            try (PreparedStatement retardStatement = connection.prepareStatement("DELETE FROM retard WHERE path LIKE ? AND secure_id=?")) {
                retardStatement.setString(1, path + "%");
                retardStatement.setString(2, secureId);
                retardStatement.executeUpdate();
                // Purge all data from delta table involving this folder
                try (PreparedStatement deltaStatement = connection.prepareStatement("DELETE FROM delta WHERE path LIKE ? AND secure_id=?")) {
                    deltaStatement.setString(1, path + "%");
                    deltaStatement.setString(2, secureId);
                    deltaStatement.executeUpdate();
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
                        insertRetardStatement.executeUpdate();
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
            statement.executeUpdate();
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
                insertRetardStatement.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
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
            statement.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // CreateSyncFromOtherEnd creates a synchronization entry in the database with the given info.
    // Used to connect from an existing task from another device
    // Filesystem is not mapped by the function as a remote setup procedure is made around this call
    public void createSyncFromOtherEnd(String rootPath, String secureId) {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO sync (secure_id, root) VALUES(?,?)")) {
            statement.setString(1, secureId);
            statement.setString(2, rootPath);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }





}
