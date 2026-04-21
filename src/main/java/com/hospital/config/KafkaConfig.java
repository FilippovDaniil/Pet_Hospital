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

@Slf4j
@Configuration
public class KafkaConfig {

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

    @Value("${kafka.partitions:3}")
    private int partitions;

    @Value("${kafka.replication-factor:1}")
    private short replicationFactor;

    // ---- Topics ----

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

    // ---- Dead Letter Topics ----

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

    // ---- DLQ Recoverer ----

    @Bean
    public ConsumerRecordRecoverer dltRecoverer() {
        return (record, exception) ->
            log.error("DLQ: failed to process record from topic={}, partition={}, offset={}, key={}, error={}",
                record.topic(), record.partition(), record.offset(), record.key(),
                exception.getMessage());
    }

    // ---- Transaction Manager ----

    @Bean
    public KafkaTransactionManager<String, Object> kafkaTransactionManager(
            ProducerFactory<String, Object> producerFactory) {
        return new KafkaTransactionManager<>(producerFactory);
    }

    // ---- Listener container factory with manual ack ----
    // Consumer value-deserializer is StringDeserializer; consumers parse JSON manually via ObjectMapper.

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            dltRecoverer(), new FixedBackOff(1000L, 2));

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
