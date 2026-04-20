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

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Administration", description = "Admin reports and patient discharge API")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/reports/ward-occupancy")
    @Operation(summary = "Report: ward occupancy by department")
    public ResponseEntity<List<WardOccupancyReport>> wardOccupancy() {
        return ResponseEntity.ok(adminService.getWardOccupancyReport());
    }

    @GetMapping("/reports/paid-services-summary")
    @Operation(summary = "Report: paid services summary by patient")
    public ResponseEntity<PaidServiceSummaryReport> paidServicesSummary() {
        return ResponseEntity.ok(adminService.getPaidServicesSummary());
    }

    @PostMapping("/patients/{patientId}/discharge")
    @Operation(summary = "Full patient discharge (ward freed, doctor unlinked)")
    public ResponseEntity<PatientResponse> dischargePatient(
            @PathVariable Long patientId,
            @RequestParam(defaultValue = "NORMAL") DischargeType dischargeType) {
        return ResponseEntity.ok(adminService.dischargePatient(patientId, dischargeType));
    }
}
