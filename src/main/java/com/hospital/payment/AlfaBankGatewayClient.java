package com.hospital.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlfaBankGatewayClient {

    private final AlfaBankProperties props;
    private final RestTemplate restTemplate;

    public RegisterOrderResponse registerOrder(String orderNumber, long amountKopecks, String description) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("userName",    props.getUserName());
        params.add("password",    props.getPassword());
        params.add("orderNumber", orderNumber);
        params.add("amount",      String.valueOf(amountKopecks));
        params.add("returnUrl",   props.getReturnUrl());
        params.add("failUrl",     props.getFailUrl());
        params.add("description", description);
        params.add("currency",    "810");

        log.info("Alfa Bank register.do: orderNumber={}, amount={}", orderNumber, amountKopecks);
        RegisterOrderResponse response = post("register.do", params, RegisterOrderResponse.class);
        log.info("Alfa Bank register.do response: errorCode={}, orderId={}", response.getErrorCode(), response.getOrderId());
        return response;
    }

    public OrderStatusResponse getOrderStatusExtended(String alfaOrderId) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("userName", props.getUserName());
        params.add("password", props.getPassword());
        params.add("orderId",  alfaOrderId);

        log.info("Alfa Bank getOrderStatusExtended: orderId={}", alfaOrderId);
        OrderStatusResponse response = post("getOrderStatusExtended.do", params, OrderStatusResponse.class);
        log.info("Alfa Bank status: orderId={}, orderStatus={}, errorCode={}", alfaOrderId, response.getOrderStatus(), response.getErrorCode());
        return response;
    }

    private <T> T post(String method, MultiValueMap<String, String> params, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        return restTemplate.postForObject(props.getGatewayUrl() + method, request, responseType);
    }
}
