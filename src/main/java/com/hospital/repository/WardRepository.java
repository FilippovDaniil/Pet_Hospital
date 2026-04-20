package com.hospital.repository;

import com.hospital.entity.Ward;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WardRepository extends JpaRepository<Ward, Long> {

    Page<Ward> findByDepartmentId(Long departmentId, Pageable pageable);

    @Query("SELECT w FROM Ward w WHERE w.department.id = :deptId AND w.currentOccupancy < w.capacity")
    List<Ward> findAvailableWardsByDepartment(@Param("deptId") Long departmentId);

    @Query("SELECT w FROM Ward w LEFT JOIN FETCH w.department ORDER BY w.department.id, w.wardNumber")
    List<Ward> findAllWithDepartment();
}
