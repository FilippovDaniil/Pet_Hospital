package com.hospital.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.transaction.KafkaTransactionManager;

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
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
