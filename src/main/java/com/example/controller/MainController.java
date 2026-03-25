package com.example.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.TreeItem;
import com.example.model.Database;
import com.example.model.FileModel;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class MainController {

    @FXML private TextField searchField;
    @FXML private Label addressLabel;
    @FXML private TreeView<String> navigationTree;
    @FXML private TableView<FileModel> fileTable;
    @FXML private TableColumn<FileModel, String> nameColumn;
    @FXML private TableColumn<FileModel, String> tagsColumn;

    private Database db;
    private String rootPath;
    private ObservableList<FileModel> fileModels = FXCollections.observableArrayList();

    public void setDatabase(Database db) {
        this.db = db;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
        if (addressLabel != null) {
            addressLabel.setText("Корень: " + rootPath);
        }
        if (navigationTree != null) {
            TreeItem<String> navRoot = new TreeItem<>("Файлы из " + rootPath);
            navigationTree.setRoot(navRoot);
            navigationTree.setShowRoot(true);
        }
    }

    @FXML
    private void initialize() {
        fileTable.setItems(fileModels);
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("path"));
        tagsColumn.setCellValueFactory(new PropertyValueFactory<>("tags"));
    }

    public void loadFiles() {
        if (db == null) return;
        try {
            fileModels.setAll(db.getAllFilesWithTags());
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Ошибка загрузки", e.getMessage());
        }
    }

    @FXML
    private void handleSearch() {
        if (db == null) return;
        String query = searchField.getText().trim();
        try {
            if (query.isEmpty()) {
                fileModels.setAll(db.getAllFilesWithTags());
            } else {
                fileModels.setAll(db.searchFiles(query));
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Ошибка поиска", e.getMessage());
        }
    }

    @FXML
    private void handleEditTags() {
        if (db == null) return;
        FileModel selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Предупреждение", "Выберите файл в таблице для редактирования тегов.");
            return;
        }

        String currentTags = selected.getTags();
        TextInputDialog dialog = new TextInputDialog(currentTags);
        dialog.setTitle("Редактирование тегов");
        dialog.setHeaderText("Файл: " + selected.getPath());
        dialog.setContentText("Введите новые теги через запятую (или оставьте пусто для удаления):");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String input = result.get().trim();
            List<String> newTags = input.isEmpty() ? List.of() :
                    List.of(input.split("\\s*,\\s*")).stream()
                            .filter(s -> !s.isEmpty())
                            .map(String::trim)
                            .toList();
            try {
                db.setTagsForFile(selected.getPath(), newTags);
                selected.setTags(String.join(", ", newTags));
                fileTable.refresh();
            } catch (SQLException e) {
                showAlert(Alert.AlertType.ERROR, "Ошибка обновления тегов", e.getMessage());
            }
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
