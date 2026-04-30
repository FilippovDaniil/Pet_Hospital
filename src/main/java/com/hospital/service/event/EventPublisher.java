package com.hospital.service.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.entity.OutboxEvent;
import com.hospital.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Центральный публикатор доменных событий.
 *
 * =====================================================================
 * ПАТТЕРН OUTBOX (Transactional Outbox Pattern)
 * =====================================================================
 *
 * Проблема "двойной записи" (dual write):
 *   Если мы сначала сохраняем данные в БД, а потом отправляем сообщение
 *   в Kafka — между этими двумя операциями может произойти сбой.
 *   Данные в БД будут, а сообщение в Kafka — нет. Потребители никогда
 *   не узнают о произошедшем изменении. Это нарушает согласованность.
 *
 * Решение — паттерн Outbox:
 *   1. Бизнес-сервис вызывает publishXxxEvent(...) ВНУТРИ своей транзакции.
 *   2. В методе send() мы делаем ДВЕ вещи в рамках ОДНОЙ транзакции БД:
 *      а) сохраняем запись OutboxEvent в таблицу outbox_event
 *         (processed = false, то есть "событие ещё не обработано")
 *      б) отправляем сообщение в Kafka через kafkaTemplate.send()
 *   3. Если транзакция БД откатывается (rollback) — запись в outbox_event
 *      тоже откатится. Kafka-сообщение технически уже улетело, но
 *      потребитель проверит outbox_event.processed и не найдёт запись —
 *      значит, это было "фантомное" сообщение и оно будет отброшено.
 *   4. Если транзакция успешно зафиксировалась (commit) — и запись в БД,
 *      и Kafka-сообщение существуют. Потребитель обработает событие
 *      и выставит processed = true, чтобы повторная доставка была
 *      отброшена (идемпотентность).
 *
 * Таким образом outbox_event служит одновременно:
 *   - гарантом "at-least-once" доставки (можно перечитать и повторить)
 *   - реестром обработанных eventId (защита от дублей на стороне потребителя)
 *
 * =====================================================================
 * PROPAGATION.MANDATORY — почему EventPublisher не открывает транзакцию
 * =====================================================================
 *
 * @Transactional(propagation = Propagation.MANDATORY) означает:
 *   "Я требую, чтобы вызывающий код уже запустил транзакцию.
 *    Если активной транзакции нет — выброси исключение."
 *
 * Зачем это нужно:
 *   EventPublisher — вспомогательный компонент. Он сам НЕ должен
 *   открывать транзакцию, потому что тогда запись в outbox_event и
 *   изменение бизнес-данных окажутся в РАЗНЫХ транзакциях — и мы
 *   снова получим проблему двойной записи.
 *
 *   Правильная схема:
 *     PatientService (@Transactional)       ← открывает транзакцию
 *       └─ patientRepository.save(...)      ← часть той же транзакции
 *       └─ eventPublisher.publishPatientEvent(...)  ← тоже в ней
 *            └─ outboxEventRepository.save(...)     ← та же транзакция!
 *            └─ kafkaTemplate.send(...)             ← отправка в Kafka
 *
 *   Если кто-то случайно вызовет publishPatientEvent() без транзакции,
 *   MANDATORY немедленно выбросит IllegalTransactionStateException —
 *   это хорошая fail-fast защита от неправильного использования.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    // KafkaTemplate — Spring-обёртка над Kafka Producer.
    // Параметры <String, Object>: ключ сообщения (String) и тело (Object → JSON).
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Репозиторий для работы с таблицей outbox_event в БД.
    // Через него сохраняем «черновик» события перед отправкой в Kafka.
    private final OutboxEventRepository outboxEventRepository;

    // Jackson ObjectMapper: сериализует Java-объект события в JSON-строку,
    // которую мы сохраняем в outbox_event.payload для возможного повтора.
    private final ObjectMapper objectMapper;

    // Названия топиков читаются из application.yml / application.properties.
    // Такой подход позволяет менять топики без перекомпиляции кода.
    @Value("${kafka.topics.patient-events}")
    private String patientTopic;

    @Value("${kafka.topics.admission-events}")
    private String admissionTopic;

    @Value("${kafka.topics.paid-service-events}")
    private String paidServiceTopic;

    @Value("${kafka.topics.doctor-events}")
    private String doctorTopic;

    @Value("${kafka.topics.department-events}")
    private String departmentTopic;

    // PROPAGATION.MANDATORY: транзакция уже должна быть открыта вызывающим сервисом.
    // Публикация пациентского события — оба действия (outbox + kafka) в одной транзакции.
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishPatientEvent(PatientEvent event) {
        send(patientTopic, event.getEventId(), event, "PatientEvent");
    }

    // Аналогично для события госпитализации/выписки.
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishAdmissionEvent(AdmissionEvent event) {
        send(admissionTopic, event.getEventId(), event, "AdmissionEvent");
    }

    // Аналогично для события платной услуги.
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishPaidServiceEvent(PaidServiceEvent event) {
        send(paidServiceTopic, event.getEventId(), event, "PaidServiceEvent");
    }

    // Аналогично для события изменения врача.
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishDoctorEvent(DoctorEvent event) {
        send(doctorTopic, event.getEventId(), event, "DoctorEvent");
    }

    // Аналогично для события изменения отделения.
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishDepartmentEvent(DepartmentEvent event) {
        send(departmentTopic, event.getEventId(), event, "DepartmentEvent");
    }

    /**
     * Внутренний метод, реализующий суть паттерна Outbox:
     *   1. Сериализуем событие в JSON.
     *   2. Сохраняем OutboxEvent в БД (в рамках текущей транзакции).
     *   3. Отправляем сообщение в Kafka асинхронно.
     *
     * Порядок важен: сначала запись в БД (синхронно, в транзакции),
     * потом Kafka (асинхронно, через CompletableFuture / whenComplete).
     * Если Kafka недоступна — транзакция всё равно зафиксируется с
     * outbox-записью, и повторитель (scheduler/retry) сможет её доставить.
     *
     * @param topic     название Kafka-топика
     * @param key       ключ сообщения (eventId) — используется для партиционирования
     * @param payload   объект события (будет сериализован в JSON)
     * @param eventType строковое название типа события (для логирования и outbox)
     */
    private void send(String topic, String key, Object payload, String eventType) {
        try {
            // Сериализуем событие в JSON-строку для хранения в outbox_event.payload.
            // Это позволяет при необходимости повторить отправку без вызова бизнес-логики.
            String json = objectMapper.writeValueAsString(payload);

            // Сохраняем запись в таблицу outbox_event.
            // processed = false (по умолчанию) — событие ещё не подтверждено потребителем.
            // Эта операция выполняется в той же JPA-транзакции, что открыта вызывающим сервисом.
            // Если транзакция откатится — этой записи в БД не будет.
            outboxEventRepository.save(OutboxEvent.builder()
                    .eventId(key)       // UUID события — уникальный идентификатор для дедупликации
                    .eventType(eventType) // тип события — нужен для мониторинга и повторной обработки
                    .payload(json)       // JSON-тело события — полная копия для возможного retry
                    .build());

            // Асинхронно отправляем сообщение в Kafka.
            // kafkaTemplate.send() возвращает CompletableFuture; мы навешиваем callback
            // через whenComplete для логирования результата.
            // ВНИМАНИЕ: Kafka-отправка происходит ПОСЛЕ того, как запись в БД уже поставлена
            // в очередь в рамках транзакции. Фактическая фиксация в БД произойдёт позже —
            // когда транзакция вызывающего сервиса завершится.
            kafkaTemplate.send(topic, key, payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            // Kafka-отправка провалилась. Запись в outbox_event уже в БД
                            // (если транзакция зафиксировалась), поэтому повторитель (retry)
                            // сможет переотправить событие позже.
                            log.error("Failed to send {} to topic {}: {}", eventType, topic, ex.getMessage());
                        } else {
                            // Успешная доставка: логируем партицию и offset для трассировки.
                            log.debug("Sent {} to topic {} partition={} offset={}",
                                    eventType, topic,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (JsonProcessingException e) {
            // Сериализация не удалась — это программная ошибка (неправильная структура класса).
            // Бросаем RuntimeException, чтобы транзакция откатилась и данные не ушли
            // в неконсистентном состоянии.
            throw new RuntimeException("Failed to serialize event " + eventType, e);
        }
    }
}
