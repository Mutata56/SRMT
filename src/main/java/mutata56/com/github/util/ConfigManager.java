package mutata56.com.github.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/**
 * Управление конфигурацией приложения:
 * хранит и читает свойства из файла config.properties
 * в текущей рабочей директории.
 */
public class ConfigManager {
    private static final String CONFIG_FILENAME = "config.properties";
    private static final Path CONFIG_PATH = Paths.get(CONFIG_FILENAME);
    private static final String KEY_OUTPUT_DIR = "outputDir";

    private final Properties props = new Properties();
    private String loadError;  // ошибка при загрузке
    private String saveError;  // ошибка при сохранении

    public ConfigManager() {
        load();
    }

    /** Вернёт текст ошибки загрузки, или null */
    public String getLoadError() {
        return loadError;
    }

    /** Вернёт текст ошибки сохранения, или null */
    public String getSaveError() {
        return saveError;
    }

    private void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) {
                loadError = e.getMessage();
            }
        }
    }

    public void save() {
        try (OutputStream out = Files.newOutputStream(CONFIG_PATH,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            props.store(new OutputStreamWriter(out, StandardCharsets.UTF_8),
                    "RimTranslator Configuration");
        } catch (IOException e) {
            saveError = e.getMessage();
        }
    }

    public File getOutputDir() {
        String path = props.getProperty(KEY_OUTPUT_DIR);
        if (path != null && !path.isBlank()) {
            File dir = new File(path);
            if (dir.isDirectory()) return dir;
        }
        return null;
    }

    public void setOutputDir(File dir) {
        props.setProperty(KEY_OUTPUT_DIR, dir.getAbsolutePath());
        save();
    }
}
