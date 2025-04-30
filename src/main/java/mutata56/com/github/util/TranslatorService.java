package mutata56.com.github.util;

import javafx.concurrent.Service;
import javafx.concurrent.Task;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Фоновый сервис для пакетного перевода модов.
 * Сбор исходных путей в changedSourcePaths, патчинг About.xml и Preview.png.
 */
public class TranslatorService extends Service<Void> {

    private final File inputDir;
    private final File outputDir;
    private final String srcLang;
    private final String tgtLang;
    private final ModProcessor processor = new ModProcessor();
    private final Map<String, Path> nameToOutputFile = new HashMap<>();

    public Map<String,Path> getNameToOutputFile() {
        return nameToOutputFile;
    }
    /** Собирает все пути исходных файлов, которые были переведены */
    private final List<Path> changedSourcePaths = new ArrayList<>();
    public List<Path> getChangedSourcePaths() {
        return changedSourcePaths;
    }

    public TranslatorService(File inputDir,
                             File outputDir,
                             String srcLang,
                             String tgtLang) {
        this.inputDir  = inputDir;
        this.outputDir = outputDir;
        this.srcLang   = srcLang;
        this.tgtLang   = tgtLang;
    }

    @Override
    protected Task<Void> createTask() {
        return new Task<>() {
            @Override
            protected Void call() {
                // 1) Основной пакетный перевод: собираем пути и обновляем UI
                processor.processMod(
                        inputDir,
                        outputDir,
                        srcLang,
                        tgtLang,
                        this::updateMessage,
                        this::updateProgress,
                        // callback: добавляем в список каждый srcPath
                        (srcPath, dstPath) -> changedSourcePaths.add(srcPath),
                        nameToOutputFile
                );

                // 2) Патчим About.xml
                updateMessage("Патчим About.xml…\n");
                try {
                    Path aboutXml = outputDir.toPath()
                            .resolve("About")
                            .resolve("About.xml");
                    processor.patchAboutXml(aboutXml,inputDir.getName());
                    updateMessage("About.xml обновлён\n");
                } catch (Exception e) {
                    updateMessage("❌ Ошибка патча About.xml: " + e.getMessage() + "\n");
                }

                // 3) Патчим Preview.png
                updateMessage("Обновляем Preview.png…\n");
                processor.patchPreviewImage(inputDir.toPath().resolve("About"),outputDir.toPath().resolve("About"));
                updateMessage("Preview.png обновлён\n");

                // 4) Завершаем
                updateMessage("Перевод завершён.\n");
                updateProgress(1, 1);
                return null;
            }
        };
    }


}