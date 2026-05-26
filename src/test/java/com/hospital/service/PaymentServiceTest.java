package com.hospital.service;

import com.hospital.dto.request.ServiceOrderRequest;
import com.hospital.dto.response.PaymentInitResponse;
import com.hospital.entity.*;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.payment.AlfaBankGatewayClient;
import com.hospital.payment.OrderStatusResponse;
import com.hospital.payment.RegisterOrderResponse;
import com.hospital.repository.*;
import com.hospital.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock private PaymentOrderRepository paymentOrderRepository;
    @Mock private ClientServiceOrderRepository clientServiceOrderRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaidServiceRepository paidServiceRepository;
    @Mock private AlfaBankGatewayClient alfaBankClient;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private User clientUser;
    private PaidService service;
    private ServiceOrderRequest request;

    @BeforeEach
    void setUp() {
        clientUser = User.builder()
                .id(1L).username("client1").fullName("Клиент Иван").role(Role.ROLE_CLIENT).active(true)
                .build();

        service = PaidService.builder()
                .id(10L).name("МРТ головного мозга").price(new BigDecimal("1500.00")).active(true)
                .build();

        request = new ServiceOrderRequest();
        request.setServiceId(10L);
        request.setContactPhone("+7 999 000 00 00");
        request.setNotes("Срочно");
        request.setPreferredDate(LocalDate.of(2026, 6, 10));
        request.setPreferredTime(LocalTime.of(10, 0));

        when(paymentOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clientServiceOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── initiatePayment ─────────────────────────────────────────────────────────

    @Test
    void initiatePayment_success_returnsFormUrl() {
        when(userRepository.findByUsername("client1")).thenReturn(Optional.of(clientUser));
        when(paidServiceRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(service));

        RegisterOrderResponse regResponse = new RegisterOrderResponse();
        regResponse.setOrderId("alfa-uuid-123");
        regResponse.setFormUrl("https://alfa.rbsuat.com/payment/merchants/rbstest/payment_ru.html?mdOrder=alfa-uuid-123");
        regResponse.setErrorCode("0");
        when(alfaBankClient.registerOrder(anyString(), eq(150000L), anyString())).thenReturn(regResponse);

        PaymentInitResponse result = paymentService.initiatePayment("client1", request);

        assertThat(result.getFormUrl()).contains("alfa.rbsuat.com");
        assertThat(result.getOrderNumber()).isNotNull().hasSize(32);
        verify(paymentOrderRepository, times(2)).save(any()); // PENDING + alfaOrderId set
        verify(alfaBankClient).registerOrder(anyString(), eq(150000L), contains("МРТ"));
    }

    @Test
    void initiatePayment_amountCalculatedCorrectly() {
        service = PaidService.builder().id(10L).name("Консультация").price(new BigDecimal("99.50")).active(true).build();
        when(userRepository.findByUsername("client1")).thenReturn(Optional.of(clientUser));
        when(paidServiceRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(service));

        RegisterOrderResponse regResponse = new RegisterOrderResponse();
        regResponse.setFormUrl("https://alfa.rbsuat.com/pay?id=1");
        regResponse.setOrderId("alfa-1");
        regResponse.setErrorCode("0");
        when(alfaBankClient.registerOrder(anyString(), eq(9950L), anyString())).thenReturn(regResponse);

        paymentService.initiatePayment("client1", request);

        verify(alfaBankClient).registerOrder(anyString(), eq(9950L), anyString());
    }

    @Test
    void initiatePayment_userNotFound_throwsResourceNotFoundException() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.initiatePayment("unknown", request))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(alfaBankClient);
    }

    @Test
    void initiatePayment_serviceNotFound_throwsResourceNotFoundException() {
        when(userRepository.findByUsername("client1")).thenReturn(Optional.of(clientUser));
        when(paidServiceRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.initiatePayment("client1", request))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(alfaBankClient);
    }

    @Test
    void initiatePayment_alfaBankReturnsNoFormUrl_setsFailedAndThrows() {
        when(userRepository.findByUsername("client1")).thenReturn(Optional.of(clientUser));
        when(paidServiceRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(service));

        RegisterOrderResponse regResponse = new RegisterOrderResponse();
        regResponse.setErrorCode("1");
        regResponse.setErrorMessage("Order with this number is already registered");
        when(alfaBankClient.registerOrder(anyString(), anyLong(), anyString())).thenReturn(regResponse);

        assertThatThrownBy(() -> paymentService.initiatePayment("client1", request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Альфа Банк");

        verify(paymentOrderRepository, atLeastOnce()).save(argThat(o ->
                o instanceof PaymentOrder && ((PaymentOrder) o).getStatus() == PaymentOrderStatus.FAILED));
    }

    // ─── confirmPayment ───────────────────────────────────────────────────────────

    @Test
    void confirmPayment_paid_createsServiceOrderAndReturnsPaid() {
        PaymentOrder paymentOrder = PaymentOrder.builder()
                .id(1L).orderNumber("order-1").alfaOrderId("alfa-1")
                .clientUser(clientUser).paidService(service)
                .contactPhone("+7 999 000 00 00").preferredDate(LocalDate.of(2026, 6, 10))
                .preferredTime(LocalTime.of(10, 0))
                .amountKopecks(150000L).status(PaymentOrderStatus.PENDING)
                .build();
        when(paymentOrderRepository.findByAlfaOrderId("alfa-1")).thenReturn(Optional.of(paymentOrder));

        OrderStatusResponse statusResponse = new OrderStatusResponse();
        statusResponse.setOrderStatus(2); // DEPOSITED — paid
        statusResponse.setErrorCode("0");
        when(alfaBankClient.getOrderStatusExtended("alfa-1")).thenReturn(statusResponse);

        String result = paymentService.confirmPayment("alfa-1");

        assertThat(result).isEqualTo("paid");
        assertThat(paymentOrder.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        assertThat(paymentOrder.getPaidAt()).isNotNull();
        verify(clientServiceOrderRepository).save(argThat(o ->
                o instanceof ClientServiceOrder
                && ((ClientServiceOrder) o).getStatus() == ClientServiceOrderStatus.CONFIRMED));
    }

    @Test
    void confirmPayment_declined_setsFailedAndReturnsFailed() {
        PaymentOrder paymentOrder = PaymentOrder.builder()
                .id(1L).orderNumber("order-2").alfaOrderId("alfa-2")
                .clientUser(clientUser).paidService(service)
                .amountKopecks(150000L).status(PaymentOrderStatus.PENDING)
                .build();
        when(paymentOrderRepository.findByAlfaOrderId("alfa-2")).thenReturn(Optional.of(paymentOrder));

        OrderStatusResponse statusResponse = new OrderStatusResponse();
        statusResponse.setOrderStatus(6); // DECLINED
        statusResponse.setErrorCode("0");
        when(alfaBankClient.getOrderStatusExtended("alfa-2")).thenReturn(statusResponse);

        String result = paymentService.confirmPayment("alfa-2");

        assertThat(result).isEqualTo("failed");
        assertThat(paymentOrder.getStatus()).isEqualTo(PaymentOrderStatus.FAILED);
        verifyNoInteractions(clientServiceOrderRepository);
    }

    @Test
    void confirmPayment_pending_returnsPending() {
        PaymentOrder paymentOrder = PaymentOrder.builder()
                .id(1L).orderNumber("order-3").alfaOrderId("alfa-3")
                .clientUser(clientUser).paidService(service)
                .amountKopecks(150000L).status(PaymentOrderStatus.PENDING)
                .build();
        when(paymentOrderRepository.findByAlfaOrderId("alfa-3")).thenReturn(Optional.of(paymentOrder));

        OrderStatusResponse statusResponse = new OrderStatusResponse();
        statusResponse.setOrderStatus(0); // CREATED — not yet paid
        statusResponse.setErrorCode("0");
        when(alfaBankClient.getOrderStatusExtended("alfa-3")).thenReturn(statusResponse);

        String result = paymentService.confirmPayment("alfa-3");

        assertThat(result).isEqualTo("pending");
        assertThat(paymentOrder.getStatus()).isEqualTo(PaymentOrderStatus.PENDING);
        verifyNoInteractions(clientServiceOrderRepository);
    }

    @Test
    void confirmPayment_orderNotFound_returnsNotFound() {
        when(paymentOrderRepository.findByAlfaOrderId("unknown")).thenReturn(Optional.empty());

        String result = paymentService.confirmPayment("unknown");

        assertThat(result).isEqualTo("not_found");
        verifyNoInteractions(alfaBankClient);
        verifyNoInteractions(clientServiceOrderRepository);
    }

    @Test
    void confirmPayment_alreadyPaid_idempotent_doesNotCallAlfaBank() {
        PaymentOrder paymentOrder = PaymentOrder.builder()
                .id(1L).orderNumber("order-4").alfaOrderId("alfa-4")
                .clientUser(clientUser).paidService(service)
                .amountKopecks(150000L).status(PaymentOrderStatus.PAID)
                .build();
        when(paymentOrderRepository.findByAlfaOrderId("alfa-4")).thenReturn(Optional.of(paymentOrder));

        String result = paymentService.confirmPayment("alfa-4");

        assertThat(result).isEqualTo("paid");
        verifyNoInteractions(alfaBankClient);
        verifyNoInteractions(clientServiceOrderRepository);
    }
}
