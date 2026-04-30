package com.hospital.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import org.springframework.util.backoff.FixedBackOff;

/**
 * КОНФИГУРАЦИЯ APACHE KAFKA
 *
 * Этот класс настраивает три ключевых аспекта Kafka в нашем приложении:
 *   1. Топики (Topics) — "каналы" для сообщений, создаются автоматически при старте
 *   2. Dead Letter Topics (DLT) — "очереди мёртвых писем" для необрабатываемых сообщений
 *   3. KafkaListenerContainerFactory — фабрика контейнеров для @KafkaListener-потребителей
 *
 * Как работает Kafka в нашей системе:
 *   Производитель (Producer): EventPublisher.send() → kafkaTemplate.send(topic, key, payload)
 *   Брокер (Broker): хранит сообщения в топиках, разбитых на партиции
 *   Потребитель (Consumer): @KafkaListener методы в PatientEventConsumer, AdmissionEventConsumer
 *
 * Топики и партиции:
 *   Каждый топик разбит на N партиций (partitions = 3).
 *   Партиции позволяют параллельно обрабатывать сообщения несколькими потребителями.
 *   Ключ сообщения (eventId) определяет, в какую партицию попадёт сообщение —
 *   это гарантирует порядок сообщений с одним ключом.
 *
 * Replication factor = 1 (только для разработки!):
 *   В production ставят 3, чтобы каждый брокер в кластере хранил копию данных.
 *   При replication-factor = 1: если брокер упадёт — данные потеряются.
 */
@Slf4j
@Configuration
public class KafkaConfig {

    // Имена топиков читаются из application.yml.
    // Такой подход позволяет менять топики без перекомпиляции (через переменные окружения).
    @Value("${kafka.topics.patient-events}")
    private String patientEventsTopic;

    @Value("${kafka.topics.admission-events}")
    private String admissionEventsTopic;

    @Value("${kafka.topics.paid-service-events}")
    private String paidServiceEventsTopic;

    @Value("${kafka.topics.doctor-events}")
    private String doctorEventsTopic;

    @Value("${kafka.topics.department-events}")
    private String departmentEventsTopic;

    // Количество партиций — параллелизм обработки.
    // 3 партиции = до 3 потребителей могут читать топик одновременно.
    // ":3" — значение по умолчанию, если kafka.partitions не задан в конфигурации.
    @Value("${kafka.partitions:3}")
    private int partitions;

    // Фактор репликации: сколько брокеров хранят копию данных.
    // 1 = только один брокер (подходит для dev/test, НЕ для production).
    @Value("${kafka.replication-factor:1}")
    private short replicationFactor;

    // ---- Основные топики ----
    // NewTopic — Spring Kafka создаёт топик при старте приложения, если он не существует.
    // TopicBuilder — fluent API для конфигурации топика.

    @Bean
    public NewTopic patientEventsTopic() {
        return TopicBuilder.name(patientEventsTopic).partitions(partitions).replicas(replicationFactor).build();
    }

    @Bean
    public NewTopic admissionEventsTopic() {
        return TopicBuilder.name(admissionEventsTopic).partitions(partitions).replicas(replicationFactor).build();
    }

    @Bean
    public NewTopic paidServiceEventsTopic() {
        return TopicBuilder.name(paidServiceEventsTopic).partitions(partitions).replicas(replicationFactor).build();
    }

    @Bean
    public NewTopic doctorEventsTopic() {
        return TopicBuilder.name(doctorEventsTopic).partitions(partitions).replicas(replicationFactor).build();
    }

    @Bean
    public NewTopic departmentEventsTopic() {
        return TopicBuilder.name(departmentEventsTopic).partitions(partitions).replicas(replicationFactor).build();
    }

    // ---- Dead Letter Topics (DLT) — очереди для необрабатываемых сообщений ----
    //
    // Если сообщение N раз подряд вызывает исключение в потребителе (настраивается в DefaultErrorHandler),
    // оно перемещается в соответствующий DLT-топик (имя основного топика + ".DLT").
    //
    // Зачем это нужно:
    //   Без DLT: "ядовитое" сообщение (poison pill) блокирует всю партицию навсегда.
    //   С DLT: плохое сообщение изолируется, остальные обрабатываются нормально.
    //
    // DLT имеет 1 партицию — это нормально, т.к. в него попадают единичные "сломанные" сообщения.

    @Bean
    public NewTopic patientEventsDlt() {
        return TopicBuilder.name(patientEventsTopic + ".DLT").partitions(1).replicas(replicationFactor).build();
    }

    @Bean
    public NewTopic admissionEventsDlt() {
        return TopicBuilder.name(admissionEventsTopic + ".DLT").partitions(1).replicas(replicationFactor).build();
    }

    @Bean
    public NewTopic paidServiceEventsDlt() {
        return TopicBuilder.name(paidServiceEventsTopic + ".DLT").partitions(1).replicas(replicationFactor).build();
    }

    @Bean
    public NewTopic doctorEventsDlt() {
        return TopicBuilder.name(doctorEventsTopic + ".DLT").partitions(1).replicas(replicationFactor).build();
    }

    @Bean
    public NewTopic departmentEventsDlt() {
        return TopicBuilder.name(departmentEventsTopic + ".DLT").partitions(1).replicas(replicationFactor).build();
    }

    // ---- Обработчик для DLT ----
    // ConsumerRecordRecoverer вызывается когда сообщение "сдаётся" (исчерпаны все повторные попытки).
    // Здесь мы просто логируем — в production можно отправить алерт или сохранить в БД для разбора.

    @Bean
    public ConsumerRecordRecoverer dltRecoverer() {
        return (record, exception) ->
            log.error("DLQ: failed to process record from topic={}, partition={}, offset={}, key={}, error={}",
                record.topic(), record.partition(), record.offset(), record.key(),
                exception.getMessage());
    }

    // ---- Kafka Transaction Manager ----
    // Используется если нужны транзакционные Kafka-сообщения (ровно один раз, exactly-once семантика).
    // В нашем проекте транзакции БД через JPA + Outbox обеспечивают согласованность,
    // но KafkaTransactionManager может понадобиться для полной exactly-once гарантии.

    @Bean
    public KafkaTransactionManager<String, Object> kafkaTransactionManager(
            ProducerFactory<String, Object> producerFactory) {
        return new KafkaTransactionManager<>(producerFactory);
    }

    /**
     * Фабрика контейнеров для @KafkaListener-потребителей.
     *
     * Эта фабрика создаёт KafkaMessageListenerContainer для каждого @KafkaListener метода.
     * Контейнер — это фоновый поток (или пул потоков), который непрерывно опрашивает Kafka.
     *
     * Ключевые настройки:
     *
     * 1. DefaultErrorHandler с FixedBackOff(1000ms, 2 попытки):
     *    При исключении в потребителе Kafka делает 2 повторные попытки с паузой 1 секунда.
     *    Если после 2 попыток всё равно ошибка → сообщение уходит в DLT через dltRecoverer().
     *    FixedBackOff(interval, maxAttempts): interval=1000мс, maxAttempts=2 повторных попытки
     *    (итого 3 обработки: 1 оригинальная + 2 повторных).
     *
     * 2. AckMode.MANUAL_IMMEDIATE:
     *    Потребитель сам вызывает ack.acknowledge() когда успешно обработал сообщение.
     *    Только тогда Kafka сдвигает offset вперёд.
     *    Это гарантирует "at-least-once" обработку: если потребитель упал ДО acknowledge,
     *    Kafka перечитает то же сообщение.
     *
     * 3. StringDeserializer для значений:
     *    Консьюмер получает строку (JSON), а не объект.
     *    Каждый потребитель сам десериализует через ObjectMapper.
     *    Это гибче чем JsonDeserializer — разные потребители могут читать разные форматы.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            dltRecoverer(), new FixedBackOff(1000L, 2));

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        // MANUAL_IMMEDIATE: offset подтверждается только при явном вызове ack.acknowledge()
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
