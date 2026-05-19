package com.hospital.repository;

import com.hospital.entity.PatientNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PatientNoteRepository extends JpaRepository<PatientNote, Long> {

    /** Все заметки по пациенту (для врача). */
    @Query("SELECT n FROM PatientNote n JOIN FETCH n.doctor WHERE n.patient.id = :patientId ORDER BY n.createdAt DESC")
    List<PatientNote> findByPatientId(@Param("patientId") Long patientId);

    /** Только видимые пациенту заметки (для клиентского портала). */
    @Query("SELECT n FROM PatientNote n JOIN FETCH n.doctor WHERE n.patient.clientUser.id = :clientUserId AND n.visibleToClient = true ORDER BY n.createdAt DESC")
    List<PatientNote> findVisibleByClientUserId(@Param("clientUserId") Long clientUserId);
}
