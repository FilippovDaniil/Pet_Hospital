package com.hospital.repository;

import com.hospital.entity.AssignmentStatus;
import com.hospital.entity.NurseAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Репозиторий назначений процедур.
 *
 * Все три метода используют JOIN FETCH — обязательный приём для избежания N+1.
 * NurseAssignment содержит два LAZY-поля (clientUser, nurseUser). Без JOIN FETCH
 * каждое обращение к clientUser.getFullName() внутри toAssignmentResponse()
 * вызвало бы отдельный SELECT — O(N) запросов на N назначений.
 * JOIN FETCH сворачивает это в один запрос.
 */
public interface NurseAssignmentRepository extends JpaRepository<NurseAssignment, Long> {

    /**
     * Все назначения с загруженными clientUser и nurseUser, сортировка по дате создания (новые первые).
     *
     * JOIN FETCH a.clientUser JOIN FETCH a.nurseUser — оба пользователя загружаются
     * одним JOIN-ом, без ленивой дозагрузки.
     * ORDER BY a.createdAt DESC — свежие назначения вверху списка.
     */
    @Query("SELECT a FROM NurseAssignment a JOIN FETCH a.clientUser JOIN FETCH a.nurseUser ORDER BY a.createdAt DESC")
    List<NurseAssignment> findAllWithUsers();

    /**
     * Назначения с фильтром по статусу (ACTIVE / DONE / CANCELLED).
     *
     * :status — именованный параметр JPQL; @Param("status") связывает его с аргументом метода.
     * AssignmentStatus (enum) сравнивается по значению благодаря @Enumerated(STRING) на поле.
     */
    @Query("SELECT a FROM NurseAssignment a JOIN FETCH a.clientUser JOIN FETCH a.nurseUser WHERE a.status = :status ORDER BY a.createdAt DESC")
    List<NurseAssignment> findByStatusWithUsers(@Param("status") AssignmentStatus status);

    /**
     * Назначения конкретного клиента — для клиентского кабинета (GET /api/client/my-assignments).
     *
     * JOIN FETCH a.nurseUser — клиентский кабинет показывает имя медсестры.
     * clientUser не нужен в JOIN FETCH, т.к. он уже известен из условия фильтра
     * (a.clientUser.id = :clientUserId) — загрузка clientUser здесь избыточна.
     */
    @Query("SELECT a FROM NurseAssignment a JOIN FETCH a.nurseUser WHERE a.clientUser.id = :clientUserId ORDER BY a.createdAt DESC")
    List<NurseAssignment> findByClientUserId(@Param("clientUserId") Long clientUserId);
}
