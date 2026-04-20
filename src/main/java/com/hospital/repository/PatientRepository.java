package com.hospital.repository;

import com.hospital.entity.Patient;
import com.hospital.entity.PatientStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Page<Patient> findAllByActiveTrue(Pageable pageable);

    Optional<Patient> findByIdAndActiveTrue(Long id);

    boolean existsBySnilsAndActiveTrue(String snils);

    @Query("SELECT COUNT(p) FROM Patient p WHERE p.currentDoctor.id = :doctorId AND p.active = true AND p.status = 'TREATMENT'")
    long countActivePatientsByDoctorId(@Param("doctorId") Long doctorId);

    @Query("SELECT p FROM Patient p WHERE p.currentDoctor.id = :doctorId AND p.active = true")
    Page<Patient> findByDoctorId(@Param("doctorId") Long doctorId, Pageable pageable);

    @Query("SELECT p FROM Patient p WHERE p.currentWard.id = :wardId AND p.active = true AND p.status = 'TREATMENT'")
    java.util.List<Patient> findCurrentPatientsInWard(@Param("wardId") Long wardId);
}
