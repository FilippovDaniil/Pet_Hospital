package com.hospital.controller;

import com.hospital.dto.request.AdjustSupplyRequest;
import com.hospital.dto.request.CreateAssignmentRequest;
import com.hospital.dto.request.CreateSupplyRequest;
import com.hospital.dto.response.MedicalSupplyResponse;
import com.hospital.dto.response.NurseAssignmentResponse;
import com.hospital.service.NurseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST-контроллер модуля медсестры.
 *
 * Все эндпоинты защищены правилом hasRole("NURSE") в SecurityConfig.
 * Исключение: GET /api/client/my-assignments (ClientController) — для клиентов.
 *
 * Два раздела:
 *   /api/nurse/supplies    — управление складом медикаментов.
 *   /api/nurse/assignments — управление назначениями процедур.
 */
@RestController
@RequestMapping("/api/nurse")
@RequiredArgsConstructor
@Tag(name = "Nurse", description = "Nurse module: supplies & assignments")
public class NurseController {

    private final NurseService nurseService;

    // ── Supplies ──────────────────────────────────────────────────────────────

    /**
     * GET /api/nurse/supplies
     * Список всех позиций склада, отсортированных по категории и имени.
     */
    @GetMapping("/supplies")
    @Operation(summary = "Список всех медикаментов/расходников")
    public ResponseEntity<List<MedicalSupplyResponse>> getSupplies() {
        return ResponseEntity.ok(nurseService.getAllSupplies());
    }

    /**
     * POST /api/nurse/supplies
     * Добавляет новую позицию на склад. Возвращает HTTP 201 Created.
     * @Valid — запускает Bean Validation на полях CreateSupplyRequest.
     */
    @PostMapping("/supplies")
    @Operation(summary = "Добавить позицию на склад")
    public ResponseEntity<MedicalSupplyResponse> createSupply(@Valid @RequestBody CreateSupplyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(nurseService.createSupply(request));
    }

    /**
     * PUT /api/nurse/supplies/{id}
     * Полное обновление позиции склада по id (все поля перезаписываются).
     * HTTP 200 OK + обновлённый DTO в теле.
     */
    @PutMapping("/supplies/{id}")
    @Operation(summary = "Редактировать позицию склада")
    public ResponseEntity<MedicalSupplyResponse> updateSupply(
            @PathVariable Long id,
            @Valid @RequestBody CreateSupplyRequest request) {
        return ResponseEntity.ok(nurseService.updateSupply(id, request));
    }

    /**
     * PATCH /api/nurse/supplies/{id}/adjust
     * Частичное обновление: корректирует остаток на delta.
     * delta > 0 — пополнение, delta < 0 — расход.
     * Возвращает HTTP 400 (BusinessRuleException), если новый остаток < 0.
     */
    @PatchMapping("/supplies/{id}/adjust")
    @Operation(summary = "Изменить остаток (delta: +пополнение / -расход)")
    public ResponseEntity<MedicalSupplyResponse> adjustSupply(
            @PathVariable Long id,
            @RequestBody AdjustSupplyRequest request) {
        return ResponseEntity.ok(nurseService.adjustSupply(id, request));
    }

    /**
     * DELETE /api/nurse/supplies/{id}
     * Физическое удаление позиции склада. HTTP 204 No Content.
     */
    @DeleteMapping("/supplies/{id}")
    @Operation(summary = "Удалить позицию склада")
    public ResponseEntity<Void> deleteSupply(@PathVariable Long id) {
        nurseService.deleteSupply(id);
        return ResponseEntity.noContent().build();
    }

    // ── Assignments ───────────────────────────────────────────────────────────

    /**
     * GET /api/nurse/assignments?status=ACTIVE
     * Список назначений с опциональным фильтром по статусу.
     * status — необязательный параметр: ACTIVE | DONE | CANCELLED.
     * Если не передан — возвращаются все назначения.
     */
    @GetMapping("/assignments")
    @Operation(summary = "Список назначений (опционально ?status=ACTIVE|DONE|CANCELLED)")
    public ResponseEntity<List<NurseAssignmentResponse>> getAssignments(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(nurseService.getAllAssignments(status));
    }

    /**
     * POST /api/nurse/assignments
     * Создаёт назначение процедуры клиенту.
     * Authentication.getName() — username медсестры из JWT, передаётся в сервис
     * для привязки назначения к конкретной медсестре.
     * HTTP 201 Created + DTO нового назначения.
     */
    @PostMapping("/assignments")
    @Operation(summary = "Создать назначение клиенту")
    public ResponseEntity<NurseAssignmentResponse> createAssignment(
            Authentication auth,                        // JWT токен текущей медсестры
            @Valid @RequestBody CreateAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(nurseService.createAssignment(auth.getName(), request));
    }

    /**
     * PATCH /api/nurse/assignments/{id}/status?status=DONE
     * Обновляет статус назначения: ACTIVE → DONE | CANCELLED.
     * status передаётся как query-параметр (не в теле), т.к. это минимальное изменение.
     */
    @PatchMapping("/assignments/{id}/status")
    @Operation(summary = "Обновить статус назначения (ACTIVE|DONE|CANCELLED)")
    public ResponseEntity<NurseAssignmentResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return ResponseEntity.ok(nurseService.updateAssignmentStatus(id, status));
    }

    /**
     * DELETE /api/nurse/assignments/{id}
     * Физическое удаление назначения. HTTP 204 No Content.
     */
    @DeleteMapping("/assignments/{id}")
    @Operation(summary = "Удалить назначение")
    public ResponseEntity<Void> deleteAssignment(@PathVariable Long id) {
        nurseService.deleteAssignment(id);
        return ResponseEntity.noContent().build();
    }
}
