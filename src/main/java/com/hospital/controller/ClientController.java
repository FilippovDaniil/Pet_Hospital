package com.hospital.controller;

import com.hospital.dto.request.AppointmentRequest;
import com.hospital.dto.request.ServiceOrderRequest;
import com.hospital.dto.response.*;
import com.hospital.service.ClientService;
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

    @GetMapping("/appointments/my")
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

    @GetMapping("/service-orders/my")
    @Operation(summary = "Мои заказы услуг (требует ROLE_CLIENT)")
    public ResponseEntity<List<ServiceOrderResponse>> getMyOrders(Authentication authentication) {
        return ResponseEntity.ok(clientService.getMyOrders(authentication.getName()));
    }
}
