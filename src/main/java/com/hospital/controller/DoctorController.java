package com.hospital.controller;

import com.hospital.dto.request.CreateDoctorRequest;
import com.hospital.dto.request.UpdateDoctorRequest;
import com.hospital.dto.response.DoctorResponse;
import com.hospital.dto.response.PageResponse;
import com.hospital.dto.response.PatientResponse;
import com.hospital.entity.Specialty;
import com.hospital.service.DoctorService;
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
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
@Tag(name = "Doctors", description = "Doctor management API")
public class DoctorController {

    private final DoctorService doctorService;

    @PostMapping
    @Operation(summary = "Create a new doctor")
    public ResponseEntity<DoctorResponse> create(@Valid @RequestBody CreateDoctorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(doctorService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get doctor by ID")
    public ResponseEntity<DoctorResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(doctorService.getById(id));
    }

    @GetMapping
    @Operation(summary = "Get all doctors, optionally filtered by specialty")
    public ResponseEntity<PageResponse<DoctorResponse>> getAll(
            @RequestParam(required = false) Specialty specialty,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("id"));
        if (specialty != null) {
            return ResponseEntity.ok(doctorService.getBySpecialty(specialty, pageable));
        }
        return ResponseEntity.ok(doctorService.getAll(pageable));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update doctor data")
    public ResponseEntity<DoctorResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDoctorRequest request) {
        return ResponseEntity.ok(doctorService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete doctor")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        doctorService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/patients")
    @Operation(summary = "Get all patients of a doctor")
    public ResponseEntity<PageResponse<PatientResponse>> getPatients(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(doctorService.getPatients(id, PageRequest.of(page, size, Sort.by("id"))));
    }
}
