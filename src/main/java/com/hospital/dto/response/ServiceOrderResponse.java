package com.hospital.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    private LocalDateTime createdAt;
}
