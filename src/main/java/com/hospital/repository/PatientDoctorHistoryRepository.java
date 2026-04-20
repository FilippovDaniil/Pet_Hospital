package com.hospital.repository;

import com.hospital.entity.PatientDoctorHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientDoctorHistoryRepository extends JpaRepository<PatientDoctorHistory, Long> {

    Optional<PatientDoctorHistory> findByPatientIdAndAssignedToIsNull(Long patientId);

    java.util.List<PatientDoctorHistory> findByPatientIdOrderByAssignedFromDesc(Long patientId);
}
