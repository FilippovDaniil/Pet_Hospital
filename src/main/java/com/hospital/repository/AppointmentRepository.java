package com.hospital.repository;

import com.hospital.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Query("SELECT a FROM Appointment a JOIN FETCH a.doctor d JOIN FETCH d.department WHERE a.clientUser.id = :userId ORDER BY a.createdAt DESC")
    List<Appointment> findByClientUserIdWithDetails(@Param("userId") Long userId);
}
