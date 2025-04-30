<!-- badges: start -->
![Java](https://img.shields.io/badge/Java-17%2B-blue)
![License: MIT](https://img.shields.io/badge/License-MIT-green)
<!-- badges: end -->

# RimTranslator

**RimTranslator** — инструмент для автоматического перевода пользовательских модификаций RimWorld  
с русского ↔ английский и обратно, с визуальным сравнением изменений и патчингом метаданных.

---

## 🚀 Зачем нужен

- Вы переводите моды, сделанные другими игроками, чтобы они работали в вашей локализации.
- Автоматически меняются тексты в XML-файлах (`<label>` и `<description>`), патчатся About.xml и Preview.png.
- Есть удобный **DiffViewer**: бок-о-бок сравнение оригинала и перевода, где подсвечиваются только теги, в которых текст изменился.

---

## 🔧 Технологии

- **Java 17+** + **JavaFX** (GUI)  
- **Maven** (сборка, управление зависимостями)  
- **Flexmark** (рендеринг Markdown в документации)  
- **LibreTranslate** (офлайн-сервер через Docker для перевода)  
- **DOM XML** (парсинг и правка About.xml)  
- **ImageIO + AWT** (патч Preview.png — наложение оверлея)  
- **RichTextFX** (DiffViewer с подсветкой)

---

## ⚙️ Как запустить

1. **Склонируйте репозиторий**  
   ```bash
   git clone https://github.com/mutata56/rim-translator.git
   cd rim-translator
   
2. Запустите сервис LibreTranslate
(в корне проекта находится docker-compose.yml)
<pre>docker-compose up -d</pre>

3. Соберите “fat” JAR
<pre>mvn clean package</pre>
После этого в target/ появится rim-translator-1.0.0-shaded.jar.

4. Запустите приложение

<pre>java --module-path /lib --add-modules javafx.controls,javafx.fxml -jar target/rim-translator-1.0.0-shaded.jar</pre>

5. В GUI выберите:

 - Original Mod Folder — папку с исходным модом

 - Output Folder — куда сохранить переведённый мод
 
6. Нажмите Translate.


## ⭐ Основные возможности
- **Пакетный перевод XML**
Рекурсивно обходятся все .xml в структуре мода, в тегах <label> и <description> переводится текст.

- **PatchAbout**
В файле About/About.xml автоматически меняются:

     - <author> → ваш ник
    
     - <packageId> → translations
    
     - <name> → добавляется [RU] или [EN]

 - Перезаписываются секции "loadAfter" и "modDependencies".

- **PatchPreview**
В папке About/Preview.png накладывается ваш оверлей (из resources/img/overlay.png) в левый-верхний угол, и результат сохраняется в выводном моде.

- **DiffViewer**
Встроенный просмотрщик:

- Две области: слева оригинал, справа перевод.

- Подсветка только тех XML-тегов, где текст изменился (логично — остальные остаются белыми).

- Горячие клавиши: Ctrl+S — сохранить правую панель, Ctrl+Scroll — зум.

 - **Логи**
 - В процессе перевода внизу приложения идёт лог:

 - Список обработанных файлов;

 - Ошибки чтения/записи;

 - Статус “Переведено X из Y”.

## 📂 Структура проекта
<pre>
  ├── pom.xml
├── src
│   ├── main
│   │   ├── java/com/mutata56/…      # исходники
│   │   ├── resources
│   │   │   ├── img/overlay.png       # ваш водяной знак
│   │   │   └── view/*.fxml           # FXML-шаблоны
</pre>
