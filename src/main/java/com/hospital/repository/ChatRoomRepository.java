package com.hospital.repository;

import com.hospital.entity.ChatRoom;
import com.hospital.entity.ChatRoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    /** Все комнаты клиента (поддержка + врачи). */
    @Query("SELECT r FROM ChatRoom r JOIN FETCH r.clientUser WHERE r.clientUser.id = :clientId ORDER BY r.createdAt DESC")
    List<ChatRoom> findByClientUserId(@Param("clientId") Long clientId);

    /** Найти SUPPORT-комнату конкретного клиента. */
    Optional<ChatRoom> findByTypeAndClientUserId(ChatRoomType type, Long clientUserId);

    /** Найти DOCTOR_CLIENT-комнату для пары клиент-врач. */
    Optional<ChatRoom> findByTypeAndClientUserIdAndStaffUserId(ChatRoomType type, Long clientUserId, Long staffUserId);

    /** Все SUPPORT-комнаты для администраторов. */
    @Query("SELECT r FROM ChatRoom r JOIN FETCH r.clientUser WHERE r.type = 'SUPPORT' ORDER BY r.createdAt DESC")
    List<ChatRoom> findAllSupportRooms();

    /** Все DOCTOR_CLIENT-комнаты, где врач = staffUser. */
    @Query("SELECT r FROM ChatRoom r JOIN FETCH r.clientUser WHERE r.type = 'DOCTOR_CLIENT' AND r.staffUser.id = :staffId ORDER BY r.createdAt DESC")
    List<ChatRoom> findDoctorRoomsByStaffUserId(@Param("staffId") Long staffId);
}
