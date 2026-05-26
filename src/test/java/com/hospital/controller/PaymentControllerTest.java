package com.hospital.controller;

import com.hospital.config.JwtUtil;
import com.hospital.payment.AlfaBankGatewayClient;
import com.hospital.payment.AlfaBankProperties;
import com.hospital.service.PaymentService;
import com.hospital.service.impl.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PaymentController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    // Требуются для загрузки контекста — JwtAuthenticationFilter зависит от них
    @MockBean
    private JwtUtil jwtUtil;
    @MockBean
    private UserDetailsServiceImpl userDetailsServiceImpl;
    @MockBean
    private AlfaBankGatewayClient alfaBankGatewayClient;
    @MockBean
    private AlfaBankProperties alfaBankProperties;

    // ─── GET /api/payment/callback ────────────────────────────────────────────────

    @Test
    void callback_paidResult_returnsSuccessHtml() throws Exception {
        when(paymentService.confirmPayment("alfa-uuid-1")).thenReturn("paid");

        String html = mockMvc.perform(get("/api/payment/callback").param("orderId", "alfa-uuid-1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Оплата прошла успешно");
        assertThat(html).contains("account.html");
    }

    @Test
    void callback_failedResult_returnsFailHtml() throws Exception {
        when(paymentService.confirmPayment("alfa-uuid-2")).thenReturn("failed");

        String html = mockMvc.perform(get("/api/payment/callback").param("orderId", "alfa-uuid-2"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Оплата не прошла");
        assertThat(html).contains("Платёж был отклонён банком");
    }

    @Test
    void callback_pendingResult_returnsFailHtml() throws Exception {
        when(paymentService.confirmPayment("alfa-uuid-3")).thenReturn("pending");

        String html = mockMvc.perform(get("/api/payment/callback").param("orderId", "alfa-uuid-3"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Оплата не завершена");
    }

    @Test
    void callback_orderNotFound_returnsNotFoundHtml() throws Exception {
        when(paymentService.confirmPayment("unknown")).thenReturn("not_found");

        String html = mockMvc.perform(get("/api/payment/callback").param("orderId", "unknown"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Заказ не найден");
    }

    @Test
    void callback_doesNotRequireJwtToken() throws Exception {
        when(paymentService.confirmPayment(anyString())).thenReturn("paid");

        // Нет заголовка Authorization — должен вернуть 200 (не 401)
        mockMvc.perform(get("/api/payment/callback").param("orderId", "any-order"))
                .andExpect(status().isOk());
    }

    // ─── GET /api/payment/fail ────────────────────────────────────────────────────

    @Test
    void fail_withoutOrderId_returnsFailHtml() throws Exception {
        String html = mockMvc.perform(get("/api/payment/fail"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Оплата не прошла");
        assertThat(html).contains("Оплата не была завершена");
        verifyNoInteractions(paymentService);
    }

    @Test
    void fail_withOrderId_callsConfirmPaymentAndReturnsFailHtml() throws Exception {
        when(paymentService.confirmPayment("alfa-uuid-fail")).thenReturn("failed");

        String html = mockMvc.perform(get("/api/payment/fail").param("orderId", "alfa-uuid-fail"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Оплата не прошла");
        verify(paymentService).confirmPayment("alfa-uuid-fail");
    }

    @Test
    void fail_doesNotRequireJwtToken() throws Exception {
        mockMvc.perform(get("/api/payment/fail"))
                .andExpect(status().isOk());
    }
}
