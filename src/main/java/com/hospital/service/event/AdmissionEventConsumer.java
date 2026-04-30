package com.hospital.service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.entity.OutboxEvent;
import com.hospital.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Потребитель событий госпитализации/выписки из Kafka-топика admission-events.
 *
 * Обрабатывает два типа событий:
 *   AdmissionEvent.Action.ADMITTED   — пациент поступил в палату
 *   AdmissionEvent.Action.DISCHARGED — пациент выписан из палаты
 *
 * Это демонстрационный потребитель — в текущей реализации выполняет логирование.
 * В production-системе здесь могут быть:
 *   - Обновление кэша занятости палат в реальном времени
 *   - Отправка уведомления дежурной медсестре о новом пациенте
 *   - Запись в audit-log внешней системы
 *   - Пересчёт статистики для дашборда
 *
 * EVENTUAL CONSISTENCY (итоговая согласованность):
 * Это ключевой принцип событийной архитектуры (Event-Driven Architecture).
 * Когда WardServiceImpl.admitPatient() сохраняет пациента в БД и публикует событие,
 * потребитель (этот класс) обрабатывает его АСИНХРОННО, с возможной задержкой.
 * В момент обработки события данные в БД уже гарантированно обновлены
 * (публикатор работает в той же транзакции через Outbox паттерн).
 *
 * Идемпотентность через outbox_event такая же как в PatientEventConsumer:
 * проверяем processed-флаг → обрабатываем → помечаем как обработанное → подтверждаем offset.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdmissionEventConsumer {

    // Репозиторий для проверки и обновления флага processed в outbox_event.
    private final OutboxEventRepository outboxEventRepository;

    // Jackson ObjectMapper для десериализации JSON-строки в объект AdmissionEvent.
    private final ObjectMapper objectMapper;

    /**
     * Обрабатывает сообщение из топика admission-events.
     *
     * groupId = "hospital-admission-consumer" — отдельная группа от patient-consumer.
     * Важно: каждый топик требует своей consumer group (или разных groupId внутри одной группы).
     * Если бы оба потребителя имели одинаковый groupId для разных топиков — это нормально,
     * каждый будет читать только свой топик.
     *
     * Полный цикл обработки (идемпотентный):
     * 1. Десериализация JSON → AdmissionEvent
     * 2. Проверка дубликата через outbox_event
     * 3. Бизнес-логика (здесь — логирование)
     * 4. markProcessed(eventId) — запись в БД
     * 5. ack.acknowledge() — подтверждение Kafka
     */
    @KafkaListener(topics = "${kafka.topics.admission-events}",
            groupId = "hospital-admission-consumer",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(@Payload String message, Acknowledgment ack) {
        try {
            // Десериализация: JSON строка → AdmissionEvent объект.
            AdmissionEvent event = objectMapper.readValue(message, AdmissionEvent.class);

            // Проверка идемпотентности: пропускаем уже обработанные события.
            if (isAlreadyProcessed(event.getEventId())) {
                log.warn("Duplicate admission event skipped: eventId={}", event.getEventId());
                ack.acknowledge(); // дубль — подтверждаем чтобы не застрять
                return;
            }

            // Бизнес-логика — в данном проекте демонстрационное логирование.
            // event.getAction() → ADMITTED или DISCHARGED
            log.info("Received AdmissionEvent: action={}, patientId={}, ward={} ({})",
                    event.getAction(), event.getPatientId(), event.getWardId(), event.getWardNumber());

            // Помечаем как обработанное ДО вызова ack — защита от потери обработки при сбое.
            markProcessed(event.getEventId());

            // Подтверждаем offset ТОЛЬКО после успешной обработки.
            ack.acknowledge();
        } catch (Exception ex) {
            // Не вызываем ack → Kafka повторит доставку.
            // После 2 повторных попыток (DefaultErrorHandler в KafkaConfig) → уйдёт в DLT.
            log.error("Error processing AdmissionEvent: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Проверяет, было ли это событие уже обработано (идемпотентность).
     * @return true — дубль, пропустить; false — новое событие, обработать
     */
    private boolean isAlreadyProcessed(String eventId) {
        return outboxEventRepository.findByEventId(eventId)
                .map(OutboxEvent::isProcessed) // берём поле processed из найденной записи
                .orElse(false);                // запись не найдена → первый раз видим событие
    }

    /**
     * Помечает событие как обработанное в таблице outbox_event (processed = true).
     * ifPresent() безопасно обрабатывает случай когда запись не найдена.
     */
    private void markProcessed(String eventId) {
        outboxEventRepository.findByEventId(eventId).ifPresent(e -> {
            e.setProcessed(true);
            outboxEventRepository.save(e);
        });
    }
}
