package com.hospital.service.event;

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
public class PaidServiceEvent {

    private String eventId;
    private LocalDateTime occurredAt;
    private Long patientId;
    private String patientName;
    private Long serviceId;
    private String serviceName;
    private BigDecimal price;
    private Long linkId;
}
