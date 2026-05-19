package com.hospital.service;

import com.hospital.dto.request.AppointmentRequest;
import com.hospital.dto.request.ServiceOrderRequest;
import com.hospital.dto.response.AppointmentResponse;
import com.hospital.dto.response.PublicDoctorResponse;
import com.hospital.dto.response.ServiceOrderResponse;
import com.hospital.dto.response.PaidServiceResponse;
import com.hospital.dto.response.DepartmentResponse;

import java.util.List;

public interface ClientService {
    List<PublicDoctorResponse> getPublicDoctors();
    List<DepartmentResponse> getPublicDepartments();
    List<PaidServiceResponse> getPublicServices();
    AppointmentResponse bookAppointment(String username, AppointmentRequest request);
    List<AppointmentResponse> getMyAppointments(String username);
    ServiceOrderResponse orderService(String username, ServiceOrderRequest request);
    List<ServiceOrderResponse> getMyOrders(String username);
}
