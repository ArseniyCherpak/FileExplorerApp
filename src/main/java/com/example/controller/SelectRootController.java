package com.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;

import java.io.File;

public class SelectRootController {

    @FXML
    private TreeView<String> rootTree;

    private String selectedPath;

    @FXML
    public void initialize() {
        populateRootTree();
    }

    private void populateRootTree() {
        TreeItem<String> rootItem = new TreeItem<>("Компьютер");
        rootItem.setExpanded(true);

        File[] roots = File.listRoots();
        for (File root : roots) {
            TreeItem<String> diskItem = new TreeItem<>(root.getAbsolutePath());
            rootItem.getChildren().add(diskItem);
            addChildren(diskItem, root);
        }

        rootTree.setRoot(rootItem);
    }

    private void addChildren(TreeItem<String> parentItem, File parentFile) {
        File[] children = parentFile.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    TreeItem<String> childItem = new TreeItem<>(child.getName());
                    parentItem.getChildren().add(childItem);
                    addChildren(childItem, child);
                }
            }
        }
    }

    @FXML
    private void handleContinue() {
        TreeItem<String> selectedItem = rootTree.getSelectionModel().getSelectedItem();
        if (selectedItem != null && !selectedItem.getValue().equals("Компьютер")) {
            selectedPath = getFullPath(selectedItem);
        }
        ((Stage) rootTree.getScene().getWindow()).close();
    }

    private String getFullPath(TreeItem<String> item) {
        if (item == null) return null;
        StringBuilder path = new StringBuilder(item.getValue());
        TreeItem<String> parent = item.getParent();
        while (parent != null && !parent.getValue().equals("Компьютер")) {
            path.insert(0, parent.getValue() + File.separator);
            parent = parent.getParent();
        }
        return path.toString();
    }

    public String getSelectedPath() {
        return selectedPath;
    }
}
