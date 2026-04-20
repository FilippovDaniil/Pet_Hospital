package com.hospital.repository;

import com.hospital.entity.Doctor;
import com.hospital.entity.Specialty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    Page<Doctor> findAllByActiveTrue(Pageable pageable);

    Optional<Doctor> findByIdAndActiveTrue(Long id);

    Page<Doctor> findBySpecialtyAndActiveTrue(Specialty specialty, Pageable pageable);
}
