/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.remotedbconnection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Administrator
 */
public class SqlToCpanelSync {
    // cPanel PHP API details
    private static final String CPANEL_API = "https://yourdomain/PHP_API_File.php";
    private static final String API_KEY = "createstrongkey";

    public static String syncUsers() throws IOException {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:sqlserver://yourlocalserver;Database=databasename;user=databaseusername;password=databasepassword;encrypt=true;trustServerCertificate=true;")) {

            System.out.println("Fetching SQL Server data...");
            JSONArray sourceData = fetchSqlServerData(conn, "table_user1"); // local networked SQL Server
            System.out.println("Fetching cPanel data...");
            JSONArray targetData = fetchCpanelData("table_users");          // remote server

            int counter = sourceData.length();
            int counter1 = targetData.length();

            //if (counter > counter1) {
                // SQL has more rows → add missing to cPanel
                for (int i = 0; i < sourceData.length(); i++) {
                    JSONObject rowSource = sourceData.getJSONObject(i);
                    int id = rowSource.getInt("id");

                    JSONObject rowTarget = findById(targetData, id);
                    if (rowTarget == null) {
                        System.out.println("Inserting ID " + id + " into remote server");
                        sendPost("add_user", rowSource);
                    }
                }

            //} else if (counter < counter1) {
                // cPanel has more rows → add missing to SQL Server
                for (int i = 0; i < targetData.length(); i++) {
                    JSONObject rowTarget = targetData.getJSONObject(i);
                    int id = rowTarget.getInt("id");

                    JSONObject rowSource = findById(sourceData, id);
                    if (rowSource == null) {
                        System.out.println("Inserting ID " + id + " into SQL Server");
                        insertIntoSqlServer(conn, "user1", rowTarget);
                    }
                }

            //} else { 
                // Both have same number of rows → update based on last_updated
                for (int i = 0; i < sourceData.length(); i++) {
                    JSONObject rowSource = sourceData.getJSONObject(i);
                    int id = rowSource.getInt("id");

                    JSONObject rowTarget = findById(targetData, id);
                    if (rowTarget != null) {
                        String sourceDate = rowSource.optString("last_updated", "");
                        String targetDate = rowTarget.optString("last_updated", "");

                        if (isNewer(sourceDate, targetDate)) {
                            System.out.println("Updating ID " + id + " in cPanel (SQL is newer)");
                            sendPost("update_user", rowSource);
                        } else if (isNewer(targetDate, sourceDate)) {
                            System.out.println("Updating ID " + id + " in SQL Server (remote server is newer)");
                            updateSqlServer(conn, "user1", rowTarget);
                        }
                    }
                }
            //}

            System.out.println("Synchronization complete!");

        } catch (SQLException e) {
            e.printStackTrace();
        } catch (IOException ex) {
            System.getLogger(SqlToCpanelSync.class.getName())
                  .log(System.Logger.Level.ERROR, (String) null, ex);
        }

        return "Sync complete!";
    }


    // Fetch data from MS SQL Server
    private static JSONArray fetchSqlServerData(Connection conn, String table) throws SQLException {
        JSONArray array = new JSONArray();
        String query = "SELECT * FROM " + table;
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            while (rs.next()) {
                JSONObject row = new JSONObject();
                for (int i = 1; i <= colCount; i++) {
                    row.put(meta.getColumnName(i), rs.getString(i));
                }
                array.put(row);
            }
        }
        return array;
    }

    // Fetch data from cPanel API
    private static JSONArray fetchCpanelData(String table) throws IOException {
        String urlString = CPANEL_API + "?action=get_data&table=" + table + "&api_key=" + API_KEY;
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            content.append(line);
        }
        in.close();
        conn.disconnect();

        return new JSONArray(content.toString());
    }

    public static JSONObject findById(JSONArray array, int id) {
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            if (obj.getInt("id") == id) {
                return obj;
            }
        }
        return null;
    }

    // Send POST request to cPanel API to add or update
    private static void sendPost(String action, JSONObject row) throws IOException {
        URL url = new URL(CPANEL_API);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        StringBuilder postData = new StringBuilder();
        postData.append("action=").append(action);
        postData.append("&api_key=").append(API_KEY);

        for (String key : row.keySet()) {
            postData.append("&").append(key).append("=").append(row.getString(key));
        }

        try (OutputStream os = conn.getOutputStream()) {
            os.write(postData.toString().getBytes());
            os.flush();
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            response.append(line);
        }
        in.close();
        conn.disconnect();

        System.out.println("Server response: " + response);
    }
    // Insert into SQL Server
    private static void insertIntoSqlServer(Connection conn, String table, JSONObject row) throws SQLException {
        String sql = "INSERT INTO " + table + " (id, username, surname, email, last_updated) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, row.getInt("id"));
            ps.setString(2, row.getString("username"));
            ps.setString(3, row.getString("surname"));
            ps.setString(4, row.getString("email"));
            ps.setTimestamp(5, new java.sql.Timestamp(System.currentTimeMillis())); // current timestamp
            ps.executeUpdate();
        }
    }

    // Update row in SQL Server
    private static void updateSqlServer(Connection conn, String table, JSONObject row) throws SQLException {
        String sql = "UPDATE " + table + " SET username=?, surname=?, email=?, last_updated=? WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, row.getString("username"));
            ps.setString(2, row.getString("surname"));
            ps.setString(3, row.getString("email"));
            ps.setTimestamp(4, new java.sql.Timestamp(System.currentTimeMillis())); // current timestamp
            ps.setInt(5, row.getInt("id"));
            ps.executeUpdate();
        }
    }
    
    private static boolean isNewer(String date1, String date2) {
        if (date1 == null || date1.isEmpty()) return false;
        if (date2 == null || date2.isEmpty()) return true;

        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            java.util.Date d1 = sdf.parse(date1);
            java.util.Date d2 = sdf.parse(date2);
            return d1.after(d2); // true if date1 is newer than date2
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}