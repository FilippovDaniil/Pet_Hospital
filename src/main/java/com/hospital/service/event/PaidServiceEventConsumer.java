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
 * Observer: listens to paid-service-events and simulates forwarding to a billing system.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaidServiceEventConsumer {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${kafka.topics.paid-service-events}",
            groupId = "hospital-billing-consumer",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(@Payload String message, Acknowledgment ack) {
        try {
            PaidServiceEvent event = objectMapper.readValue(message, PaidServiceEvent.class);
            if (isAlreadyProcessed(event.getEventId())) {
                log.warn("Duplicate paid-service event skipped: eventId={}", event.getEventId());
                ack.acknowledge();
                return;
            }
            // Billing integration stub
            log.info("[BILLING STUB] PaidServiceEvent: patient={}, service={}, price={}",
                    event.getPatientName(), event.getServiceName(), event.getPrice());
            markProcessed(event.getEventId());
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Error processing PaidServiceEvent: {}", ex.getMessage(), ex);
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
