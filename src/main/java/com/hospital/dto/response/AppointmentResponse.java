package com.hospital.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentResponse {
    private Long id;
    private Long doctorId;
    private String doctorName;
    private String doctorSpecialty;
    private String departmentName;
    private Long clientUserId;
    private String clientName;
    private LocalDate preferredDate;
    private String preferredTime;
    private String contactPhone;
    private String notes;
    private String status;
    private LocalDateTime createdAt;
}
