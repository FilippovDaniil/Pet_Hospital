package com.hospital.repository;

import com.hospital.entity.PatientPaidService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PatientPaidServiceRepository extends JpaRepository<PatientPaidService, Long> {

    List<PatientPaidService> findByPatientId(Long patientId);

    @Query("SELECT COALESCE(SUM(pps.paidService.price), 0) FROM PatientPaidService pps WHERE pps.patient.id = :patientId")
    BigDecimal sumPriceByPatientId(@Param("patientId") Long patientId);

    @Query("""
            SELECT pps FROM PatientPaidService pps
            JOIN FETCH pps.paidService
            JOIN FETCH pps.patient p
            WHERE p.currentDoctor.id = :doctorId
            """)
    List<PatientPaidService> findByDoctorId(@Param("doctorId") Long doctorId);
}
