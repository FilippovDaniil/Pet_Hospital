package com.hospital.repository;

import com.hospital.entity.ClientServiceOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClientServiceOrderRepository extends JpaRepository<ClientServiceOrder, Long> {

    @Query("SELECT o FROM ClientServiceOrder o JOIN FETCH o.paidService WHERE o.clientUser.id = :userId ORDER BY o.createdAt DESC")
    List<ClientServiceOrder> findByClientUserIdWithDetails(@Param("userId") Long userId);
}
