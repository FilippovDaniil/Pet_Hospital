package com.hospital.service.impl;

import com.hospital.dto.request.ServiceOrderRequest;
import com.hospital.dto.response.PaymentInitResponse;
import com.hospital.entity.*;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.payment.AlfaBankGatewayClient;
import com.hospital.payment.OrderStatusResponse;
import com.hospital.payment.RegisterOrderResponse;
import com.hospital.repository.*;
import com.hospital.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final ClientServiceOrderRepository clientServiceOrderRepository;
    private final UserRepository userRepository;
    private final PaidServiceRepository paidServiceRepository;
    private final AlfaBankGatewayClient alfaBankClient;

    @Override
    @Transactional
    public PaymentInitResponse initiatePayment(String username, ServiceOrderRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
        PaidService service = paidServiceRepository.findByIdAndActiveTrue(request.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Услуга не найдена или недоступна"));

        long amountKopecks = service.getPrice()
                .multiply(BigDecimal.valueOf(100))
                .longValueExact();

        String orderNumber = UUID.randomUUID().toString().replace("-", "").substring(0, 32);

        PaymentOrder paymentOrder = PaymentOrder.builder()
                .orderNumber(orderNumber)
                .clientUser(user)
                .paidService(service)
                .contactPhone(request.getContactPhone())
                .notes(request.getNotes())
                .preferredDate(request.getPreferredDate())
                .preferredTime(request.getPreferredTime())
                .amountKopecks(amountKopecks)
                .status(PaymentOrderStatus.PENDING)
                .build();
        paymentOrder = paymentOrderRepository.save(paymentOrder);

        RegisterOrderResponse regResponse = alfaBankClient.registerOrder(
                orderNumber,
                amountKopecks,
                "Оплата услуги: " + service.getName()
        );

        if (!regResponse.isSuccess()) {
            paymentOrder.setStatus(PaymentOrderStatus.FAILED);
            paymentOrderRepository.save(paymentOrder);
            throw new RuntimeException("Альфа Банк отклонил создание платежа: " + regResponse.getErrorMessage());
        }

        paymentOrder.setAlfaOrderId(regResponse.getOrderId());
        paymentOrderRepository.save(paymentOrder);

        return PaymentInitResponse.builder()
                .formUrl(regResponse.getFormUrl())
                .orderNumber(orderNumber)
                .build();
    }

    @Override
    @Transactional
    public String confirmPayment(String alfaOrderId) {
        PaymentOrder paymentOrder = paymentOrderRepository.findByAlfaOrderId(alfaOrderId)
                .orElse(null);

        if (paymentOrder == null) {
            log.warn("PaymentOrder not found for alfaOrderId={}", alfaOrderId);
            return "not_found";
        }

        if (paymentOrder.getStatus() == PaymentOrderStatus.PAID) {
            return "paid";
        }

        OrderStatusResponse statusResponse = alfaBankClient.getOrderStatusExtended(alfaOrderId);

        if (statusResponse.isPaid()) {
            paymentOrder.setStatus(PaymentOrderStatus.PAID);
            paymentOrder.setPaidAt(LocalDateTime.now());
            paymentOrderRepository.save(paymentOrder);

            ClientServiceOrder order = ClientServiceOrder.builder()
                    .clientUser(paymentOrder.getClientUser())
                    .paidService(paymentOrder.getPaidService())
                    .contactPhone(paymentOrder.getContactPhone())
                    .notes(paymentOrder.getNotes())
                    .preferredDate(paymentOrder.getPreferredDate())
                    .preferredTime(paymentOrder.getPreferredTime())
                    .status(ClientServiceOrderStatus.CONFIRMED)
                    .build();
            clientServiceOrderRepository.save(order);

            log.info("Payment confirmed: orderNumber={}, service={}", paymentOrder.getOrderNumber(), paymentOrder.getPaidService().getName());
            return "paid";
        }

        if (statusResponse.isFailed()) {
            paymentOrder.setStatus(PaymentOrderStatus.FAILED);
            paymentOrderRepository.save(paymentOrder);
            return "failed";
        }

        return "pending";
    }
}
