package com.hospital.controller;

import com.hospital.dto.request.CreatePatientRequest;
import com.hospital.dto.request.UpdatePatientRequest;
import com.hospital.dto.response.PageResponse;
import com.hospital.dto.response.PatientPaidServiceResponse;
import com.hospital.dto.response.PatientResponse;
import com.hospital.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
@Tag(name = "Patients", description = "Patient management API")
public class PatientController {

    private final PatientService patientService;

    @PostMapping
    @Operation(summary = "Register a new patient")
    public ResponseEntity<PatientResponse> create(@Valid @RequestBody CreatePatientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(patientService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get patient by ID")
    public ResponseEntity<PatientResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(patientService.getById(id));
    }

    @GetMapping
    @Operation(summary = "Get all active patients (paginated)")
    public ResponseEntity<PageResponse<PatientResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(patientService.getAll(PageRequest.of(page, size, Sort.by("id"))));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update patient data")
    public ResponseEntity<PatientResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePatientRequest request) {
        return ResponseEntity.ok(patientService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete patient")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        patientService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{patientId}/assign-doctor/{doctorId}")
    @Operation(summary = "Assign a doctor to patient")
    public ResponseEntity<PatientResponse> assignDoctor(
            @PathVariable Long patientId,
            @PathVariable Long doctorId) {
        return ResponseEntity.ok(patientService.assignDoctor(patientId, doctorId));
    }

    @GetMapping("/{patientId}/services")
    @Operation(summary = "Get all paid services for patient")
    public ResponseEntity<List<PatientPaidServiceResponse>> getServices(@PathVariable Long patientId) {
        return ResponseEntity.ok(patientService.getServices(patientId));
    }
}
