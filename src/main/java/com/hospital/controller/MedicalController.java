package com.hospital.controller;

import com.hospital.dto.request.CreateMedicalDocumentRequest;
import com.hospital.dto.request.CreatePatientNoteRequest;
import com.hospital.dto.response.MedicalDocumentResponse;
import com.hospital.dto.response.PatientHistoryResponse;
import com.hospital.dto.response.PatientNoteResponse;
import com.hospital.entity.User;
import com.hospital.repository.UserRepository;
import com.hospital.service.MedicalService;
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
@RequestMapping("/api/medical")
@RequiredArgsConstructor
@Tag(name = "Medical", description = "Медицинские документы и история пациента")
public class MedicalController {

    private final MedicalService medicalService;
    private final UserRepository userRepository;

    // ─────────────────────────────────────────────
    // DOCUMENTS — DOCTOR
    // ─────────────────────────────────────────────

    @PostMapping("/documents")
    @Operation(summary = "Врач: создать медицинский документ для пациента")
    public ResponseEntity<MedicalDocumentResponse> createDocument(
            @RequestBody @Valid CreateMedicalDocumentRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(medicalService.createDocument(request, currentUser(auth)));
    }

    @GetMapping("/documents/patient/{patientId}")
    @Operation(summary = "Врач: все документы конкретного пациента")
    public List<MedicalDocumentResponse> getPatientDocuments(
            @PathVariable Long patientId,
            Authentication auth) {
        return medicalService.getPatientDocuments(patientId, currentUser(auth));
    }

    // ─────────────────────────────────────────────
    // DOCUMENTS — CLIENT
    // ─────────────────────────────────────────────

    @GetMapping("/documents/my")
    @Operation(summary = "Клиент: свои медицинские документы")
    public List<MedicalDocumentResponse> getMyDocuments(Authentication auth) {
        return medicalService.getMyDocuments(currentUser(auth));
    }

    // ─────────────────────────────────────────────
    // NOTES — DOCTOR
    // ─────────────────────────────────────────────

    @PostMapping("/notes")
    @Operation(summary = "Врач: добавить заметку / диагноз пациенту")
    public ResponseEntity<PatientNoteResponse> createNote(
            @RequestBody @Valid CreatePatientNoteRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(medicalService.createNote(request, currentUser(auth)));
    }

    // ─────────────────────────────────────────────
    // HISTORY
    // ─────────────────────────────────────────────

    @GetMapping("/history/patient/{patientId}")
    @Operation(summary = "Врач: полная история пациента (заметки + документы)")
    public PatientHistoryResponse getPatientHistory(
            @PathVariable Long patientId,
            Authentication auth) {
        return medicalService.getPatientHistory(patientId, currentUser(auth));
    }

    @GetMapping("/history/my")
    @Operation(summary = "Клиент: своя медицинская история")
    public PatientHistoryResponse getMyHistory(Authentication auth) {
        return medicalService.getMyHistory(currentUser(auth));
    }

    // ─────────────────────────────────────────────

    private User currentUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName()).orElseThrow();
    }
}
