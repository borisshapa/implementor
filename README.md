## Implementor

* Класс `Implementor`, который генерирует реализации классов и интерфейсов.
* Аргумент командной строки: полное имя класса/интерфейса, для которого требуется сгенерировать реализацию.
* В результате работы должен генерируется java-код класса с суффиксом Impl, расширяющий (реализующий) указанный класс (интерфейс).
* Сгенерированный класс компилируется без ошибок.
* Сгенерированный класс не является абстрактным.
* Методы сгенерированного класса игнорируют свои аргументы и возвращают значения по умолчанию.
* При запуске с аргументами `-jar имя-класса файл.jar` он генерирует `.jar-файл` с реализацией соответствующего класса (интерфейса).
* Для того, чтобы протестировать программу:
   * Скачайте
      * тесты
          * [info.kgeorgiy.java.advanced.base.jar](artifacts/info.kgeorgiy.java.advanced.base.jar)
          * [info.kgeorgiy.java.advanced.implementor.jar](artifacts/info.kgeorgiy.java.advanced.implementor.jar)
      * и библиотеки к ним:
          * [junit-4.11.jar](lib/junit-4.11.jar)
          * [hamcrest-core-1.3.jar](lib/hamcrest-core-1.3.jar)
          * [jsoup-1.8.1.jar](lib/jsoup-1.8.1.jar)
          * [quickcheck-0.6.jar](lib/quickcheck-0.6.jar)
   * Откомпилируйте программу
   * Протестируйте программу
      * Текущая директория должна:
         * содержать все скачанные `.jar` файлы;
         * содержать скомпилированные классы;
         * __не__ содержать скомпилированные самостоятельно тесты.
      * Implementor: ```java -cp . -p . -m info.kgeorgiy.java.advanced.implementor advanced ru.ifmo.rain.shaposhnikov.implementor.Implementor```
      * JarImplementor: ```java -cp . -p . -m info.kgeorgiy.java.advanced.implementor jar-advanced ru.ifmo.rain.shaposhnikov.implementor.JarImplementor```