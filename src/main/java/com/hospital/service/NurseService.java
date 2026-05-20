package com.hospital.service;

import com.hospital.dto.request.AdjustSupplyRequest;
import com.hospital.dto.request.CreateAssignmentRequest;
import com.hospital.dto.request.CreateSupplyRequest;
import com.hospital.dto.response.MedicalSupplyResponse;
import com.hospital.dto.response.NurseAssignmentResponse;

import java.util.List;

/**
 * Контракт модуля медсестры.
 *
 * Интерфейс разделён на два раздела:
 *   1. Управление складом (MedicalSupply) — CRUD + корректировка остатка.
 *   2. Управление назначениями (NurseAssignment) — создание, фильтрация,
 *      смена статуса, удаление, просмотр со стороны клиента.
 *
 * Реализация: NurseServiceImpl.
 * Контроллер: NurseController (ROLE_NURSE) + ClientController.getMyAssignments (ROLE_CLIENT).
 */
public interface NurseService {

    // ── Склад медикаментов ────────────────────────────────────────────────────

    /** Все позиции склада, отсортированные по категории и имени. */
    List<MedicalSupplyResponse> getAllSupplies();

    /** Создаёт новую позицию на складе. */
    MedicalSupplyResponse createSupply(CreateSupplyRequest request);

    /** Полное обновление существующей позиции по id. */
    MedicalSupplyResponse updateSupply(Long id, CreateSupplyRequest request);

    /**
     * Корректирует остаток: delta > 0 — пополнение, delta < 0 — расход.
     * Бросает BusinessRuleException, если новый остаток < 0.
     */
    MedicalSupplyResponse adjustSupply(Long id, AdjustSupplyRequest request);

    /** Физическое удаление позиции. Бросает ResourceNotFoundException, если не найдена. */
    void deleteSupply(Long id);

    // ── Назначения процедур ───────────────────────────────────────────────────

    /**
     * Список назначений с опциональным фильтром по статусу.
     * status == null или "" → все назначения.
     * status == "ACTIVE" | "DONE" | "CANCELLED" → фильтрация.
     */
    List<NurseAssignmentResponse> getAllAssignments(String status);

    /**
     * Создаёт назначение процедуры клиенту.
     * nurseUsername — имя пользователя из JWT (Authentication.getName()).
     * Проверяет, что получатель имеет роль ROLE_CLIENT.
     */
    NurseAssignmentResponse createAssignment(String nurseUsername, CreateAssignmentRequest request);

    /** Меняет статус назначения (ACTIVE → DONE | CANCELLED). */
    NurseAssignmentResponse updateAssignmentStatus(Long id, String status);

    /** Физическое удаление назначения. */
    void deleteAssignment(Long id);

    /** Назначения конкретного клиента — для отображения в личном кабинете. */
    List<NurseAssignmentResponse> getClientAssignments(Long clientUserId);
}
