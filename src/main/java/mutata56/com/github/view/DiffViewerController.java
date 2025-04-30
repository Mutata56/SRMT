package mutata56.com.github.view;

import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.InlineCssTextArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DiffViewerController {

    @FXML private VBox rootContainer;
    @FXML private TabPane tabPane;


    // Добавляем мапу вверху класса:
    private final Map<Tab, Path> tabToFile = new HashMap<>();
    private static final Pattern TAG_PATTERN =
            Pattern.compile("(</?(?:label|description)>)");

    private static final String NORMAL_STYLE_TEMPLATE =
            "-fx-fill: #ffffff; -fx-font-size: %.1fpt;";
    private static final String TAG_STYLE_TEMPLATE =
            "-fx-fill: #ffb86c; -fx-font-size: %.1fpt; -fx-font-weight: bold;";

    private double fontSize = 14.0;

    public static class Pair<A, B> {
        private final A left; private final B right;
        public Pair(A l, B r) { left = l; right = r; }
        public A getLeft() { return left; }
        public B getRight() { return right; }
    }


    public void init(Map<String, Pair<String,String>> diffs,Map<String, Path> nameToOutputFile) {
        diffs.forEach((name, pair) -> {
            Tab tab = new Tab(name);
            SplitPane split = new SplitPane();

            InlineCssTextArea leftArea  = createArea(pair.getLeft(), false);
            InlineCssTextArea rightArea = createArea(pair.getRight(), true);

            // Навешиваем синхронный зум на обе панели:
            setupZoomSync(leftArea, rightArea);

            VirtualizedScrollPane<InlineCssTextArea> ls =
                    new VirtualizedScrollPane<>(leftArea);
            VirtualizedScrollPane<InlineCssTextArea> rs =
                    new VirtualizedScrollPane<>(rightArea);
            ls.setStyle("-fx-background-color:#1e1e1e;");
            rs.setStyle("-fx-background-color:#1e1e1e;");

            split.getItems().addAll(ls, rs);
            split.setDividerPositions(0.5);

            tab.setContent(split);
            tabPane.getTabs().add(tab);
            // 1) связываем tab с файлом
            Path output = nameToOutputFile.get(name);
            tabToFile.put(tab, output);
        });

    }
    @FXML
    public void initialize() {
        // 1) Навешиваем Ctrl+S, когда сцена станет доступна
        rootContainer.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
                    if (ev.isControlDown() && ev.getCode() == KeyCode.S) {
                        Tab current = tabPane.getSelectionModel().getSelectedItem();
                        if (current != null) saveTab(current);
                        ev.consume();
                    }
                });
            }
        });

        // 2) Слушаем смену вкладки
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (oldTab != null) {
                saveTab(oldTab);
            }
        });
    }
    private void setupZoomSync(InlineCssTextArea left, InlineCssTextArea right) {
        EventHandler<ScrollEvent> zoomHandler = e -> {
            if (e.isControlDown()) {
                double factor = 1.05;
                fontSize = e.getDeltaY() > 0 ? fontSize * factor : fontSize / factor;
                fontSize = Math.max(6.0, Math.min(48.0, fontSize));

                // Обновляем подсветку и шрифты в обеих
                applyHighlighting(left);
                applyHighlighting(right);

                // Пересчёт шрифта у номеров строк, если используете custom factory
                Stream.of(left, right).forEach(area ->
                        area.lookupAll(".line-number-label").forEach(node -> {
                            if (node instanceof Label lbl) {
                                lbl.setFont(Font.font("Consolas", fontSize));
                            }
                        })
                );

                e.consume();
            }
        };

        left.addEventFilter(ScrollEvent.SCROLL, zoomHandler);
        right.addEventFilter(ScrollEvent.SCROLL, zoomHandler);
    }
    /** Поиск только в активной вкладке */

    private void saveTab(Tab tab) {
        // 1) найдём правую панель внутри SplitPane
        SplitPane split = (SplitPane) tab.getContent();
        VirtualizedScrollPane<?> vsp = (VirtualizedScrollPane<?>) split.getItems().get(1);
        InlineCssTextArea rightArea = (InlineCssTextArea) vsp.getContent();

        // 2) получим путь файла
        Path outFile = tabToFile.get(tab);
        if (outFile == null) return;

        // 3) запишем текст
        try {
            Files.writeString(outFile, rightArea.getText(), StandardCharsets.UTF_8);
            // при желании можно вывести статус в statusBar:
            System.out.println("Saved: " + outFile);
        } catch (IOException ex) {
            ex.printStackTrace();
            // можно показать Alert:
            new Alert(Alert.AlertType.ERROR, "Не удалось сохранить " + outFile.getFileName())
                    .showAndWait();
        }
    }

    private InlineCssTextArea createArea(String text, boolean editable) {
        InlineCssTextArea area = new InlineCssTextArea(text);
        area.setEditable(editable);
        area.setWrapText(false);
        area.setBackground(new Background(new BackgroundFill(
                Color.web("#1e1e1e"), CornerRadii.EMPTY, Insets.EMPTY
        )));

        // Сразу узнаём, сколько строк и сколько цифр в максимальном номере:
        int totalLines = area.getParagraphs().size();
        int digitCount = String.valueOf(totalLines).length();
        // Коэффициент ширины одного символа approx 0.6em, плюс паддинг 10px
        final double CHAR_WIDTH_FACTOR = 0.6;
        final double LABEL_PADDING = 10;

        // Фабрика номеров строк:
        area.setParagraphGraphicFactory(idx -> {
            Label ln = new Label(String.valueOf(idx + 1));
            ln.getStyleClass().add("line-number-label");

            // начальный расчёт ширины и шрифта
            double labelWidth = fontSize * digitCount * CHAR_WIDTH_FACTOR + LABEL_PADDING;
            ln.setPrefWidth(labelWidth);
            ln.setMinWidth(labelWidth);
            ln.setAlignment(Pos.CENTER_RIGHT);
            ln.setBackground(new Background(new BackgroundFill(
                    Color.web("#1e1e1e"), CornerRadii.EMPTY, Insets.EMPTY
            )));
            ln.setTextFill(Color.web("#ffb86c"));
            ln.setFont(Font.font("Consolas", fontSize));
            return ln;
        });

        // подсветка и слушатель текста
        applyHighlighting(area);
        area.textProperty().addListener((obs, o, n) -> applyHighlighting(area));



        return area;
    }

    private void applyHighlighting(InlineCssTextArea area) {
        String text = area.getText();
        Matcher m = TAG_PATTERN.matcher(text);
        StyleSpansBuilder<String> sb = new StyleSpansBuilder<>();

        int last = 0;
        while (m.find()) {
            int lenBefore = m.start() - last;
            if (lenBefore > 0) {
                sb.add(String.format(NORMAL_STYLE_TEMPLATE, fontSize), lenBefore);
            }
            int tagLen = m.end() - m.start();
            sb.add(String.format(TAG_STYLE_TEMPLATE, fontSize), tagLen);
            last = m.end();
        }
        int rest = text.length() - last;
        if (rest > 0) {
            sb.add(String.format(NORMAL_STYLE_TEMPLATE, fontSize), rest);
        }

        StyleSpans<String> spans = sb.create();
        area.setStyleSpans(0, spans);
    }

    @FXML private void onClose() {
        Stage s = (Stage) tabPane.getScene().getWindow();
        s.close();
    }
}
