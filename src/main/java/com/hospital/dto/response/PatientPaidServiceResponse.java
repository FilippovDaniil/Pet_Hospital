package com.hospital.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PatientPaidServiceResponse {
    private Long id;
    private Long patientId;
    private String patientName;
    private Long serviceId;
    private String serviceName;
    private BigDecimal price;
    private LocalDateTime assignedDate;
    private boolean paid;
}
