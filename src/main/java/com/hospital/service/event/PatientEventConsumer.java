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

import java.util.Optional;

/**
 * Потребитель событий пациентов из Kafka-топика patient-events.
 *
 * =====================================================================
 * @KafkaListener — как работает
 * =====================================================================
 *
 * Аннотация @KafkaListener делает метод consume() обработчиком сообщений Kafka.
 * Spring Kafka создаёт фоновый поток (KafkaMessageListenerContainer), который
 * непрерывно опрашивает брокер (poll) и вызывает наш метод при появлении
 * новых сообщений.
 *
 * Параметры аннотации:
 *   topics   — название топика из application.yml (${kafka.topics.patient-events}).
 *              Это позволяет менять топик без перекомпиляции.
 *   groupId  — идентификатор Consumer Group ("hospital-patient-consumer").
 *              Все экземпляры приложения с одним groupId образуют группу:
 *              каждое сообщение обрабатывается РОВНО ОДНИМ экземпляром.
 *              Если запустить 3 экземпляра — они разделят партиции топика.
 *   containerFactory — ссылка на KafkaListenerContainerFactory из KafkaConfig.
 *              Именно там настраивается ручное подтверждение (MANUAL_IMMEDIATE ack-mode).
 *
 * =====================================================================
 * Acknowledgment / Manual ACK — зачем подтверждать вручную
 * =====================================================================
 *
 * В Kafka по умолчанию offset подтверждается автоматически (auto.commit).
 * Это удобно, но опасно: если сообщение получено, offset зафиксирован,
 * а обработка упала — сообщение потеряно навсегда.
 *
 * Мы используем ручное подтверждение (AckMode.MANUAL_IMMEDIATE):
 *   - Offset фиксируется только при явном вызове ack.acknowledge().
 *   - Это означает: "я успешно обработал это сообщение, можно двигаться дальше".
 *   - Если обработка прошла успешно — вызываем ack.acknowledge().
 *   - Если произошла ошибка — НЕ вызываем acknowledge().
 *     Kafka перечитает то же сообщение при следующем poll() или после
 *     перебалансировки (rebalance), что обеспечивает повторную попытку.
 *
 * Почему НЕ вызываем ack при ошибке:
 *   Если ack вызвать даже при исключении — offset сдвинется вперёд,
 *   и упавшее сообщение будет пропущено навсегда. Это нарушает гарантию
 *   "at-least-once processing". Лучше обработать дважды (и защититься
 *   идемпотентностью), чем не обработать вовсе.
 *
 * =====================================================================
 * Dead Letter Topic (DLT) — что будет при постоянных ошибках
 * =====================================================================
 *
 * Если сообщение систематически вызывает исключение (например, некорректный JSON),
 * и мы никогда не вызываем ack — потребитель застревает на одном сообщении.
 * Решение — Dead Letter Topic:
 *   - После N неудачных попыток (настраивается в SeekToCurrentErrorHandler /
 *     DefaultErrorHandler) сообщение автоматически перекладывается в отдельный
 *     топик: patient-events.DLT (или hospital.DLT и т.п.).
 *   - Основной потребитель продолжает работу с остальными сообщениями.
 *   - Сообщения из DLT можно позже проанализировать и при необходимости
 *     переиграть вручную или автоматически.
 *
 * =====================================================================
 * Идемпотентность — защита от двойной обработки
 * =====================================================================
 *
 * Kafka гарантирует доставку "at-least-once": при сбоях брокера или ребалансировке
 * одно и то же сообщение может прийти дважды. Без защиты одно событие
 * (например, PatientEvent) будет обработано несколько раз — это некорректно.
 *
 * Наша защита через outbox_event:
 *   1. EventPublisher сохраняет OutboxEvent(eventId, processed=false) при публикации.
 *   2. consume() проверяет isAlreadyProcessed(event.getEventId()).
 *   3. Если processed=true — это дубль, ack.acknowledge() и выход.
 *   4. Если processed=false — обрабатываем, затем markProcessed(eventId) → save().
 *
 * Таким образом повторное сообщение молча пропускается без побочных эффектов.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PatientEventConsumer {

    // Репозиторий для проверки и обновления статуса обработки события в outbox_event.
    private final OutboxEventRepository outboxEventRepository;

    // ObjectMapper для десериализации JSON-тела Kafka-сообщения в объект PatientEvent.
    private final ObjectMapper objectMapper;

    /**
     * Основной обработчик сообщений из топика patient-events.
     *
     * @param message - тело Kafka-сообщения в формате JSON (строка).
     *                  @Payload указывает Spring, что нужно взять именно payload,
     *                  а не заголовки или метаданные.
     * @param ack     - объект для ручного подтверждения offset.
     *                  Вызов ack.acknowledge() говорит Kafka: "этот offset обработан".
     *                  НЕ вызываем при ошибке — Kafka повторит доставку.
     */
    @KafkaListener(topics = "${kafka.topics.patient-events}",
            groupId = "hospital-patient-consumer",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(@Payload String message, Acknowledgment ack) {
        try {
            // Десериализация JSON → PatientEvent. Если JSON некорректен —
            // будет JsonProcessingException, ack не вызовется, Kafka повторит.
            PatientEvent event = objectMapper.readValue(message, PatientEvent.class);

            // Проверка идемпотентности: было ли это событие уже обработано ранее?
            // Смотрим в outbox_event по eventId — если processed=true, это дубль.
            if (isAlreadyProcessed(event.getEventId())) {
                log.warn("Duplicate patient event skipped: eventId={}", event.getEventId());
                // Дубль — ничего не делаем, но offset всё равно подтверждаем,
                // чтобы не застрять на этом сообщении вечно.
                ack.acknowledge();
                return;
            }

            // Бизнес-логика обработки события.
            // В данном проекте — демонстрационное логирование.
            // В реальной системе здесь могли бы быть: обновление кеша,
            // отправка уведомления, изменение агрегатов в другом сервисе и т.д.
            log.info("Received PatientEvent: type={}, patientId={}, status={}",
                    event.getEventType(), event.getPatientId(), event.getNewStatus());

            // Помечаем событие как обработанное: outbox_event.processed = true.
            // Это защита от повторной обработки при следующей доставке из Kafka.
            markProcessed(event.getEventId());

            // Подтверждаем offset ТОЛЬКО после успешной обработки.
            // Если markProcessed() или бизнес-логика выбросит исключение —
            // ack не будет вызван, Kafka повторит доставку.
            ack.acknowledge();

        } catch (Exception ex) {
            // Логируем ошибку, но НЕ вызываем ack.acknowledge().
            // Это означает: offset остаётся на текущей позиции.
            // При следующем poll() или перебалансировке Kafka переотправит сообщение.
            // Если ошибка повторяется N раз — сработает Dead Letter Topic (если настроен
            // в DefaultErrorHandler / SeekToCurrentErrorHandler в KafkaConfig).
            log.error("Error processing PatientEvent: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Проверяет, было ли событие с данным eventId уже обработано.
     * Ищет запись в outbox_event и проверяет флаг processed.
     *
     * @param eventId UUID события из Kafka-сообщения
     * @return true — дубль, обработку пропустить; false — новое событие, обработать
     */
    private boolean isAlreadyProcessed(String eventId) {
        return outboxEventRepository.findByEventId(eventId)
                .map(OutboxEvent::isProcessed) // берём флаг processed из найденной записи
                .orElse(false);                // запись не найдена → считаем новым событием
    }

    /**
     * Помечает событие как обработанное (processed = true).
     * Вызывается ПОСЛЕ успешного выполнения бизнес-логики, ДО вызова ack.acknowledge().
     *
     * @param eventId UUID события, которое нужно пометить
     */
    private void markProcessed(String eventId) {
        outboxEventRepository.findByEventId(eventId).ifPresent(e -> {
            e.setProcessed(true);         // выставляем флаг
            outboxEventRepository.save(e); // сохраняем изменение в БД
        });
    }
}
