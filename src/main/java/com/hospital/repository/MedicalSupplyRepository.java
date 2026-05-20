package com.hospital.repository;

import com.hospital.entity.MedicalSupply;
import com.hospital.entity.SupplyCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicalSupplyRepository extends JpaRepository<MedicalSupply, Long> {

    List<MedicalSupply> findAllByOrderByCategoryAscNameAsc();

    List<MedicalSupply> findByCategoryOrderByNameAsc(SupplyCategory category);
}
