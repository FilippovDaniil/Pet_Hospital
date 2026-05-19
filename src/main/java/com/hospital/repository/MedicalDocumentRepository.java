package com.hospital.repository;

import com.hospital.entity.MedicalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MedicalDocumentRepository extends JpaRepository<MedicalDocument, Long> {

    /** Все активные документы пациента (для врача). */
    @Query("SELECT d FROM MedicalDocument d JOIN FETCH d.doctor WHERE d.patient.id = :patientId ORDER BY d.issuedAt DESC")
    List<MedicalDocument> findByPatientId(@Param("patientId") Long patientId);

    /** Документы пациента, привязанного к клиентскому аккаунту (для клиентского портала). */
    @Query("SELECT d FROM MedicalDocument d JOIN FETCH d.doctor WHERE d.patient.clientUser.id = :clientUserId AND d.active = true ORDER BY d.issuedAt DESC")
    List<MedicalDocument> findByPatientClientUserId(@Param("clientUserId") Long clientUserId);

    /** Документы, созданные конкретным врачом. */
    @Query("SELECT d FROM MedicalDocument d JOIN FETCH d.patient WHERE d.doctor.id = :doctorId ORDER BY d.issuedAt DESC")
    List<MedicalDocument> findByDoctorId(@Param("doctorId") Long doctorId);
}
