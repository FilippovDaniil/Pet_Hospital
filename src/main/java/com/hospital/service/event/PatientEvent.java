package com.hospital.service.event;

import com.hospital.entity.PatientStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientEvent {

    private String eventId;
    private String eventType;
    private LocalDateTime occurredAt;
    private Long patientId;
    private String patientName;
    private PatientStatus newStatus;
    private Long previousDoctorId;
    private Long newDoctorId;
}
