package com.hospital.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegisterOrderResponse {
    private String orderId;
    private String formUrl;
    private String errorCode;
    private String errorMessage;

    public boolean isSuccess() {
        return formUrl != null && !formUrl.isBlank();
    }
}
