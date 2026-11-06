# Zimogor Melon (Fabric 1.21.8)

Арбуз выглядит как спавнер: берётся оригинальная текстура спавнера из игры
и включается прозрачность клетки. Поведение арбуза не меняется.

## Сборка через GitHub (без установки Java)

Загрузи проект в репозиторий на GitHub вместе с папкой `.github`. Сборка запустится сама, jar будет во вкладке Actions в разделе Artifacts. Ручной способ ниже нужен только если не хочешь GitHub.

## Как собрать у себя (Windows)

1. Установи **JDK 21** (например, Temurin: https://adoptium.net). Проверка: `java -version` должен показать 21.
2. Распакуй этот архив в папку без русских букв и пробелов, например `C:\mods\zimogor-melon`.
3. В архиве нет файлов `gradlew`, поэтому возьми их из официального шаблона Fabric:
   - зайди на https://fabricmc.net/develop, выбери Minecraft 1.21.8 и нажми Download Template;
   - распакуй шаблон куда угодно и скопируй из него **папку `gradle` и файлы `gradlew` и `gradlew.bat`**
     в папку `zimogor-melon` (рядом с `build.gradle`).
     Больше ничего из шаблона копировать не надо.
   - Если у тебя уже установлен Gradle 8.14+, этот шаг можно пропустить и вместо `gradlew` писать `gradle`.
4. Открой в папке `zimogor-melon` командную строку (в проводнике в адресной строке напиши `cmd` и нажми Enter).
5. Запусти:

       gradlew.bat build

   (на Linux/macOS: `./gradlew build`). Первый раз скачивается много, это нормально, подожди несколько минут.
6. Готовый мод лежит тут: `build\libs\zimogor-melon-1.3.0.jar`.
   Файл с `-sources` в конце не нужен.
7. Положи jar в папку `.minecraft\mods` вместе с **Fabric API** для 1.21.8.

## Если сборка не идёт

- **Не находятся версии (Could not find / resolve)**: зайди на https://fabricmc.net/develop,
  выбери Minecraft 1.21.8 и скопируй актуальные `yarn_mappings`, `loader_version`
  и `fabric_version` в `gradle.properties`.
- **Ошибка в `ZimogorMelonClient.java` про `BlockRenderLayerMap` или `BlockRenderLayer`**:
  в разных версиях Fabric API это имя или способ вызова могли поменяться.
  Скопируй текст ошибки, я поправлю.
- **Видны чёрные дыры вместо прозрачной клетки**: значит, слой CUTOUT не применился.
  Проверь, что в `mods` лежит Fabric API.

## Что внутри

- `src/main/resources/assets/minecraft/models/block/melon.json` подменяет модель арбуза на текстуру спавнера.
- `src/main/java/ua/zimogor/ZimogorMelonClient.java` включает прозрачность (слой CUTOUT) для арбуза.

Вращающегося зимогора внутри клетки здесь нет: для него нужен отдельный код отрисовки.
