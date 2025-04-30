/**
 * Контроллер главного окна приложения RimTranslator.
 * <p>
 * Отвечает за взаимодействие пользователя с интерфейсом:
 * выбор папок для ввода/вывода модов, настройка языковой пары,
 * запуск процесса перевода, отображение логов и статуса, а также работу меню (Help, About, Exit).
 * </p>
 *
 * @author mutata56
 * @version 1.0.0
 */
package mutata56.com.github.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.event.ActionEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import mutata56.com.github.util.ConfigManager;
import mutata56.com.github.util.ModProcessor;
import mutata56.com.github.util.TranslatorService;
import mutata56.com.github.view.DiffViewerController;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Контроллер для главного интерфейса RimTranslator.
 */
public class MainController {

    /** Комбобокс выбора исходного языка перевода. */
    @FXML private ComboBox<String> sourceLang;
    /** Комбобокс выбора целевого языка перевода. */
    @FXML private ComboBox<String> targetLang;
    /** Отображает дерево файлов и папок исходного мода. */
    @FXML private TreeView<File> modTree;
    /** Прогресс-бар для отображения хода процесса перевода. */
    @FXML private ProgressBar progressBar;
    /** Метка для вывода текущего статуса приложения. */
    @FXML private Label statusLabel;
    /** Текстовое поле для логов операций (перевод, ошибки и предупреждения). */
    @FXML private TextArea logArea;

    @FXML private Button translateButton;

    @FXML private Button inputButton;

    @FXML private Button outputButton;

    @FXML private Menu fileMenu;

    @FXML private Button clearButton;
    /** Корневая папка исходного мода. */
    private File inputDir;
    /** Папка для сохранения переведённого мода. */
    private File outputDir;

    private ConfigManager config;

    private ModProcessor modProcessor;
    /**
     * Инициализация контроллера.
     * <p>
     * Заполняет списки языков, устанавливает значения по умолчанию и сбрасывает
     * статус и прогресс-бар.
     * </p>
     */
    public void initialize() {
        // инициализация языков
        sourceLang.getItems().addAll("Russian", "English");
        targetLang.getItems().addAll("English", "Russian");
        sourceLang.getSelectionModel().selectFirst();
        targetLang.getSelectionModel().select(1);



        // сброс прогресса и статуса
        progressBar.setProgress(0);
        statusLabel.setText("Готово");

        // 1) Пытаемся найти Steam
        File steam = detectSteamFolder();
        if (steam == null) {
            log("Не удалось обнаружить папку Steam на этом компьютере.\n");
            statusLabel.setText("Steam не найден");
            return;
        }
        log("Найдена папка Steam: " + steam.getAbsolutePath() + "\n");

        // 2) Пытаемся найти папку с модами RimWorld
        File mods = detectRimworldModsFolder(steam);
        if (mods != null) {
            log("Найдена папка RimWorld модов: " + mods.getAbsolutePath() + "\n");
            statusLabel.setText("Найдена папка с RimWorld");
            inputDir = mods;
            loadModTree(mods);
        } else {
            log("Не удалось найти папку с модами RimWorld (workshop/content/294100).\n");
            statusLabel.setText("Папка RimWorld модов не найдена");
        }
        // попробуем загрузить конфиг и вывести ошибку в лог, если она есть
        config = new ConfigManager();
        String err = config.getLoadError();
        if (err != null) {
            logAndShowError("⚠ Не удалось загрузить config.properties(Файл настроек): "
                    + err + "\n");
        }

        // если в конфигах есть папка вывода — применим её
        File savedOutput = config.getOutputDir();
        if (savedOutput != null) {
            outputDir = savedOutput;
            statusLabel.setText("Папка вывода: " + outputDir.getAbsolutePath());
        }
    }

    /**
     * Ищет корневую папку Steam.
     */
    private File detectSteamFolder() {
        String os = System.getProperty("os.name").toLowerCase();
        List<Path> candidates = new ArrayList<>();
        if (os.contains("win")) {
            String pf86 = System.getenv("ProgramFiles(x86)");
            String pf    = pf86 != null ? pf86 : System.getenv("ProgramFiles");
            if (pf != null) candidates.add(Paths.get(pf, "Steam"));
        } else if (os.contains("mac")) {
            candidates.add(Paths.get(System.getProperty("user.home"),
                    "Library", "Application Support", "Steam"));
        } else {
            candidates.add(Paths.get(System.getProperty("user.home"), ".steam", "steam"));
            candidates.add(Paths.get(System.getProperty("user.home"),
                    ".local", "share", "Steam"));
        }
        for (Path p : candidates) {
            if (Files.isDirectory(p) &&
                    (Files.exists(p.resolve("steam.exe")) ||
                            Files.exists(p.resolve("steam.sh")) ||
                            Files.isDirectory(p.resolve("steamapps")))) {
                return p.toFile();
            }
        }
        return null;
    }

    private void log(String msg) {
        // Удобный метод для записи в TextArea
        logArea.appendText(msg);
    }
    /**
     * По корню Steam ищет подпапку с workshop-контентом RimWorld (ID 294100).
     */
    private File detectRimworldModsFolder(File steamFolder) {
        Path workshop = steamFolder.toPath()
                .resolve("steamapps")
                .resolve("workshop")
                .resolve("content")
                .resolve("294100");
        if (Files.isDirectory(workshop)) {
            return workshop.toFile();
        }
        return null;
    }

    private Path correspondingOutputPath(Path srcPath) {
        Path inRoot  = inputDir.toPath();
        Path outRoot = outputDir.toPath();
        // относительный путь внутри inputDir
        Path relative = inRoot.relativize(srcPath);
        // аппендим к outputDir
        return outRoot.resolve(relative);
    }

    /**
     * Обработчик кнопки "Перевести мод".
     * <p>
     * Запускает процесс пакетного перевода, используя фоновые задачи.
     * </p>
     *
     * @param event событие нажатия кнопки
     */
    @FXML
    private void onTranslate(ActionEvent event) {
        logArea.clear();
        statusLabel.setText("Запускаем перевод…");
        setControlsDisabled(true);

        // Создаём и настраиваем сервис
        TranslatorService service = new TranslatorService(
                inputDir,
                outputDir,
                sourceLang.getValue(),
                targetLang.getValue()
        );
        progressBar.progressProperty().bind(service.progressProperty());
        service.messageProperty().addListener((obs, oldMsg, newMsg) -> {
            logArea.appendText(newMsg);
        });
        service.progressProperty().addListener((o, old, val) ->
                statusLabel.setText(String.format("Переведено %d%%", (int)(val.doubleValue()*100)))
        );
        service.setOnSucceeded(e -> {
            statusLabel.setText("Перевод завершён");
            setControlsDisabled(false);
            openOutputDirectory();

            // 1) Собираем Map с парами оригинал/перевод
            Map<String, DiffViewerController.Pair<String,String>> diffs = new LinkedHashMap<>();
            for (Path src : service.getChangedSourcePaths()) {
                Path dst = correspondingOutputPath(src);
                String orig = null;
                String trans = null;
                try {
                    orig = Files.readString(src, StandardCharsets.UTF_8);
                    trans= Files.readString(dst, StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    logArea.appendText("Критическая ошибка:\n" + ex.getMessage());
                    return;
                }
                diffs.put(src.getFileName().toString(),
                        new DiffViewerController.Pair<>(orig, trans));
            }

            Platform.runLater(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(
                            getClass().getResource("/view/DiffViewer.fxml"));
                    Parent root = loader.load();
                    DiffViewerController ctrl = loader.getController();
                    ctrl.init(diffs,service.getNameToOutputFile());


                    Stage stage = new Stage();
                    stage.setTitle("Просмотр изменений");
                    stage.setScene(new Scene(root, 800, 600));
                    stage.show();
                } catch (IOException ex) {
                    logArea.appendText("Не удалось открыть просмотр изменений: " + ex.getMessage());
                }
            });

        });
        service.setOnFailed(e -> {
            logArea.appendText("❌ Ошибка сервиса: " +
                    service.getException().getMessage() + "\n");
            setControlsDisabled(false);
        });
        // Запускаем фоновую задачу
        service.start();

    }
    /** Открывает outputDir в системном файловом менеджере */
    private void openOutputDirectory() {
        if (outputDir == null) return;
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(outputDir);
            } else {
                // Запасной вариант для Linux
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("linux")) {
                    new ProcessBuilder("xdg-open", outputDir.getAbsolutePath())
                            .start();
                } else if (os.contains("mac")) {
                    new ProcessBuilder("open", outputDir.getAbsolutePath())
                            .start();
                }
            }
        } catch (Exception ex) {
            logArea.appendText("Не удалось открыть папку вывода: " + ex.getMessage() + "\n");
        }
    }

    private void setControlsDisabled(boolean disabled) {
        inputButton.setDisable(disabled);
        outputButton.setDisable(disabled);
        translateButton.setDisable(disabled);
        clearButton.setDisable(disabled);
        sourceLang.setDisable(disabled);
        targetLang.setDisable(disabled);
        fileMenu.setDisable(disabled);

    }


    /**
     * Открывает диалог выбора папки с исходным модом.
     * При выборе обновляет статус и загружает структуру в TreeView.
     */
    @FXML
    private void onChooseInput() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Выберите папку с оригинальным модом");
        if (inputDir != null && inputDir.exists()) {
            chooser.setInitialDirectory(inputDir);
        }
        File dir = chooser.showDialog(getStage());
        if (dir != null) {
            inputDir = dir;
            statusLabel.setText("Вход: " + dir.getName());
            loadModTree(dir);
            if (outputDir != null)
                translateButton.setDisable(false);
        }
    }

    /**
     * Открывает диалог выбора папки для сохранения переведённого мода.
     * При выборе обновляет статус.
     */
    @FXML
    private void onChooseOutput() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Выберите папку для переведённого мода");
        if (outputDir != null && outputDir.exists()) {
            chooser.setInitialDirectory(outputDir);
        }
        File dir = chooser.showDialog(getStage());
        if (dir != null) {
            outputDir = dir;
            config.setOutputDir(dir);
            statusLabel.setText("Папка вывода: " + dir.getAbsolutePath());
            // логируем ошибку сохранения, если она была
            String saveErr = config.getSaveError();
            if (saveErr != null) {
                logAndShowError("⚠ Не удалось сохранить config.properties (Файл настроек): "
                        + saveErr + "\n");
            } else {
                if (inputDir != null)
                    translateButton.setDisable(false);
            }
        }
    }

    /**
     * Очищает текущие выборы папок, дерево файлов, прогресс-бар, статус и логи.
     */
    @FXML
    private void onClear() {
        inputDir = null;
        outputDir = null;
        modTree.setRoot(null);
        progressBar.setProgress(0);
        statusLabel.setText("Готово");
        logArea.clear();
        translateButton.setDisable(true);
    }

    /**
     * Экспортирует текущий лог операций в текстовый файл.
     * Открывает FileChooser для выбора пути и имени файла.
     */
    @FXML
    private void onExportReport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Сохранить отчёт перевода");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt")
        );
        File file = chooser.showSaveDialog(getStage());
        if (file != null) {
            try {
                Files.writeString(file.toPath(), logArea.getText(), StandardCharsets.UTF_8);
                statusLabel.setText("Отчёт сохранён: " + file.getName());
            } catch (IOException ex) {
                showError("Ошибка при сохранении отчёта");
            }
        }
    }

    /**
     * Завершает работу приложения.
     */
    @FXML
    private void onExit() {
        Platform.exit();
    }

    /**
     * Открывает окно справки, загружая локальный HTML-файл в WebView.
     * В случае ошибки отображает сообщение об ошибке.
     */
    @FXML
    private void onHelpContents() {
        try {
            WebView webView = new WebView();
            webView.getEngine().load(
                    getClass().getResource("/help/help.html").toExternalForm()
            );
            Stage helpStage = new Stage();
            helpStage.setTitle("Справка");
            helpStage.setScene(new Scene(webView, 800, 600));
            helpStage.show();
        } catch (Exception e) {
            showError("Не удалось открыть справку");
        }
    }

    private void openURI(String uriStr) {
        try {
            Desktop.getDesktop().browse(new URI(uriStr));
        } catch (Exception e) {
            showError("Не удалось открыть ссылку: " + uriStr);
        }
    }

    /**
     * Отображает диалог "О программе" с информацией о версии, авторе и ссылками.
     */
    @FXML
    private void onAbout() {
        Dialog<Void> dialog = new Dialog<>();

        // Создаём заголовок с иконкой и выравниванием по центру
        HBox headerBox = new HBox(5);
        Label infoIcon = new Label("ℹ");
        infoIcon.setStyle("-fx-font-size: 18px; -fx-text-fill: #2a579a;");
        Label headerLabel = new Label("RimTranslator");
        headerLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        headerBox.getChildren().addAll(infoIcon, headerLabel);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setPadding(new Insets(10, 0, 0, 0));

        dialog.getDialogPane().setHeader(headerBox);
        dialog.setTitle("О программе");

        Label versionLabel = new Label("Версия: 1.0.1");
        Label authorLabel = new Label("Автор: mutata56");
        Hyperlink gitLink = new Hyperlink("GitHub: https://github.com/mutata56/rim-translator");
        gitLink.setOnAction(e -> openURI("https://github.com/mutata56/rim-translator"));
        Hyperlink steamLink = new Hyperlink("Steam: https://steamcommunity.com/id/KKRLL56/");
        steamLink.setOnAction(e -> openURI("https://steamcommunity.com/id/KKRLL56/"));
        Label licenseLabel = new Label("Лицензия: MIT");

        VBox content = new VBox(8, versionLabel, authorLabel, gitLink, steamLink, licenseLabel);
        content.setStyle("-fx-font-size: 14px;");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.showAndWait();
    }

    /**
     * Открывает в браузере страницу создания issue на GitHub.
     * При неудаче показывает сообщение об ошибке.
     */
    @FXML
    private void onReportIssue() {
        try {
            Desktop.getDesktop().browse(
                    new URI("https://github.com/mutata56/rim-translator/issues")
            );
        } catch (Exception e) {
            showError("Не удалось открыть страницу issues");
        }
    }

    public void logAndShowError(String errorString) {
        logArea.appendText(errorString);
        showError(errorString);
    }

    public void logAndShowError(String error,String logs) {
        logArea.appendText(logs);
        showError(error);
    }

    /**
     * Показывает модальное окно с ошибкой и указанным сообщением.
     *
     * @param message текст сообщения об ошибке
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.showAndWait();
    }

    /**
     * Загружает структуру файлов и папок заданного каталога в TreeView.
     *
     * @param rootDir корневая папка мода для отображения
     */
    private void loadModTree(File rootDir) {
        TreeItem<File> rootItem = createNode(rootDir);
        rootItem.setExpanded(true);
        modTree.setRoot(rootItem);
    }

    /**
     * Рекурсивно создает TreeItem для файла или папки.
     * Если файл является директорией, создается вложенная структура.
     *
     * @param file файл или папка для представления
     * @return корневой узел TreeItem, содержащий дочерние элементы
     */
    private TreeItem<File> createNode(File file) {
        TreeItem<File> item = new TreeItem<>(file);
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    item.getChildren().add(createNode(child));
                }
            }
        }
        return item;
    }

    /**
     * Возвращает Stage, связанный с текущим окном, используя статусную метку.
     *
     * @return Stage текущего окна приложения
     */
    private Stage getStage() {
        return (Stage) statusLabel.getScene().getWindow();
    }
}