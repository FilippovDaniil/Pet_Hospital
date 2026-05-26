package com.hospital.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentInitResponse {
    private String formUrl;
    private String orderNumber;
}
