package com.hospital.repository;

import com.hospital.entity.PaidService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaidServiceRepository extends JpaRepository<PaidService, Long> {

    Page<PaidService> findAllByActiveTrue(Pageable pageable);

    Optional<PaidService> findByIdAndActiveTrue(Long id);
}
