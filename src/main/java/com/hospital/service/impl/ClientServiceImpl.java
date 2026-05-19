package com.hospital.service.impl;

import com.hospital.dto.request.AppointmentRequest;
import com.hospital.dto.request.ServiceOrderRequest;
import com.hospital.dto.response.*;
import com.hospital.entity.*;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.repository.*;
import com.hospital.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClientServiceImpl implements ClientService {

    private final DoctorRepository doctorRepository;
    private final DepartmentRepository departmentRepository;
    private final PaidServiceRepository paidServiceRepository;
    private final AppointmentRepository appointmentRepository;
    private final ClientServiceOrderRepository clientServiceOrderRepository;
    private final UserRepository userRepository;

    @Override
    public List<PublicDoctorResponse> getPublicDoctors() {
        return doctorRepository.findAllActiveDoctorsWithDepartment().stream()
                .map(d -> PublicDoctorResponse.builder()
                        .id(d.getId())
                        .fullName(d.getFullName())
                        .specialty(d.getSpecialty().name())
                        .cabinetNumber(d.getCabinetNumber())
                        .departmentId(d.getDepartment().getId())
                        .departmentName(d.getDepartment().getName())
                        .departmentLocation(d.getDepartment().getLocation())
                        .build())
                .toList();
    }

    @Override
    public List<DepartmentResponse> getPublicDepartments() {
        return departmentRepository.findAll().stream()
                .filter(Department::isActive)
                .map(dep -> DepartmentResponse.builder()
                        .id(dep.getId())
                        .name(dep.getName())
                        .description(dep.getDescription())
                        .location(dep.getLocation())
                        .headDoctorId(dep.getHeadDoctor() != null ? dep.getHeadDoctor().getId() : null)
                        .headDoctorName(dep.getHeadDoctor() != null ? dep.getHeadDoctor().getFullName() : null)
                        .build())
                .toList();
    }

    @Override
    public List<PaidServiceResponse> getPublicServices() {
        return paidServiceRepository.findAll().stream()
                .filter(PaidService::isActive)
                .map(s -> PaidServiceResponse.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .description(s.getDescription())
                        .price(s.getPrice())
                        .active(true)
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public AppointmentResponse bookAppointment(String username, AppointmentRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
        Doctor doctor = doctorRepository.findByIdAndActiveTrue(request.getDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Врач не найден или неактивен"));

        Appointment appointment = Appointment.builder()
                .clientUser(user)
                .doctor(doctor)
                .preferredDate(request.getPreferredDate())
                .preferredTime(request.getPreferredTime())
                .contactPhone(request.getContactPhone())
                .notes(request.getNotes())
                .status(AppointmentStatus.PENDING)
                .build();

        appointment = appointmentRepository.save(appointment);
        return toAppointmentResponse(appointment);
    }

    @Override
    public List<AppointmentResponse> getMyAppointments(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
        return appointmentRepository.findByClientUserIdWithDetails(user.getId()).stream()
                .map(this::toAppointmentResponse)
                .toList();
    }

    @Override
    @Transactional
    public ServiceOrderResponse orderService(String username, ServiceOrderRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
        PaidService service = paidServiceRepository.findByIdAndActiveTrue(request.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Услуга не найдена или недоступна"));

        ClientServiceOrder order = ClientServiceOrder.builder()
                .clientUser(user)
                .paidService(service)
                .contactPhone(request.getContactPhone())
                .notes(request.getNotes())
                .status(ClientServiceOrderStatus.PENDING)
                .build();

        order = clientServiceOrderRepository.save(order);
        return toServiceOrderResponse(order);
    }

    @Override
    public List<ServiceOrderResponse> getMyOrders(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
        return clientServiceOrderRepository.findByClientUserIdWithDetails(user.getId()).stream()
                .map(this::toServiceOrderResponse)
                .toList();
    }

    private AppointmentResponse toAppointmentResponse(Appointment a) {
        return AppointmentResponse.builder()
                .id(a.getId())
                .doctorId(a.getDoctor().getId())
                .doctorName(a.getDoctor().getFullName())
                .doctorSpecialty(a.getDoctor().getSpecialty().name())
                .departmentName(a.getDoctor().getDepartment().getName())
                .preferredDate(a.getPreferredDate())
                .preferredTime(a.getPreferredTime())
                .contactPhone(a.getContactPhone())
                .notes(a.getNotes())
                .status(a.getStatus().name())
                .createdAt(a.getCreatedAt())
                .build();
    }

    private ServiceOrderResponse toServiceOrderResponse(ClientServiceOrder o) {
        return ServiceOrderResponse.builder()
                .id(o.getId())
                .serviceId(o.getPaidService().getId())
                .serviceName(o.getPaidService().getName())
                .serviceDescription(o.getPaidService().getDescription())
                .servicePrice(o.getPaidService().getPrice())
                .contactPhone(o.getContactPhone())
                .notes(o.getNotes())
                .status(o.getStatus().name())
                .createdAt(o.getCreatedAt())
                .build();
    }
}
