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
 * Observer: listens to patient-events.
 * Idempotency guard: skips events whose eventId has already been processed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PatientEventConsumer {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${kafka.topics.patient-events}",
            groupId = "hospital-patient-consumer",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(@Payload String message, Acknowledgment ack) {
        try {
            PatientEvent event = objectMapper.readValue(message, PatientEvent.class);
            if (isAlreadyProcessed(event.getEventId())) {
                log.warn("Duplicate patient event skipped: eventId={}", event.getEventId());
                ack.acknowledge();
                return;
            }
            log.info("Received PatientEvent: type={}, patientId={}, status={}",
                    event.getEventType(), event.getPatientId(), event.getNewStatus());
            markProcessed(event.getEventId());
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Error processing PatientEvent: {}", ex.getMessage(), ex);
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
