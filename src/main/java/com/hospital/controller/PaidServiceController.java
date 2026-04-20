package com.hospital.controller;

import com.hospital.dto.request.CreatePaidServiceRequest;
import com.hospital.dto.response.PageResponse;
import com.hospital.dto.response.PaidServiceResponse;
import com.hospital.dto.response.PatientPaidServiceResponse;
import com.hospital.service.PaidServiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Paid Services", description = "Paid services catalog and assignment API")
@RequiredArgsConstructor
public class PaidServiceController {

    private final PaidServiceService paidServiceService;

    @PostMapping("/api/paid-services")
    @Operation(summary = "Create a new paid service")
    public ResponseEntity<PaidServiceResponse> create(@Valid @RequestBody CreatePaidServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paidServiceService.create(request));
    }

    @GetMapping("/api/paid-services/{id}")
    @Operation(summary = "Get paid service by ID")
    public ResponseEntity<PaidServiceResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(paidServiceService.getById(id));
    }

    @GetMapping("/api/paid-services")
    @Operation(summary = "Get all active paid services")
    public ResponseEntity<PageResponse<PaidServiceResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(paidServiceService.getAll(PageRequest.of(page, size, Sort.by("id"))));
    }

    @PostMapping("/api/patients/{patientId}/paid-services/{serviceId}")
    @Operation(summary = "Assign a paid service to a patient")
    public ResponseEntity<PatientPaidServiceResponse> assign(
            @PathVariable Long patientId,
            @PathVariable Long serviceId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paidServiceService.assignToPatient(patientId, serviceId));
    }

    @PatchMapping("/api/patients/{patientId}/paid-services/{linkId}/pay")
    @Operation(summary = "Mark a patient's service as paid")
    public ResponseEntity<PatientPaidServiceResponse> markPaid(
            @PathVariable Long patientId,
            @PathVariable Long linkId) {
        return ResponseEntity.ok(paidServiceService.markPaid(patientId, linkId));
    }
}
