package com.hospital.repository;

import com.hospital.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Query("SELECT a FROM Appointment a JOIN FETCH a.doctor d JOIN FETCH d.department WHERE a.clientUser.id = :userId ORDER BY a.createdAt DESC")
    List<Appointment> findByClientUserIdWithDetails(@Param("userId") Long userId);

    @Query("SELECT a FROM Appointment a JOIN FETCH a.clientUser JOIN FETCH a.doctor d JOIN FETCH d.department WHERE a.doctor.id = :doctorId ORDER BY a.createdAt DESC")
    List<Appointment> findByDoctorIdWithDetails(@Param("doctorId") Long doctorId);

    /** Количество всех приёмов у врача (когда doctor ещё ни разу не смотрел раздел). */
    long countByDoctorId(Long doctorId);

    /** Количество приёмов, созданных ПОСЛЕ указанного момента (для бейджа новых). */
    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.doctor.id = :doctorId AND a.createdAt > :since")
    long countNewByDoctorIdSince(@Param("doctorId") Long doctorId, @Param("since") LocalDateTime since);
}
