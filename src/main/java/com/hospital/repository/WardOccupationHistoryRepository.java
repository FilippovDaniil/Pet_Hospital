package com.hospital.repository;

import com.hospital.entity.WardOccupationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WardOccupationHistoryRepository extends JpaRepository<WardOccupationHistory, Long> {

    Optional<WardOccupationHistory> findByPatientIdAndDischargedAtIsNull(Long patientId);
}
