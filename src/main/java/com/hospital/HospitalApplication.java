package com.hospital;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа в приложение Pet Hospital HIS.
 *
 * <p>@SpringBootApplication — это составная аннотация, которая одновременно включает три механизма:
 * <ul>
 *   <li>@Configuration — говорит Spring, что этот класс может содержать бины (@Bean-методы).</li>
 *   <li>@EnableAutoConfiguration — запускает автоконфигурацию Spring Boot: фреймворк сам
 *       обнаруживает зависимости в classpath (Hibernate, Security, Kafka и т.д.) и создаёт
 *       нужные бины без XML и ручной настройки.</li>
 *   <li>@ComponentScan — сканирует текущий пакет (com.hospital) и все подпакеты в поисках
 *       компонентов (@Service, @Repository, @Controller, @Component и т.д.) и регистрирует
 *       их в контексте Spring.</li>
 * </ul>
 *
 * <p>Архитектурная заметка: держать этот класс в корневом пакете (com.hospital) — лучшая практика,
 * потому что @ComponentScan по умолчанию сканирует текущий пакет и все дочерние.
 * Если переместить класс в подпакет, часть компонентов окажется вне зоны сканирования.
 */
@SpringBootApplication
public class HospitalApplication {

    /**
     * Главный метод — стандартная точка входа JVM.
     *
     * <p>SpringApplication.run() выполняет полный жизненный цикл запуска:
     * <ol>
     *   <li>Создаёт ApplicationContext (контейнер бинов Spring).</li>
     *   <li>Запускает автоконфигурацию.</li>
     *   <li>Поднимает встроенный Tomcat (или другой сервер, указанный в classpath).</li>
     *   <li>Публикует события запуска (ApplicationStartedEvent и т.д.).</li>
     * </ol>
     *
     * @param args аргументы командной строки; Spring пробрасывает их в Environment,
     *             что позволяет переопределять свойства из application.properties прямо
     *             при запуске jar: java -jar app.jar --server.port=9090
     */
    public static void main(String[] args) {
        SpringApplication.run(HospitalApplication.class, args);
    }
}
