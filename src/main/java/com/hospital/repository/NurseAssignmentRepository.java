package com.hospital.repository;

import com.hospital.entity.AssignmentStatus;
import com.hospital.entity.NurseAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NurseAssignmentRepository extends JpaRepository<NurseAssignment, Long> {

    @Query("SELECT a FROM NurseAssignment a JOIN FETCH a.clientUser JOIN FETCH a.nurseUser ORDER BY a.createdAt DESC")
    List<NurseAssignment> findAllWithUsers();

    @Query("SELECT a FROM NurseAssignment a JOIN FETCH a.clientUser JOIN FETCH a.nurseUser WHERE a.status = :status ORDER BY a.createdAt DESC")
    List<NurseAssignment> findByStatusWithUsers(@Param("status") AssignmentStatus status);

    @Query("SELECT a FROM NurseAssignment a JOIN FETCH a.nurseUser WHERE a.clientUser.id = :clientUserId ORDER BY a.createdAt DESC")
    List<NurseAssignment> findByClientUserId(@Param("clientUserId") Long clientUserId);
}
