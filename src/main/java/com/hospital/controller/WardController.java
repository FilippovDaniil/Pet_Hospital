package com.hospital.controller;

import com.hospital.dto.request.CreateWardRequest;
import com.hospital.dto.response.WardResponse;
import com.hospital.service.WardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST-контроллер для управления палатами.
 *
 * URL-структура:
 *   POST   /api/wards                              — создать палату
 *   GET    /api/wards/{id}                         — получить палату по ID
 *   GET    /api/wards                              — список всех палат
 *   POST   /api/wards/{wardId}/admit/{patientId}   — госпитализировать пациента в палату
 *   POST   /api/wards/{wardId}/discharge/{patientId} — выписать пациента из палаты
 *
 * Обратите внимание: admit и discharge используют POST (не PUT), потому что
 * это события/действия, а не обновления ресурса. В REST это дискуссионный вопрос,
 * но POST для "действий над ресурсом" — распространённая практика.
 *
 * dischargePatient здесь — выписка из палаты (освобождение места).
 * Полная выписка из больницы (смена статуса пациента) — в AdminController.
 */
@RestController
@RequestMapping("/api/wards")
@RequiredArgsConstructor
@Tag(name = "Wards", description = "Ward management API")
public class WardController {

    private final WardService wardService;

    /** Создаёт новую палату в отделении. */
    @PostMapping
    @Operation(summary = "Create a new ward")
    public ResponseEntity<WardResponse> create(@Valid @RequestBody CreateWardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(wardService.create(request));
    }

    /** Возвращает палату по ID. */
    @GetMapping("/{id}")
    @Operation(summary = "Get ward by ID")
    public ResponseEntity<WardResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(wardService.getById(id));
    }

    /**
     * Возвращает все палаты без пагинации.
     * Список небольшой (палат в больнице обычно до нескольких сотен) — полный список приемлем.
     */
    @GetMapping
    @Operation(summary = "Get all wards")
    public ResponseEntity<List<WardResponse>> getAll() {
        return ResponseEntity.ok(wardService.getAll());
    }

    /**
     * Госпитализирует пациента в палату.
     * Сервис проверяет: палата не переполнена, пациент ещё не в другой палате.
     * После успешной госпитализации возвращает обновлённую палату (с увеличенным счётчиком).
     */
    @PostMapping("/{wardId}/admit/{patientId}")
    @Operation(summary = "Admit patient to ward")
    public ResponseEntity<WardResponse> admitPatient(
            @PathVariable Long wardId,
            @PathVariable Long patientId) {
        return ResponseEntity.ok(wardService.admitPatient(wardId, patientId));
    }

    /**
     * Выписывает пациента из конкретной палаты (освобождает койку).
     * НЕ меняет статус пациента — только убирает его из палаты.
     * Для полной выписки из больницы — используйте AdminController.dischargePatient().
     */
    @PostMapping("/{wardId}/discharge/{patientId}")
    @Operation(summary = "Discharge patient from ward")
    public ResponseEntity<WardResponse> dischargePatient(
            @PathVariable Long wardId,
            @PathVariable Long patientId) {
        return ResponseEntity.ok(wardService.dischargePatientFromWard(wardId, patientId));
    }
}
