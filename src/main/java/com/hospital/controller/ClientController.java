package com.hospital.controller;

import com.hospital.dto.request.AppointmentRequest;
import com.hospital.dto.request.ServiceOrderRequest;
import com.hospital.dto.response.*;
import com.hospital.entity.User;
import com.hospital.repository.UserRepository;
import com.hospital.service.ClientService;
import com.hospital.service.NurseService;
import com.hospital.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/client")
@RequiredArgsConstructor
@Tag(name = "Client Portal", description = "Пациентский портал: запись на приём и заказ услуг")
public class ClientController {

    private final ClientService clientService;
    private final NurseService nurseService;
    private final UserRepository userRepository;
    private final PaymentService paymentService;

    @GetMapping("/doctors")
    @Operation(summary = "Список всех врачей (публичный)")
    public ResponseEntity<List<PublicDoctorResponse>> getDoctors() {
        return ResponseEntity.ok(clientService.getPublicDoctors());
    }

    @GetMapping("/departments")
    @Operation(summary = "Список всех отделений (публичный)")
    public ResponseEntity<List<DepartmentResponse>> getDepartments() {
        return ResponseEntity.ok(clientService.getPublicDepartments());
    }

    @GetMapping("/services")
    @Operation(summary = "Список всех платных услуг (публичный)")
    public ResponseEntity<List<PaidServiceResponse>> getServices() {
        return ResponseEntity.ok(clientService.getPublicServices());
    }

    @PostMapping("/appointments")
    @Operation(summary = "Записаться к врачу (требует ROLE_CLIENT)")
    public ResponseEntity<AppointmentResponse> bookAppointment(
            Authentication authentication,
            @RequestBody @Valid AppointmentRequest request) {
        AppointmentResponse response = clientService.bookAppointment(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me/appointments")
    @Operation(summary = "Мои записи к врачу (требует ROLE_CLIENT)")
    public ResponseEntity<List<AppointmentResponse>> getMyAppointments(Authentication authentication) {
        return ResponseEntity.ok(clientService.getMyAppointments(authentication.getName()));
    }

    @PostMapping("/service-orders")
    @Operation(summary = "Заказать платную услугу (требует ROLE_CLIENT)")
    public ResponseEntity<ServiceOrderResponse> orderService(
            Authentication authentication,
            @RequestBody @Valid ServiceOrderRequest request) {
        ServiceOrderResponse response = clientService.orderService(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/service-orders/pay")
    @Operation(summary = "Оплатить услугу через Альфа Банк — возвращает ссылку на платёжную форму (требует ROLE_CLIENT)")
    public ResponseEntity<PaymentInitResponse> payForService(
            Authentication authentication,
            @RequestBody @Valid ServiceOrderRequest request) {
        PaymentInitResponse response = paymentService.initiatePayment(authentication.getName(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/service-orders")
    @Operation(summary = "Мои заказы услуг (требует ROLE_CLIENT)")
    public ResponseEntity<List<ServiceOrderResponse>> getMyOrders(Authentication authentication) {
        return ResponseEntity.ok(clientService.getMyOrders(authentication.getName()));
    }

    @GetMapping("/me/assignments")
    @Operation(summary = "Мои назначения от медсестры (требует ROLE_CLIENT)")
    public ResponseEntity<List<NurseAssignmentResponse>> getMyAssignments(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        return ResponseEntity.ok(nurseService.getClientAssignments(user.getId()));
    }
}
