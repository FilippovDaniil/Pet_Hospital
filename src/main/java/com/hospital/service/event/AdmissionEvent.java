package com.hospital.service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdmissionEvent {

    public enum Action { ADMITTED, DISCHARGED }

    private String eventId;
    private LocalDateTime occurredAt;
    private Long patientId;
    private String patientName;
    private Long wardId;
    private String wardNumber;
    private Action action;
}
