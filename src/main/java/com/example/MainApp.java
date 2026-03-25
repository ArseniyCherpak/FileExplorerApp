package com.example;

import com.example.controller.MainController;
import com.example.controller.SelectRootController;
import com.example.model.Database;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    private Database db;

    @Override
    public void start(Stage primaryStage) throws Exception {
        db = new Database();

        String lastRoot = db.getLastRoot();
        String selectedRoot = lastRoot;

        if (lastRoot == null || lastRoot.isEmpty()) {
            FXMLLoader selectLoader = new FXMLLoader(getClass().getResource("/SelectRootView.fxml"));
            Scene selectScene = new Scene(selectLoader.load());
            SelectRootController selectController = selectLoader.getController();

            Stage selectStage = new Stage();
            selectStage.setTitle("Выбор корневой директории для индексации");
            selectStage.setScene(selectScene);
            selectStage.showAndWait();

            selectedRoot = selectController.getSelectedPath();
            if (selectedRoot == null || selectedRoot.isEmpty()) {
                System.exit(0);
            }
            db.setLastRoot(selectedRoot);
        }

        // Индексация (полная при первом запуске или инкрементальная при повторном с той же директорией)
        long lastScanTime = db.getLastScanTime();
        db.indexDirectory(selectedRoot, lastScanTime);
        db.setLastScanTime(System.currentTimeMillis());

        // Главное окно
        FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("/MainView.fxml"));
        Scene mainScene = new Scene(mainLoader.load(), 1200, 800);
        MainController mainController = mainLoader.getController();
        mainController.setDatabase(db);
        mainController.setRootPath(selectedRoot);
        mainController.loadFiles();

        primaryStage.setScene(mainScene);
        primaryStage.setTitle("Файловый менеджер с тегами");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
