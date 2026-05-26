package com.hospital.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderStatusResponse {
    private Integer orderStatus;
    private String errorCode;
    private String errorMessage;
    private Long amount;

    public boolean isPaid() {
        return orderStatus != null && orderStatus == 2;
    }

    public boolean isFailed() {
        return orderStatus != null && (orderStatus == 6 || orderStatus == 3 || orderStatus == 4);
    }
}
