package com.hospital.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NurseAssignmentResponse {
    private Long id;
    private Long clientUserId;
    private String clientName;
    private Long nurseUserId;
    private String nurseName;
    private String procedureType;
    private String procedureTypeLabel;
    private String title;
    private String description;
    private String dosage;
    private LocalDate scheduledDate;
    private LocalTime scheduledTime;
    private String status;
    private LocalDateTime createdAt;
}
