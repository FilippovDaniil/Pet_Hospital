package com.hospital.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceOrderResponse {
    private Long id;
    private Long serviceId;
    private String serviceName;
    private String serviceDescription;
    private BigDecimal servicePrice;
    private String contactPhone;
    private String notes;
    private String status;
    private LocalDate preferredDate;
    private LocalTime preferredTime;
    private LocalDateTime createdAt;
}
