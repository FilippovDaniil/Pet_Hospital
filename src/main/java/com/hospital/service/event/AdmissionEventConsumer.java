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
 * Observer: listens to admission-events (ward check-in / check-out).
 * Demonstrates eventual-consistency: logs the admission action received via Kafka.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdmissionEventConsumer {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${kafka.topics.admission-events}",
            groupId = "hospital-admission-consumer",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(@Payload String message, Acknowledgment ack) {
        try {
            AdmissionEvent event = objectMapper.readValue(message, AdmissionEvent.class);
            if (isAlreadyProcessed(event.getEventId())) {
                log.warn("Duplicate admission event skipped: eventId={}", event.getEventId());
                ack.acknowledge();
                return;
            }
            log.info("Received AdmissionEvent: action={}, patientId={}, ward={} ({})",
                    event.getAction(), event.getPatientId(), event.getWardId(), event.getWardNumber());
            markProcessed(event.getEventId());
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Error processing AdmissionEvent: {}", ex.getMessage(), ex);
        }
    }

    private boolean isAlreadyProcessed(String eventId) {
        return outboxEventRepository.findByEventId(eventId)
                .map(OutboxEvent::isProcessed)
                .orElse(false);
    }

    private void markProcessed(String eventId) {
        outboxEventRepository.findByEventId(eventId).ifPresent(e -> {
            e.setProcessed(true);
            outboxEventRepository.save(e);
        });
    }
}
