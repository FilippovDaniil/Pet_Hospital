package com.hospital.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PatientHistoryResponse {
    private Long patientId;
    private String patientName;
    private List<PatientNoteResponse> notes;
    private List<MedicalDocumentResponse> documents;
}
