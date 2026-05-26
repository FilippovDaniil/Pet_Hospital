package com.hospital.service;

import com.hospital.dto.request.ServiceOrderRequest;
import com.hospital.dto.response.PaymentInitResponse;

public interface PaymentService {
    PaymentInitResponse initiatePayment(String username, ServiceOrderRequest request);
    String confirmPayment(String alfaOrderId);
}
