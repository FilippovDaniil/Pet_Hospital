package com.hospital.service;

import com.hospital.dto.request.CreateMedicalDocumentRequest;
import com.hospital.dto.request.CreatePatientNoteRequest;
import com.hospital.dto.response.MedicalDocumentResponse;
import com.hospital.dto.response.PatientHistoryResponse;
import com.hospital.dto.response.PatientNoteResponse;
import com.hospital.entity.User;

import java.util.List;

public interface MedicalService {

    /** Врач создаёт медицинский документ. */
    MedicalDocumentResponse createDocument(CreateMedicalDocumentRequest request, User doctorUser);

    /** Врач: все документы пациента. */
    List<MedicalDocumentResponse> getPatientDocuments(Long patientId, User doctorUser);

    /** Клиент: свои медицинские документы (только активные, через client_user_id). */
    List<MedicalDocumentResponse> getMyDocuments(User clientUser);

    /** Врач создаёт заметку / диагноз. */
    PatientNoteResponse createNote(CreatePatientNoteRequest request, User doctorUser);

    /** Врач: полная история пациента (заметки + документы). */
    PatientHistoryResponse getPatientHistory(Long patientId, User doctorUser);

    /** Клиент: своя история (только записи с visibleToClient=true + документы). */
    PatientHistoryResponse getMyHistory(User clientUser);
}
