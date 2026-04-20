package com.hospital.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class PaidServiceSummaryReport {
    private List<PatientSummary> byPatient;
    private BigDecimal grandTotal;

    @Data
    @Builder
    public static class PatientSummary {
        private Long patientId;
        private String patientName;
        private BigDecimal total;
        private int serviceCount;
    }
}
