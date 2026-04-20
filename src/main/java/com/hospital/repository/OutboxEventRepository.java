package com.hospital.repository;

import com.hospital.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    boolean existsByEventId(String eventId);

    Optional<OutboxEvent> findByEventId(String eventId);
}
