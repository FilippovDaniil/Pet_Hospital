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

@RestController
@RequestMapping("/api/nurse")
@RequiredArgsConstructor
@Tag(name = "Nurse", description = "Nurse module: supplies & assignments")
public class NurseController {

    private final NurseService nurseService;

    // ── Supplies ──────────────────────────────────────────────────────────────

    @GetMapping("/supplies")
    @Operation(summary = "Список всех медикаментов/расходников")
    public ResponseEntity<List<MedicalSupplyResponse>> getSupplies() {
        return ResponseEntity.ok(nurseService.getAllSupplies());
    }

    @PostMapping("/supplies")
    @Operation(summary = "Добавить позицию на склад")
    public ResponseEntity<MedicalSupplyResponse> createSupply(@Valid @RequestBody CreateSupplyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(nurseService.createSupply(request));
    }

    @PutMapping("/supplies/{id}")
    @Operation(summary = "Редактировать позицию склада")
    public ResponseEntity<MedicalSupplyResponse> updateSupply(
            @PathVariable Long id,
            @Valid @RequestBody CreateSupplyRequest request) {
        return ResponseEntity.ok(nurseService.updateSupply(id, request));
    }

    @PatchMapping("/supplies/{id}/adjust")
    @Operation(summary = "Изменить остаток (delta: +пополнение / -расход)")
    public ResponseEntity<MedicalSupplyResponse> adjustSupply(
            @PathVariable Long id,
            @RequestBody AdjustSupplyRequest request) {
        return ResponseEntity.ok(nurseService.adjustSupply(id, request));
    }

    @DeleteMapping("/supplies/{id}")
    @Operation(summary = "Удалить позицию склада")
    public ResponseEntity<Void> deleteSupply(@PathVariable Long id) {
        nurseService.deleteSupply(id);
        return ResponseEntity.noContent().build();
    }

    // ── Assignments ───────────────────────────────────────────────────────────

    @GetMapping("/assignments")
    @Operation(summary = "Список назначений (опционально ?status=ACTIVE|DONE|CANCELLED)")
    public ResponseEntity<List<NurseAssignmentResponse>> getAssignments(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(nurseService.getAllAssignments(status));
    }

    @PostMapping("/assignments")
    @Operation(summary = "Создать назначение клиенту")
    public ResponseEntity<NurseAssignmentResponse> createAssignment(
            Authentication auth,
            @Valid @RequestBody CreateAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(nurseService.createAssignment(auth.getName(), request));
    }

    @PatchMapping("/assignments/{id}/status")
    @Operation(summary = "Обновить статус назначения (ACTIVE|DONE|CANCELLED)")
    public ResponseEntity<NurseAssignmentResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return ResponseEntity.ok(nurseService.updateAssignmentStatus(id, status));
    }

    @DeleteMapping("/assignments/{id}")
    @Operation(summary = "Удалить назначение")
    public ResponseEntity<Void> deleteAssignment(@PathVariable Long id) {
        nurseService.deleteAssignment(id);
        return ResponseEntity.noContent().build();
    }
}
