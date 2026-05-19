package com.hospital.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PatientNoteResponse {
    private Long id;
    private Long patientId;
    private String patientName;
    private Long doctorId;
    private String doctorName;
    private String type;
    private String typeLabel;
    private String content;
    private boolean visibleToClient;
    private LocalDateTime createdAt;
}
