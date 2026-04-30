package com.hospital.controller;

import com.hospital.dto.response.PaidServiceSummaryReport;
import com.hospital.dto.response.PatientResponse;
import com.hospital.dto.response.WardOccupancyReport;
import com.hospital.service.AdminService;
import com.hospital.service.strategy.DischargeType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST-контроллер административных операций — доступен только роли ADMIN.
 *
 * Доступ ограничен на уровне SecurityConfig:
 *   .requestMatchers("/api/admin/**").hasRole("ADMIN")
 * Попытка обратиться без роли ADMIN → HTTP 403 Forbidden.
 *
 * Предоставляет три группы операций:
 *   1. Отчёт по занятости палат — агрегация по всем отделениям.
 *   2. Отчёт по платным услугам — сводка затрат по пациентам.
 *   3. Полная выписка пациента — освобождение палаты, открепление врача, смена статуса.
 *
 * Ответы отчётов кэшируются в Redis (см. AdminServiceImpl, @Cacheable).
 * Выписка сбрасывает кэши (@CacheEvict), чтобы следующий отчёт был актуальным.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Administration", description = "Admin reports and patient discharge API")
public class AdminController {

    private final AdminService adminService;

    /**
     * Отчёт по занятости палат, сгруппированный по отделениям.
     * Результат кэшируется в Redis на 5 минут — дорогой запрос с агрегацией.
     */
    @GetMapping("/reports/ward-occupancy")
    @Operation(summary = "Report: ward occupancy by department")
    public ResponseEntity<List<WardOccupancyReport>> wardOccupancy() {
        return ResponseEntity.ok(adminService.getWardOccupancyReport());
    }

    /**
     * Сводный отчёт по платным услугам: количество и суммарная стоимость по каждому пациенту.
     * Также кэшируется. Инвалидируется при выписке пациента (dischargePatient).
     */
    @GetMapping("/reports/paid-services-summary")
    @Operation(summary = "Report: paid services summary by patient")
    public ResponseEntity<PaidServiceSummaryReport> paidServicesSummary() {
        return ResponseEntity.ok(adminService.getPaidServicesSummary());
    }

    /**
     * Полная выписка пациента с применением стратегии выписки.
     *
     * dischargeType — тип выписки:
     *   NORMAL   — плановая выписка домой (по умолчанию)
     *   FORCED   — принудительная административная выписка
     *   TRANSFER — перевод в другое учреждение
     *
     * Spring конвертирует строку из запроса в enum DischargeType автоматически.
     * Если передана неизвестная строка — HTTP 400 Bad Request.
     *
     * defaultValue = "NORMAL" — если параметр не передан, используется обычная выписка.
     */
    @PostMapping("/patients/{patientId}/discharge")
    @Operation(summary = "Full patient discharge (ward freed, doctor unlinked)")
    public ResponseEntity<PatientResponse> dischargePatient(
            @PathVariable Long patientId,
            @RequestParam(defaultValue = "NORMAL") DischargeType dischargeType) {
        return ResponseEntity.ok(adminService.dischargePatient(patientId, dischargeType));
    }
}
