package com.example.model;

import javafx.beans.property.SimpleStringProperty;

public class FileModel {
    private final SimpleStringProperty path;
    private final SimpleStringProperty tags;

    public FileModel(String path, String tags) {
        this.path = new SimpleStringProperty(path);
        this.tags = new SimpleStringProperty(tags);
    }

    public String getPath() {
        return path.get();
    }

    public String getTags() {
        return tags.get();
    }

    public void setTags(String tags) {
        this.tags.set(tags);
    }
}
