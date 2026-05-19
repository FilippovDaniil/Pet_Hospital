package com.hospital.service.impl;

import com.hospital.dto.request.CreateMedicalDocumentRequest;
import com.hospital.dto.request.CreatePatientNoteRequest;
import com.hospital.dto.response.MedicalDocumentResponse;
import com.hospital.dto.response.PatientHistoryResponse;
import com.hospital.dto.response.PatientNoteResponse;
import com.hospital.entity.*;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.repository.*;
import com.hospital.service.MedicalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MedicalServiceImpl implements MedicalService {

    private final MedicalDocumentRepository medicalDocumentRepository;
    private final PatientNoteRepository patientNoteRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;

    @Override
    @Transactional
    public MedicalDocumentResponse createDocument(CreateMedicalDocumentRequest request, User doctorUser) {
        Doctor doctor = doctorRepository.findByLinkedUserIdAndActiveTrue(doctorUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Профиль врача для пользователя не найден"));
        Patient patient = patientRepository.findByIdAndActiveTrue(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Пациент #" + request.getPatientId() + " не найден"));

        MedicalDocument doc = medicalDocumentRepository.save(
                MedicalDocument.builder()
                        .patient(patient)
                        .doctor(doctor)
                        .type(request.getType())
                        .title(request.getTitle())
                        .content(request.getContent())
                        .validUntil(request.getValidUntil())
                        .build()
        );
        return toDocumentResponse(doc);
    }

    @Override
    public List<MedicalDocumentResponse> getPatientDocuments(Long patientId, User doctorUser) {
        return medicalDocumentRepository.findByPatientId(patientId).stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    @Override
    public List<MedicalDocumentResponse> getMyDocuments(User clientUser) {
        return medicalDocumentRepository.findByPatientClientUserId(clientUser.getId()).stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    @Override
    @Transactional
    public PatientNoteResponse createNote(CreatePatientNoteRequest request, User doctorUser) {
        Doctor doctor = doctorRepository.findByLinkedUserIdAndActiveTrue(doctorUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Профиль врача для пользователя не найден"));
        Patient patient = patientRepository.findByIdAndActiveTrue(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Пациент #" + request.getPatientId() + " не найден"));

        PatientNote note = patientNoteRepository.save(
                PatientNote.builder()
                        .patient(patient)
                        .doctor(doctor)
                        .type(request.getType())
                        .content(request.getContent())
                        .visibleToClient(request.isVisibleToClient())
                        .build()
        );
        return toNoteResponse(note);
    }

    @Override
    public PatientHistoryResponse getPatientHistory(Long patientId, User doctorUser) {
        Patient patient = patientRepository.findByIdAndActiveTrue(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Пациент #" + patientId + " не найден"));

        List<PatientNoteResponse> notes = patientNoteRepository.findByPatientId(patientId).stream()
                .map(this::toNoteResponse).toList();
        List<MedicalDocumentResponse> docs = medicalDocumentRepository.findByPatientId(patientId).stream()
                .map(this::toDocumentResponse).toList();

        return PatientHistoryResponse.builder()
                .patientId(patient.getId())
                .patientName(patient.getFullName())
                .notes(notes)
                .documents(docs)
                .build();
    }

    @Override
    public PatientHistoryResponse getMyHistory(User clientUser) {
        List<PatientNoteResponse> notes = patientNoteRepository.findVisibleByClientUserId(clientUser.getId()).stream()
                .map(this::toNoteResponse).toList();
        List<MedicalDocumentResponse> docs = medicalDocumentRepository.findByPatientClientUserId(clientUser.getId()).stream()
                .map(this::toDocumentResponse).toList();

        String patientName = notes.isEmpty() && docs.isEmpty() ? ""
                : (!notes.isEmpty() ? notes.getFirst().getPatientName() : docs.getFirst().getPatientName());

        return PatientHistoryResponse.builder()
                .patientName(patientName)
                .notes(notes)
                .documents(docs)
                .build();
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private MedicalDocumentResponse toDocumentResponse(MedicalDocument doc) {
        return MedicalDocumentResponse.builder()
                .id(doc.getId())
                .patientId(doc.getPatient().getId())
                .patientName(doc.getPatient().getFullName())
                .doctorId(doc.getDoctor().getId())
                .doctorName(doc.getDoctor().getFullName())
                .doctorSpecialty(doc.getDoctor().getSpecialty().name())
                .type(doc.getType().name())
                .typeLabel(docTypeLabel(doc.getType()))
                .title(doc.getTitle())
                .content(doc.getContent())
                .issuedAt(doc.getIssuedAt())
                .validUntil(doc.getValidUntil())
                .active(doc.isActive())
                .build();
    }

    private PatientNoteResponse toNoteResponse(PatientNote note) {
        return PatientNoteResponse.builder()
                .id(note.getId())
                .patientId(note.getPatient().getId())
                .patientName(note.getPatient().getFullName())
                .doctorId(note.getDoctor().getId())
                .doctorName(note.getDoctor().getFullName())
                .type(note.getType().name())
                .typeLabel(noteTypeLabel(note.getType()))
                .content(note.getContent())
                .visibleToClient(note.isVisibleToClient())
                .createdAt(note.getCreatedAt())
                .build();
    }

    private String docTypeLabel(MedicalDocumentType type) {
        return switch (type) {
            case PRESCRIPTION   -> "Рецепт";
            case REFERRAL       -> "Направление";
            case SICK_LEAVE     -> "Больничный лист";
            case ANALYSIS_ORDER -> "Направление на анализы";
            case CERTIFICATE    -> "Справка";
        };
    }

    private String noteTypeLabel(PatientNoteType type) {
        return switch (type) {
            case DIAGNOSIS   -> "Диагноз";
            case OBSERVATION -> "Наблюдение";
            case NOTE        -> "Заметка";
        };
    }
}
