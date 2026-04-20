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
 * Central Domain Event publisher.
 * Each publish call sends a Kafka message AND persists an outbox record
 * within the same JPA transaction so both succeed or both roll back.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

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

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishPatientEvent(PatientEvent event) {
        send(patientTopic, event.getEventId(), event, "PatientEvent");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishAdmissionEvent(AdmissionEvent event) {
        send(admissionTopic, event.getEventId(), event, "AdmissionEvent");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishPaidServiceEvent(PaidServiceEvent event) {
        send(paidServiceTopic, event.getEventId(), event, "PaidServiceEvent");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishDoctorEvent(DoctorEvent event) {
        send(doctorTopic, event.getEventId(), event, "DoctorEvent");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishDepartmentEvent(DepartmentEvent event) {
        send(departmentTopic, event.getEventId(), event, "DepartmentEvent");
    }

    private void send(String topic, String key, Object payload, String eventType) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            // Save outbox record within the same JPA transaction for idempotency tracking
            outboxEventRepository.save(OutboxEvent.builder()
                    .eventId(key)
                    .eventType(eventType)
                    .payload(json)
                    .build());

            kafkaTemplate.send(topic, key, payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to send {} to topic {}: {}", eventType, topic, ex.getMessage());
                        } else {
                            log.debug("Sent {} to topic {} partition={} offset={}",
                                    eventType, topic,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event " + eventType, e);
        }
    }
}
