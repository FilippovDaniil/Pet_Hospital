package com.hospital.dto.request;

import lombok.Data;

@Data
public class AdjustSupplyRequest {
    /** Положительное — пополнение, отрицательное — расход. */
    private int delta;
}
