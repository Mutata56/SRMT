package mutata56.com.github;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;


/**
 * Точка входа в приложение RimTranslator.
 */
public class App extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {


        // Загружаем разметку главного окна
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/MainView.fxml")
        );
        Parent root = loader.load();

        // Создаем сцену и подключаем стили
        Scene scene = new Scene(root);
        scene.getStylesheets().add(
                getClass().getResource("/view/styles.css").toExternalForm()
        );

        primaryStage.setTitle("RimTranslator");
        primaryStage.setScene(scene);
        primaryStage.setWidth(800);
        primaryStage.setHeight(600);
        primaryStage.show();
    }

    /**
     * Главный метод, запускает JavaFX-приложение.
     */
    public static void main(String[] args) {
        launch(args);
    }
}