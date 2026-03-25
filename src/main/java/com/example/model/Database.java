package com.example.model;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.io.IOException;

public class Database {
    private Connection connection;

    public Database() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:file_tags.db");
        initializeDatabase();
    }

    private void initializeDatabase() throws SQLException {
        String sqlFiles = "CREATE TABLE IF NOT EXISTS files (id INTEGER PRIMARY KEY AUTOINCREMENT, path TEXT NOT NULL UNIQUE);";
        String sqlTags = "CREATE TABLE IF NOT EXISTS tags (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE);";
        String sqlFileTags = "CREATE TABLE IF NOT EXISTS file_tags (file_id INTEGER, tag_id INTEGER, FOREIGN KEY(file_id) REFERENCES files(id), FOREIGN KEY(tag_id) REFERENCES tags(id), PRIMARY KEY(file_id, tag_id));";
        String sqlSettings = "CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT);";

        try (PreparedStatement stmt = connection.prepareStatement(sqlFiles)) { stmt.execute(); }
        try (PreparedStatement stmt = connection.prepareStatement(sqlTags)) { stmt.execute(); }
        try (PreparedStatement stmt = connection.prepareStatement(sqlFileTags)) { stmt.execute(); }
        try (PreparedStatement stmt = connection.prepareStatement(sqlSettings)) { stmt.execute(); }
    }

    public void addFile(String path) throws SQLException {
        if (getFileId(path) != -1) return;
        String sql = "INSERT INTO files (path) VALUES (?);";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, path);
            stmt.executeUpdate();
        }
    }

    public void addTag(String name) throws SQLException {
        String sql = "INSERT INTO tags (name) VALUES (?);";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.executeUpdate();
        }
    }

    public void addFileTag(int fileId, int tagId) throws SQLException {
        String sql = "INSERT INTO file_tags (file_id, tag_id) VALUES (?, ?);";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, fileId);
            stmt.setInt(2, tagId);
            stmt.executeUpdate();
        }
    }

    public ResultSet searchFilesByTag(String tagName) throws SQLException {
        String sql = "SELECT f.path FROM files f JOIN file_tags ft ON f.id = ft.file_id JOIN tags t ON ft.tag_id = t.id WHERE t.name = ?;";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setString(1, tagName);
        return stmt.executeQuery();
    }

    public ResultSet searchFilesByNameOrTag(String query) throws SQLException {
        String sql = "SELECT f.path FROM files f LEFT JOIN file_tags ft ON f.id = ft.file_id LEFT JOIN tags t ON ft.tag_id = t.id WHERE f.path LIKE ? OR t.name LIKE ?;";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setString(1, "%" + query + "%");
        stmt.setString(2, "%" + query + "%");
        return stmt.executeQuery();
    }

    public void addTagToFile(String filePath, String tagName) throws SQLException {
        int fileId = getFileId(filePath);
        int tagId = getTagId(tagName);
        if (fileId != -1 && tagId != -1) {
            addFileTag(fileId, tagId);
        }
    }

    public void removeTagFromFile(String filePath, String tagName) throws SQLException {
        int fileId = getFileId(filePath);
        int tagId = getTagId(tagName);
        if (fileId != -1 && tagId != -1) {
            String sql = "DELETE FROM file_tags WHERE file_id = ? AND tag_id = ?;";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setInt(1, fileId);
                stmt.setInt(2, tagId);
                stmt.executeUpdate();
            }
        }
    }

    private int getFileId(String filePath) throws SQLException {
        String sql = "SELECT id FROM files WHERE path = ?;";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, filePath);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("id") : -1;
        }
    }

    private int getTagId(String tagName) throws SQLException {
        String sql = "SELECT id FROM tags WHERE name = ?;";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, tagName);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("id") : -1;
        }
    }

    // Новые методы для запоминания директории и времени последнего сканирования
    public String getLastRoot() throws SQLException {
        return getSetting("last_root");
    }

    public void setLastRoot(String root) throws SQLException {
        setSetting("last_root", root);
    }

    public long getLastScanTime() throws SQLException {
        String val = getSetting("last_scan_time");
        return val != null && !val.isEmpty() ? Long.parseLong(val) : 0L;
    }

    public void setLastScanTime(long time) throws SQLException {
        setSetting("last_scan_time", String.valueOf(time));
    }

    private String getSetting(String key) throws SQLException {
        String sql = "SELECT value FROM settings WHERE key = ?;";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, key);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("value") : null;
        }
    }

    private void setSetting(String key, String value) throws SQLException {
        String sql = "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?);";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.executeUpdate();
        }
    }

    // Инкрементальная индексация (только новые файлы/папки по времени создания)
    public void indexDirectory(String rootPath, long lastScanTime) throws SQLException {
        try {
            Path start = Paths.get(rootPath);
            Files.walk(start).forEach(path -> {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                    long creationTime = attrs.creationTime().toMillis();
                    if (lastScanTime == 0 || creationTime > lastScanTime) {
                        String filePath = path.toAbsolutePath().toString();
                        addFile(filePath);
                    }
                } catch (IOException e) {
                    // Пропускаем файлы/папки без доступа
                }
            });
        } catch (IOException e) {
            // Пропускаем ошибки обхода
        }
    }

    // Получение тегов для конкретного файла
    public String getTagsForFile(String path) throws SQLException {
        String sql = "SELECT GROUP_CONCAT(t.name, ', ') as tags " +
                     "FROM files f LEFT JOIN file_tags ft ON f.id = ft.file_id " +
                     "LEFT JOIN tags t ON ft.tag_id = t.id " +
                     "WHERE f.path = ? GROUP BY f.id;";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, path);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getString("tags") != null ? rs.getString("tags") : "";
        }
    }

    // Полная замена тегов у файла (удаляет старые, добавляет новые)
    public void setTagsForFile(String path, List<String> tagNames) throws SQLException {
        int fileId = getFileId(path);
        if (fileId == -1) return;

        // Удаляем старые связи
        String deleteSql = "DELETE FROM file_tags WHERE file_id = ?;";
        try (PreparedStatement stmt = connection.prepareStatement(deleteSql)) {
            stmt.setInt(1, fileId);
            stmt.executeUpdate();
        }

        // Добавляем новые теги
        for (String name : tagNames) {
            if (name.trim().isEmpty()) continue;
            int tagId = getTagId(name);
            if (tagId == -1) {
                addTag(name);
                tagId = getTagId(name);
            }
            if (tagId != -1) {
                addFileTag(fileId, tagId);
            }
        }
    }

    // Получить все файлы с тегами для таблицы
    public List<FileModel> getAllFilesWithTags() throws SQLException {
        List<FileModel> models = new ArrayList<>();
        String sql = "SELECT f.path, GROUP_CONCAT(t.name, ', ') as tags " +
                     "FROM files f LEFT JOIN file_tags ft ON f.id = ft.file_id " +
                     "LEFT JOIN tags t ON ft.tag_id = t.id " +
                     "GROUP BY f.path ORDER BY f.path;";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String tags = rs.getString("tags");
                models.add(new FileModel(rs.getString("path"), tags != null ? tags : ""));
            }
        }
        return models;
    }

    // Поиск по имени или тегу с возвратом тегов
    public List<FileModel> searchFiles(String query) throws SQLException {
        List<FileModel> models = new ArrayList<>();
        String sql = "SELECT f.path, GROUP_CONCAT(t.name, ', ') as tags " +
                     "FROM files f LEFT JOIN file_tags ft ON f.id = ft.file_id " +
                     "LEFT JOIN tags t ON ft.tag_id = t.id " +
                     "WHERE f.path LIKE ? OR t.name LIKE ? " +
                     "GROUP BY f.path ORDER BY f.path;";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            String like = "%" + query + "%";
            stmt.setString(1, like);
            stmt.setString(2, like);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String tags = rs.getString("tags");
                models.add(new FileModel(rs.getString("path"), tags != null ? tags : ""));
            }
        }
        return models;
    }
}
