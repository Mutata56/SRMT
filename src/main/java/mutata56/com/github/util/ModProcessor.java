package mutata56.com.github.util;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ModProcessor {

    private final static String ERROR_MESSAGE = "Не удалось получить доступ к указанным папкам:\n";
    private final LibreTranslateService translator = new LibreTranslateService();




    public void patchPreviewImage(Path inputAbout, Path outputAbout) {
        try {
            // 1) Исходный Preview.png
            Path srcPreview = inputAbout.resolve("Preview.png");
            if (!Files.exists(srcPreview)) {
                // ничего не делаем, если превью нет
                return;
            }

            // 2) Читаем исходную картинку
            BufferedImage base = ImageIO.read(srcPreview.toFile());

            // 3) Загружаем оверлей из classpath: resources/img/overlay.png
            String overlayResource = "/img/overlay.png";
            try (InputStream is = getClass().getResourceAsStream(overlayResource)) {
                if (is == null) {
                    throw new FileNotFoundException(
                            "Ресурс не найден: " + overlayResource
                    );
                }
                BufferedImage overlay = ImageIO.read(is);

                // 4) Рисуем оверлей поверх base в левом верхнем углу
                Graphics2D g = base.createGraphics();
                g.setComposite(AlphaComposite.SrcOver);
                g.drawImage(overlay, 0, 0, null);
                g.dispose();
            }

            // 5) Готовим output About: создаём папку, если нужно
            Files.createDirectories(outputAbout);

            // 6) Записываем результат в [outputAbout]/Preview.png
            Path dstPreview = outputAbout.resolve("Preview.png");
            ImageIO.write(base, "PNG", dstPreview.toFile());

        } catch (IOException e) {
            // Здесь логируем ошибку, но не прерываем общий процесс
            e.printStackTrace();
        }
    }

    public void processMod(File inputDir, File outputDir,
                           String srcLang, String tgtLang,
                           Consumer<String> log,
                           BiConsumer<Integer, Integer> progress, BiConsumer<Path,Path> fileCallback, Map<String,Path> nameToOutputPath) {
        try {
            // 1) Собираем список XML-файлов
            List<Path> xmlFiles = Files.walk(inputDir.toPath())
                    .filter(p -> p.toString().endsWith(".xml"))
                    .toList();

            int total = xmlFiles.size();
            int done = 0;

            // 2) Для каждого файла
            for (Path srcPath : xmlFiles) {
                // относительный путь для логов и для сохранения
                Path rel = inputDir.toPath().relativize(srcPath);
                Path dstPath = outputDir.toPath().resolve(rel);
                nameToOutputPath.put(String.valueOf(rel.getFileName()),dstPath);
                // создаём папки, если нужно
                Files.createDirectories(dstPath.getParent());

                // переводим и сохраняем
                translateAndSaveXml(srcPath, dstPath, srcLang, tgtLang);
                fileCallback.accept(srcPath, dstPath);
                done++;
                log.accept("Переведён: " + rel + "\n");
                progress.accept(done, total);
            }

            log.accept("Все файлы переведены.\n");
        } catch (Exception e) {
            log.accept("Ошибка при переводе мода: \n" + e.getMessage());
            return;
        }


    }

    /**
     * Патчит About.xml:
     * – правит author, packageId, name (как было раньше);
     * – очищает и заполняет <loadAfter> одним <li>oldPackageName</li>;
     * – очищает и заполняет <modDependencies> одним <li>…</li> с
     *   packageId, displayName и steamWorkshopUrl.
     *
     * @param aboutXml         путь к [outputDir]/About/About.xml

     * @param steamId          ID мода в Steam Workshop
     */
    public void patchAboutXml(Path aboutXml,
                              String steamId) throws Exception {
        // === 1) Парсим документ ===
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        org.w3c.dom.Document doc;


        try (InputStream in = Files.newInputStream(aboutXml)) {
            doc = db.parse(new InputSource(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }

        // === 2) Обычные патчи: author, packageId, name ===
        Node author = doc.getElementsByTagName("author").item(0);
        if (author != null) author.setTextContent("KKRL56");

        Node pkg = doc.getElementsByTagName("packageId").item(0);
        String oldPackageName = pkg.getTextContent();

        if (pkg != null) pkg.setTextContent("kkrll56.translations");

        Node nameNode = doc.getElementsByTagName("name").item(0);
        String oldName = nameNode.getTextContent();
        if (nameNode != null) nameNode.setTextContent(oldName + "[RU]");


        // === 3) Обновляем <loadAfter> ===
        NodeList loadAfterList = doc.getElementsByTagName("loadAfter");
        for (int i = 0; i < loadAfterList.getLength(); i++) {
            Element loadAfter = (Element) loadAfterList.item(i);
            // удаляем всех детей
            while (loadAfter.hasChildNodes()) {
                loadAfter.removeChild(loadAfter.getFirstChild());
            }
            // создаём <li>oldPackageName</li>
            Element li = doc.createElement("li");
            li.setTextContent(oldPackageName);
            loadAfter.appendChild(li);
        }

        // === 4) Обновляем <modDependencies> ===
        NodeList depsList = doc.getElementsByTagName("modDependencies");
        for (int i = 0; i < depsList.getLength(); i++) {
            Element deps = (Element) depsList.item(i);
            // удаляем всех детей
            while (deps.hasChildNodes()) {
                deps.removeChild(deps.getFirstChild());
            }
            // создаём <li>
            Element li = doc.createElement("li");
            //  └─ <packageId>oldPackageName</packageId>
            Element pid = doc.createElement("packageId");
            pid.setTextContent(oldPackageName);
            li.appendChild(pid);
            //  └─ <displayName>oldName</displayName>
            Element dname = doc.createElement("displayName");
            dname.setTextContent(oldName);
            li.appendChild(dname);
            //  └─ <steamWorkshopUrl>https://.../id=steamId</steamWorkshopUrl>
            Element url = doc.createElement("steamWorkshopUrl");
            url.setTextContent(
                    "https://steamcommunity.com/sharedfiles/filedetails/?id=" + steamId
            );
            li.appendChild(url);

            deps.appendChild(li);
        }

        // === 5) Сохраняем обратно ===
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        try (Writer w = Files.newBufferedWriter(aboutXml, StandardCharsets.UTF_8)) {
            t.transform(new DOMSource(doc), new StreamResult(w));
        }
    }
    /**
     * Открывает XML, переводит содержимое тегов <label> и <description> и пишет результат.
     */
    private void translateAndSaveXml(Path srcPath, Path dstPath,
                                     String srcLang, String tgtLang) throws Exception {
        // 1) Считаем весь файл как строку
        byte[] raw = Files.readAllBytes(srcPath);
        String xml = new String(raw, StandardCharsets.UTF_8);
        // 2) Убираем BOM (U+FEFF) и любые пробельные символы перед декларацией
        xml = xml.replaceFirst("\\A\\uFEFF*", "")      // убрать BOM
                .replaceFirst("\\A\\s*(?=<)", "");    // убрать пробелы/переводы строк до '<'

        // 3) Парсим из этой «чистой» строки
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc;
        try (StringReader reader = new StringReader(xml)) {
            doc = db.parse(new InputSource(reader));
        }

        // 4) Переводим теги и сохраняем точно так же, как раньше
        translateElements(doc, "label", srcLang, tgtLang);
        translateElements(doc, "description", srcLang, tgtLang);

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "no");
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

        try (Writer out = Files.newBufferedWriter(dstPath, StandardCharsets.UTF_8)) {
            t.transform(new DOMSource(doc), new StreamResult(out));
        }
    }


    /**
     * Находит все узлы с именем tagName, берёт их текст, переводит и обрезает на результат.
     */
    private void translateElements(Document doc,
                                   String tagName,
                                   String srcLang,
                                   String tgtLang) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            String original = n.getTextContent().trim();
            if (!original.isEmpty()) {
                // srcLang: "Russian"/"English" → "ru"/"en"
                String srcCode = srcLang.toLowerCase().startsWith("r") ? "ru" : "en";
                String tgtCode = tgtLang.toLowerCase().startsWith("e") ? "en" : "ru";
                String translated = translator.translate(original, srcCode, tgtCode);
                n.setTextContent(translated);
            }
        }
    }
}